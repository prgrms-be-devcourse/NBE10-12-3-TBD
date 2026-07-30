package com.whattoeat.domain.auth.service

import com.whattoeat.domain.auth.dto.AuthResult
import com.whattoeat.domain.auth.dto.AuthUserResponse
import com.whattoeat.domain.auth.dto.LoginRequest
import com.whattoeat.domain.auth.dto.SignUpRequest
import com.whattoeat.domain.auth.dto.TokenResponse
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.DuplicateLoginIdException
import com.whattoeat.global.exception.DuplicateNicknameException
import com.whattoeat.global.exception.InvalidCredentialsException
import com.whattoeat.global.jwt.JwtUtil
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val redisTemplate: RedisTemplate<String, String>
) {
    @Transactional
    fun signup(request: SignUpRequest): User {
        if (userRepository.existsByLoginId(request.loginId)) {
            throw DuplicateLoginIdException("이미 사용 중인 아이디입니다.")
        }
        if (userRepository.existsByNickname(request.nickname)) {
            throw DuplicateNicknameException("이미 사용 중인 닉네임입니다.")
        }
        val user = User.builder()
            .loginId(request.loginId)
            .password(passwordEncoder.encode(request.password))
            .nickname(request.nickname)
            .email(request.loginId)
            .provider(Provider.LOCAL)
            .role(Role.USER)
            .build()
        return userRepository.save(user)
    }

    @Transactional
    fun signupAndLogin(request: SignUpRequest): AuthResult {
        val user = signup(request)
        val accessToken = jwtUtil.generateAccessToken(user)
        val refreshToken = jwtUtil.generateRefreshToken(user)
        saveRefreshToken(checkNotNull(user.id){"회원가입된 사용자의 id가 없습니다."}, refreshToken)
        return AuthResult(accessToken, refreshToken, AuthUserResponse.from(user))
    }

    fun saveRefreshToken(userId: Long, refreshToken: String) {
        redisTemplate.opsForValue().set(
            "refresh:${userId}",
            refreshToken,
            Duration.ofDays(7)
        )
    }

    @Transactional
    fun reissue(refreshToken: String): TokenResponse {
        val userId = jwtUtil.getUserId(refreshToken)
        val savedRefreshToken = redisTemplate.opsForValue().get("refresh:${userId}")
        if (savedRefreshToken == null || savedRefreshToken != refreshToken) {
            throw InvalidCredentialsException("유효하지 않은 refreshToken입니다.")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { InvalidCredentialsException("유효하지 않은 refreshToken입니다.") }

        val newAccessToken = jwtUtil.generateAccessToken(user)
        val newRefreshToken = jwtUtil.generateRefreshToken(user)

        saveRefreshToken(userId, newRefreshToken)
        return TokenResponse(newAccessToken, newRefreshToken)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResult {
        val user = userRepository.findByLoginId(request.loginId)
            .orElseThrow{ InvalidCredentialsException("아이디/비밀번호가 올바르지 않습니다.") }
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw InvalidCredentialsException("아이디/비밀번호가 올바르지 않습니다.")
        }

        val accessToken = jwtUtil.generateAccessToken(user)
        val refreshToken = jwtUtil.generateRefreshToken(user)
        saveRefreshToken(checkNotNull(user.id){"조회된 사용자의 ID가 없습니다."}, refreshToken)
        return AuthResult(accessToken, refreshToken, AuthUserResponse.from(user))
    }

    // OAuth2 로그인은 백엔드 도메인으로 직접 리다이렉트되므로, 그 응답에서 쿠키를 바로 굽지 않고
    // 1회용 코드만 프론트로 넘긴 뒤 프론트가 /api 프록시로 교환하게 해서 쿠키가 프론트 도메인에 스코프되도록 한다.
    fun createOAuthCode(userId: Long): String {
        val code: String = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(
            "oauth-code:${code}",
            userId.toString(),
            Duration.ofSeconds(60)
        )
        return code
    }

    @Transactional
    fun exchangeOAuthCode(code: String): AuthResult {
        val key = "oauth-code:${code}"
        val userIdValue = redisTemplate.opsForValue().get(key)
        redisTemplate.delete(key)

        if (userIdValue == null) {
            throw InvalidCredentialsException("유효하지 않거나 만료된 코드입니다.")
        }

        val user = userRepository.findById(userIdValue.toLong())
            .orElseThrow{ InvalidCredentialsException("유효하지 않은 코드입니다.") }

        val accessToken = jwtUtil.generateAccessToken(user)
        val refreshToken = jwtUtil.generateRefreshToken(user)
        saveRefreshToken(checkNotNull(user.id){"조회된 사용자의 ID가 없습니다."}, refreshToken)
        return AuthResult(accessToken, refreshToken, AuthUserResponse.from(user))
    }

    fun logout(token: String) {
        val remaining = jwtUtil.getRemainingExpiration(token)
        if (remaining > 0) {
            redisTemplate.opsForValue().set("blacklist:${token}", "logout", remaining, TimeUnit.MILLISECONDS)
        }
    }
}
