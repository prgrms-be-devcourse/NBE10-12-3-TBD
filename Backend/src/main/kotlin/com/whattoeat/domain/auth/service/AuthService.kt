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

    // accessToken(1시간)은 refreshToken(7일)보다 먼저 만료돼 쿠키가 사라질 수 있으므로,
    // accessToken이 없어도 refreshToken만으로 로그아웃 처리가 가능해야 한다. 안 그러면
    // 로그인 후 1시간이 지나서 로그아웃할 때 accessToken 블랙리스트 등록만 건너뛰고
    // refreshToken 무효화까지 통째로 생략돼, 탈취된 refreshToken이 /reissue에 계속
    // 쓰일 수 있다. 토큰 파싱/Redis 처리 중 어느 단계가 실패해도(만료/위변조된 토큰,
    // Redis 장애 등) 예외를 밖으로 던지지 않고 로그아웃 자체는 항상 성공 처리한다.
    fun logout(accessToken: String?, refreshToken: String?) {
        // accessToken은 블랙리스트 등록과 userId 추출에 모두 필요하므로, JwtUtil.getTokenInfo로
        // 한 번만 파싱해서 재사용한다(getRemainingExpiration/getUserId를 따로 부르면 같은
        // 토큰을 두 번 파싱하게 된다). 단, userId 추출(파싱)과 블랙리스트 등록(Redis I/O)은
        // 실패 도메인이 다르므로 하나의 runCatching으로 묶지 않는다 - 묶으면 파싱엔 성공해서
        // 이미 알아낸 userId가 있는데도, 블랙리스트 등록만 실패해도 그 userId까지 함께
        // 버려져서 정작 이 메서드가 보장하려는 refreshToken 무효화가 스킵될 수 있다.
        val info = accessToken?.takeIf { it.isNotBlank() }?.let { token ->
            val parsed = runCatching { jwtUtil.getTokenInfo(token) }.getOrNull()
            if (parsed != null && parsed.remainingMillis > 0) {
                runCatching {
                    redisTemplate.opsForValue().set("blacklist:${token}", "logout", parsed.remainingMillis, TimeUnit.MILLISECONDS)
                }
            }
            parsed
        }

        val userId = info?.userId ?: resolveUserId(refreshToken)
        if (userId != null) {
            runCatching { redisTemplate.delete("refresh:${userId}") }
        }
    }

    private fun resolveUserId(token: String?): Long? =
        token?.takeIf { it.isNotBlank() }?.let { runCatching { jwtUtil.getUserId(it) }.getOrNull() }
}
