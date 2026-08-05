package com.whattoeat.domain.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.auth.dto.*
import com.whattoeat.domain.auth.service.AuthService
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.global.config.SecurityConfig
import com.whattoeat.global.exception.DuplicateLoginIdException
import com.whattoeat.global.exception.DuplicateNicknameException
import com.whattoeat.global.exception.InvalidCredentialsException
import com.whattoeat.global.jwt.JwtAuthenticationFilter
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.rq.Rq
import com.whattoeat.global.security.CustomUserDetailsService
import org.hamcrest.Matchers
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [AuthController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
    excludeFilters = [ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = [JwtAuthenticationFilter::class, SecurityConfig::class
        ]
    )]
)
@AutoConfigureMockMvc(addFilters = false)
internal class AuthControllerTest {
    @TestConfiguration
    internal class TestSecurityConfig {
        @Bean
        fun filterChain(http: HttpSecurity): SecurityFilterChain {
            http.csrf{ it.disable() }.authorizeHttpRequests { a -> a.anyRequest().permitAll() }
            return http.build()
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper()

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var jwtUtil: JwtUtil

    @MockitoBean
    private lateinit var rq: Rq

    @MockitoBean
    private lateinit var customUserDetailsService: CustomUserDetailsService


    // ========== POST /api/v1/auth/signup ==========
    @Test
    @DisplayName("정상 입력으로 회원가입 성공 시 200 반환 및 쿠키 설정")
    fun signupSuccess() {
        val request = SignUpRequest("test@tset.com", "pass1234", "testnick")
        val userProfile = AuthUserResponse(
            1L, "testnick", null, "test@test.com", Provider.LOCAL, Role.USER, LocalDateTime.now()
        )
        val result = AuthResult("mocked-access-token", "mocked-refresh-token", userProfile)
        given(authService.signupAndLogin(request))
            .willReturn(result)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.nickname").value("testnick"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.email").value("test@test.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.provider").value("LOCAL"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("회원가입이 완료되었습니다."))

        then(rq).should().setCookie("accessToken", "mocked-access-token", 60 * 60)
        then(rq).should().setCookie("refreshToken", "mocked-refresh-token", 60 * 60 * 24 * 7)
    }

    @Test
    @DisplayName("아이디 중복 시 409 반환")
    fun signupFailDuplicateLoginId() {
        val request = SignUpRequest("test@tset.com", "pass1234", "testnick")
            given(authService.signupAndLogin(request))
                .willThrow(DuplicateLoginIdException("이미 사용 중인 아이디입니다."))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isConflict())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                .value("이미 사용 중인 아이디입니다."))
    }

    @Test
    @DisplayName("닉네임 중복 시 409 반환")
    fun signupFailDuplicateNickname() {
        val request = SignUpRequest("test@tset.com", "pass1234", "testnick")
        given(authService.signupAndLogin(request)).willThrow(DuplicateNicknameException("이미 사용 중인 닉네임입니다."))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isConflict())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("이미 사용 중인 닉네임입니다."))
    }

    @Test
    @DisplayName("아이디 빈 값으로 회원가입 시 400 반환")
    fun signupFailBlankLoginId() {
        val request = SignUpRequest("", "pass1234", "testnick")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(Matchers.containsString("loginId")))
    }

    @Test
    @DisplayName("비밀번호 4자 미만으로 회원가입 시 400 반환")
    fun signupFailShortPassword() {
        val request = SignUpRequest("test@tset.com", "abc", "testnick")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(Matchers.containsString("password")))
    }

    @Test
    @DisplayName("이메일 형식이 아닌 값으로 회원가입 시 400 반환")
    fun signupFailInvalidEmail() {
        val request = SignUpRequest("invalid-email", "pass1234", "testnick")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(Matchers.containsString("loginId")))
    }

    // ========== POST /api/v1/auth/login ==========
    @Test
    @DisplayName("정상 아이디/비밀번호로 로그인 성공 시 200과 토큰 반환")
    fun loginSuccess() {
        val request = LoginRequest("test@tset.com", "pass1234")
        val userProfile = AuthUserResponse(
            1L, "testnick", null, "test@test.com", Provider.LOCAL, Role.USER, LocalDateTime.now()
        )
        val result = AuthResult("mocked-access-token", "mocked-refresh-token", userProfile)
        given(authService.login(request)).willReturn(result)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.userId").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.nickname").value("testnick"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.email").value("test@test.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.provider").value("LOCAL"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.role").value("USER"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("로그인 성공"))

        then(rq).should().setCookie("accessToken", "mocked-access-token", 60 * 60)
        then(rq).should().setCookie("refreshToken", "mocked-refresh-token", 60 * 60 * 24 * 7)
    }

    @Test
    @DisplayName("아이디 또는 비밀번호 불일치 시 401 반환")
    fun loginFailInvalidCredentials() {
        val request = LoginRequest("test@tset.com", "wrongpass")
        given(authService.login(request)).willThrow(
            InvalidCredentialsException("아이디/비밀번호가 올바르지 않습니다.")
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                .value("아이디/비밀번호가 올바르지 않습니다."))
    }

    @Test
    @DisplayName("아이디 빈 값으로 로그인 시 400 반환")
    fun loginFailBlankLoginId() {
        val request = LoginRequest("", "pass1234")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                .value(Matchers.containsString("loginId")))
    }

    // ========== POST /api/v1/auth/reissue ==========
    @Test
    @DisplayName("유효 refreshToken으로 재발급시 200 반환")
    fun refreshTokenSuccess() {
        given(rq.getCookieValue("refreshToken")).willReturn("valid-token")
        val res = TokenResponse("new-access-token", "new-refresh-token")
        given(authService.reissue("valid-token")).willReturn(res)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/reissue"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("토큰이 갱신되었습니다."))

        then(rq).should().setCookie("accessToken", "new-access-token", 60 * 60)
        then(rq).should().setCookie("refreshToken", "new-refresh-token", 60 * 60 * 24 * 7)
    }

    @Test
    @DisplayName("refreshToken 없이 재발급 요청 시 401 반환")
    fun refreshTokenMissing() {
        given(rq.getCookieValue("refreshToken")).willReturn(null)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/reissue"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized())
    }

    // ========== POST /api/v1/auth/logout ==========
    @Test
    @DisplayName("accessToken쿠키와 함께 로그아웃 요청 시 200 반환")
    fun logoutSuccess() {
        given(rq.getCookieValue("accessToken")).willReturn("valid-token")
        given(rq.getCookieValue("refreshToken")).willReturn("valid-refresh-token")

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("로그아웃 되었습니다."))

        then(authService).should().logout("valid-token", "valid-refresh-token")
        then(rq).should().delCookie("accessToken")
        then(rq).should().delCookie("refreshToken")
    }

    @Test
    @DisplayName("accessToken쿠키가 만료돼 없어도 refreshToken으로 서비스가 호출되고 쿠키는 지워진다")
    fun logoutWithoutAccessTokenStillInvalidatesRefreshToken() {
        given(rq.getCookieValue("accessToken")).willReturn(null)
        given(rq.getCookieValue("refreshToken")).willReturn("valid-refresh-token")

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))

        then(authService).should().logout(null, "valid-refresh-token")
        then(rq).should().delCookie("accessToken")
        then(rq).should().delCookie("refreshToken")
    }
}
