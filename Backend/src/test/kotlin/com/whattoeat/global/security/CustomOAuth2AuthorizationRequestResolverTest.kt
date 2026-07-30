package com.whattoeat.global.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import java.nio.charset.StandardCharsets
import java.util.*

class CustomOAuth2AuthorizationRequestResolverTest {
    private lateinit var resolver: CustomOAuth2AuthorizationRequestResolver
    private lateinit var request: MockHttpServletRequest

    @BeforeEach
    fun setUp() {
        val kakao = ClientRegistration.withRegistrationId("kakao")
            .clientId("test-cid")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
            .scope("profile_nickname", "profile_image", "account_email")
            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
            .tokenUri("https://kauth.kakao.com/oauth/token")
            .userNameAttributeName("id")
            .clientName("kakao").build()

        val repo: ClientRegistrationRepository = InMemoryClientRegistrationRepository(kakao)
        resolver = CustomOAuth2AuthorizationRequestResolver(repo)
        request = MockHttpServletRequest()
        request.serverName = "localhost"
        request.serverPort = 8080
        request.scheme = "http"
    }

    @Test
    @DisplayName("요청 생성")
    fun resolve() {
        request.setParameter("redirectUri", "https://localhost:3000/mypage")

        val result = requireNotNull(resolver.resolve(request, "kakao"))
        assertThat(result.clientId).isEqualTo("test-cid")
    }

    @Test
    @DisplayName("state redirectUri 인코딩")
    fun state_redirectUri() {
        val customRedirectUri = "http://localhost:5173/feed/123"
        request.setParameter("redirectUri", customRedirectUri)
        val result = requireNotNull(resolver.resolve(request, "kakao"))
        val decode = String(
            Base64.getUrlDecoder().decode(requireNotNull(result.state)),
            StandardCharsets.UTF_8
        )
        assertThat(decode.split("#", limit = 2)[0]).isEqualTo(customRedirectUri)
    }

    @Test
    @DisplayName("redirectUri 없으면 기본값으로")
    fun default_redirectUri() {
        val result = requireNotNull(resolver.resolve(request, "kakao"))
        val decode = String(
            Base64.getUrlDecoder().decode(requireNotNull(result.state)),
            StandardCharsets.UTF_8
        )
        assertThat(decode.split("#", limit = 2)[0]).isEqualTo("http://localhost:3000")
    }
}
