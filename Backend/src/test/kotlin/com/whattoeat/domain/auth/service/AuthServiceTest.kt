package com.whattoeat.domain.auth.service

import com.whattoeat.domain.auth.dto.LoginRequest
import com.whattoeat.domain.auth.dto.SignUpRequest
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.DuplicateLoginIdException
import com.whattoeat.global.exception.DuplicateNicknameException
import com.whattoeat.global.exception.InvalidCredentialsException
import com.whattoeat.global.jwt.JwtUtil
import io.jsonwebtoken.JwtException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.*
import org.mockito.BDDMockito.given
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
internal class AuthServiceTest {
    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var jwtUtil: JwtUtil

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @InjectMocks
    private lateinit var authService: AuthService

    private lateinit var signUpRequest: SignUpRequest
    private lateinit var loginRequest: LoginRequest
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        signUpRequest = SignUpRequest("test@tset.com", "pass1234", "testnick")
        loginRequest = LoginRequest("test@tset.com", "pass1234")
        user = User.builder()
            .loginId("test@tset.com")
            .password("encodedPassword")
            .nickname("testnick")
            .email("test@test.com")
            .provider(Provider.LOCAL)
            .role(Role.USER)
            .build()
        ReflectionTestUtils.setField(user, "id", 1L)
    }

    // ========== signup ==========
    @Test
    @DisplayName("정상 입력으로 회원가입 성공")
    fun signupSuccess() {
        given(userRepository.existsByLoginId("test@tset.com")).willReturn(false)
        given(userRepository.existsByNickname("testnick")).willReturn(false)
        given(passwordEncoder.encode("pass1234")).willReturn("encodedPassword")

        given(userRepository
            .save(ArgumentMatchers.any(User::class.java))).willReturn(user)

        val result = authService.signup(signUpRequest)
        assertThat(result).isSameAs(user)

        Mockito.verify(userRepository, Mockito.times(1))
            .save(ArgumentMatchers.any(User::class.java))
    }

    @Test
    @DisplayName("아이디 중복 시 DuplicateLoginIdException 발생")
    fun signupFailDuplicateLoginId() {
        given(userRepository.existsByLoginId("test@tset.com")).willReturn(true)

        assertThatThrownBy { authService.signup(signUpRequest) }
            .isInstanceOf(DuplicateLoginIdException::class.java)
            .hasMessageContaining("아이디")
    }

    @Test
    @DisplayName("닉네임 중복 시 DuplicateNicknameException 발생")
    fun signupFailDuplicateNickname() {
        given(userRepository.existsByLoginId("test@tset.com")).willReturn(false)
        given(userRepository.existsByNickname("testnick")).willReturn(true)

        assertThatThrownBy { authService.signup(signUpRequest) }
            .isInstanceOf(DuplicateNicknameException::class.java)
            .hasMessageContaining("닉네임")
    }


    // ========== login ==========
    @Test
    @DisplayName("정상 아이디/비밀번호로 로그인 성공 후 토큰 반환")
    fun loginSuccess() {
        given(userRepository.findByLoginId("test@tset.com"))
            .willReturn(Optional.of(user))
        given(passwordEncoder.matches("pass1234", "encodedPassword"))
            .willReturn(true)
        given(jwtUtil.generateAccessToken(user)).willReturn("mocked-access-token")
        given(jwtUtil.generateRefreshToken(user)).willReturn("mocked-refresh-token")

        given(redisTemplate.opsForValue()).willReturn(valueOperations)

        val result = authService.login(loginRequest)

        assertThat(result.accessToken).isEqualTo("mocked-access-token")
        assertThat(result.refreshToken).isEqualTo("mocked-refresh-token")
        assertThat(result.userProfile.nickname).isEqualTo("testnick")
        assertThat(result.userProfile.email).isEqualTo("test@test.com")
        assertThat(result.userProfile.provider).isEqualTo(Provider.LOCAL)
    }

    @Test
    @DisplayName("존재하지 않는 아이디로 로그인 시 InvalidCredentialsException 발생")
    fun loginFailUserNotFound() {
        given(userRepository.findByLoginId("test@tset.com"))
            .willReturn(Optional.empty())

        assertThatThrownBy { authService.login(loginRequest) }
            .isInstanceOf(InvalidCredentialsException::class.java)
    }

    @Test
    @DisplayName("비밀번호 불일치 시 InvalidCredentialsException 발생")
    fun loginFailWrongPassword() {
        given(userRepository.findByLoginId("test@tset.com"))
            .willReturn(Optional.of(user))
        given(passwordEncoder
            .matches("pass1234", "encodedPassword"))
            .willReturn(false)

        assertThatThrownBy { authService.login(loginRequest) }
            .isInstanceOf(InvalidCredentialsException::class.java)
    }

    @Test
    @DisplayName("refreshToken으로 새 accessToken 발급")
    fun refreshTokenSuccess() {
        val refreshToken = "mocked-refresh-token"
        val newAccessToken = "mocked-access-token"
        val newRefreshToken = "mocked-refresh-token"

        given(jwtUtil.getUserId(refreshToken)).willReturn(1L)
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("refresh:1")).willReturn(refreshToken)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(jwtUtil.generateAccessToken(user)).willReturn(newAccessToken)
        given(jwtUtil.generateRefreshToken(user)).willReturn(newRefreshToken)

        val response = authService.reissue(refreshToken)

        assertThat(response.accessToken).isEqualTo(newAccessToken)
    }

    @Test
    @DisplayName("refreshToken으로 accessToken+refreshToken 재발급")
    fun refreshSuccess() {
        val oldRefreshToken = "valid-refresh-token"
        val newAccessToken = "new-access-token"
        val newRefreshToken = "new-refresh-token"

        given(jwtUtil.getUserId(oldRefreshToken)).willReturn(1L)
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("refresh:1")).willReturn(oldRefreshToken)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(jwtUtil.generateAccessToken(user)).willReturn(newAccessToken)
        given(jwtUtil.generateRefreshToken(user)).willReturn(newRefreshToken)

        val response = authService.reissue(oldRefreshToken)

        assertThat(response.accessToken).isEqualTo(newAccessToken)
        assertThat(response.refreshToken).isEqualTo(newRefreshToken)
    }

    @Test
    @DisplayName("Redis 저장된 refreshToken 없을 때 예외 발생")
    fun refreshFailNotFound() {
        val refreshToken = "unknown-refresh-token"
        given(jwtUtil.getUserId(refreshToken)).willReturn(1L)
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("refresh:1")).willReturn(null)

        assertThatThrownBy { authService.reissue(refreshToken) }
            .isInstanceOf(InvalidCredentialsException::class.java)
            .hasMessageContaining("refreshToken")
    }

    @Test
    @DisplayName("저장된 refreshToken, 요청받은 refreshToken 다를 시 예외 발생")
    fun refreshMismatch() {
        val refreshToken = "my-refresh-token"
        val storedRefreshToken = "different-token"

        given(jwtUtil.getUserId(refreshToken)).willReturn(1L)
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("refresh:1")).willReturn(storedRefreshToken)

        assertThatThrownBy { authService.reissue(refreshToken) }
            .isInstanceOf(InvalidCredentialsException::class.java)
            .hasMessageContaining("refreshToken")
    }

    @Test
    @DisplayName("위변조된 refreshToken일 시 JwtException 발생")
    fun refreshFailJwtException() {
        val invalidToken = "fake.jwt.token"
        given(jwtUtil.getUserId(invalidToken)).willThrow(JwtException("위변조된 토큰입니다."))
        assertThatThrownBy { authService.reissue(invalidToken) }
            .isInstanceOf(JwtException::class.java)
    }

    // ========== oauth exchange ==========
    @Test
    @DisplayName("1회용 코드 발급 시 Redis에 저장")
    fun createOAuthCodeSuccess() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations)

        val code = authService.createOAuthCode(1L)

        assertThat(code).isNotBlank()
        Mockito.verify(valueOperations, Mockito.times(1))
            .set(
                ArgumentMatchers.eq("oauth-code:" + code),
                ArgumentMatchers.eq("1"),
                ArgumentMatchers.any(
                    Duration::class.java
                )
            )
    }

    @Test
    @DisplayName("유효한 코드 교환 시 토큰과 유저 정보 반환")
    fun exchangeOAuthCodeSuccess() {
        val code = "one-time-code"
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("oauth-code:" + code)).willReturn("1")
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(jwtUtil.generateAccessToken(user)).willReturn("mocked-access-token")
        given(jwtUtil.generateRefreshToken(user)).willReturn("mocked-refresh-token")

        val result = authService.exchangeOAuthCode(code)

        assertThat(result.accessToken).isEqualTo("mocked-access-token")
        assertThat(result.refreshToken).isEqualTo("mocked-refresh-token")
        Mockito
            .verify(redisTemplate, Mockito.times(1))
            .delete("oauth-code:" + code)
    }

    @Test
    @DisplayName("존재하지 않거나 만료된 코드 교환 시 InvalidCredentialsException 발생")
    fun exchangeOAuthCodeFailNotFound() {
        val code = "unknown-code"
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("oauth-code:${code}")).willReturn(null)

        assertThatThrownBy { authService.exchangeOAuthCode(code) }
            .isInstanceOf(InvalidCredentialsException::class.java)
    }

    // ========== logout ==========
    @Test
    @DisplayName("로그아웃 시 Redis 블랙리스트에 토큰 저장")
    fun logoutSuccess() {
        val token = "valid.jwt.token"
        val remaining = 3600000L

        given(jwtUtil.getRemainingExpiration(token)).willReturn(remaining)
        given(redisTemplate.opsForValue()).willReturn(valueOperations)

        authService.logout(token)

        Mockito.verify(valueOperations, Mockito.times(1))
            .set("blacklist:" + token, "logout", remaining, TimeUnit.MILLISECONDS)
    }

    @Test
    @DisplayName("만료된 토큰으로 로그아웃 시 Redis에 저장하지 않음")
    fun logoutWithExpiredToken() {
        val token = "expired.jwt.token"

        given(jwtUtil.getRemainingExpiration(token)).willReturn(-1L)

        authService.logout(token)

        Mockito.verify(redisTemplate, Mockito.never()).opsForValue()
    }
}
