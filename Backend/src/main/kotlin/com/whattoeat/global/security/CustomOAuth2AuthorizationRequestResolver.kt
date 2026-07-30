package com.whattoeat.global.security

import jakarta.servlet.http.HttpServletRequest

import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class CustomOAuth2AuthorizationRequestResolver(
    private val clientRegistrationRepository: ClientRegistrationRepository
) : OAuth2AuthorizationRequestResolver {
    private val log = LoggerFactory.getLogger(CustomOAuth2AuthorizationRequestResolver::class.java)


    private fun defaultResolver(): DefaultOAuth2AuthorizationRequestResolver {
        return DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
        )
    }

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val authRequest = defaultResolver().resolve(request)
        return customize(authRequest, request)
    }

    override fun resolve(request: HttpServletRequest, clientRegiId: String): OAuth2AuthorizationRequest? {
        val authRequest = defaultResolver().resolve(request, clientRegiId)
        return customize(authRequest, request)
    }

    private fun customize(
        authRequest: OAuth2AuthorizationRequest?,
        request: HttpServletRequest
    ): OAuth2AuthorizationRequest? {
        if (authRequest == null) return null

        // 요청 파라미터에서 redirectUri 가져오기
        var redirectUri = request.getParameter("redirectUri")
        if (redirectUri == null || redirectUri.isBlank()) redirectUri = "http://localhost:3000"

        //CSRF 방지용 nonce 추가
        val originState: String = UUID.randomUUID().toString()

        // redirectUri#originState 결합
        val rawState = "$redirectUri#$originState"

        // Base64 URL-safe 인코딩
        val encodeState = Base64.getUrlEncoder().encodeToString(rawState.toByteArray(StandardCharsets.UTF_8))

        log.info(
            "[OAuth2] authorization request built. redirectUri={}, state={}",
            redirectUri,
            encodeState
        )

        return OAuth2AuthorizationRequest.from(authRequest)
            .state(encodeState) //state 교체
            .build()
    }
}
