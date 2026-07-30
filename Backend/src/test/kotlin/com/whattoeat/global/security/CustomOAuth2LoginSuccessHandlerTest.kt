package com.whattoeat.global.security

import com.whattoeat.domain.auth.service.AuthService
import com.whattoeat.domain.user.entity.User
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import java.nio.charset.StandardCharsets
import java.util.*

@ExtendWith(MockitoExtension::class)
class CustomOAuth2LoginSuccessHandlerTest {
    @Mock
    private lateinit var authService: AuthService

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var authentication: Authentication

    private lateinit var handler: CustomOAuth2LoginSuccessHandler

    private lateinit var req: MockHttpServletRequest
    private lateinit var res: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        handler = CustomOAuth2LoginSuccessHandler(authService = authService, frontendUrl = "http://localhost:3000")

        req = MockHttpServletRequest()
        res = MockHttpServletResponse()

        val userId=1L
        given(user.id).willReturn(userId)
        given(authentication.principal).willReturn(KakaoOAuth2User(user))
        given(authService.createOAuthCode(userId)).willReturn("one-time-code")
    }

    @Test
    @DisplayName("로그인 성공 시 1회용 코드를 발급하고 프론트 콜백으로 리다이렉트한다")
    @Throws(Exception::class)
    fun success() {
        handler.onAuthenticationSuccess(req, res, authentication)

        Assertions.assertThat(res.redirectedUrl)
            .isEqualTo("http://localhost:3000/oauth/callback?code=one-time-code")
    }

    @Test
    @DisplayName("state로 redirectUri 복원")
    fun state_redirect_uri() {
        val state = Base64.getUrlEncoder().encodeToString(
            "http://localhost:3000/mypage#uuid".toByteArray(StandardCharsets.UTF_8)
        )

        req.setParameter("state", state)
        handler.onAuthenticationSuccess(req, res, authentication)

        assertThat(res.redirectedUrl)
            .isEqualTo("http://localhost:3000/mypage/oauth/callback?code=one-time-code")
    }
}
