package com.whattoeat.global.security

import com.whattoeat.domain.auth.service.AuthService
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class CustomOAuth2LoginSuccessHandler(
    private val authService: AuthService,
    //application.yaml 파일에 app: frontend: url: 없으면 localhost:3000 동작
    @param:Value("\${app.frontend.url:http://localhost:3000}")
    private val frontendUrl: String
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(CustomOAuth2LoginSuccessHandler::class.java)

    private val allowedFrontendUri = URI.create(frontendUrl).also { uri ->
        val isHTTP = uri.scheme?.equals("http", ignoreCase = true) == true
        val isHTTPS = uri.scheme?.equals("https", ignoreCase = true) == true

        require(
            (isHTTP || isHTTPS) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
        ) { "app.frontend.url은 유효한 HTTP(S) URL 이어야 합니다." }
    }

    @Throws(IOException::class, ServletException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        log.info("[OAuth2] onAuthenticationSuccess called")
        val kakaoUser = authentication.principal as KakaoOAuth2User
        val user = kakaoUser.user
        log.info("[OAuth2] authenticated user id={}", user.id)

        // 여기서 쿠키를 바로 굽지 않는다: 이 응답은 프론트가 아니라 백엔드 자신의 도메인으로의
        // 직접 리다이렉트라서, 쿠키를 지금 세팅하면 백엔드 도메인에만 스코프된다.
        // 프론트가 /api 프록시로 이 코드를 교환할 때 쿠키를 세팅해야 프론트 도메인에 쿠키가 붙는다.
        val code = authService.createOAuthCode(checkNotNull(user.id){"인증 사용자 id가 없습니다."})

        val redirectUri = resolveRedirectUri(request.getParameter("state"))
        val encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8)
        val target = "${redirectUri.trimEnd('/')}/oauth/callback?code=$encodedCode"
        log.info("[OAuth2] redirecting to trusted frontend callback")

        response.sendRedirect(target)
    }

    private fun resolveRedirectUri(stateParam: String?): String {
        if (stateParam.isNullOrBlank()) return frontendUrl
        return try {
            val decodedState = String(
                Base64.getUrlDecoder().decode(stateParam),
                StandardCharsets.UTF_8,
            )
            val candidateValue = decodedState.substringBefore('#')
            val candidateUri = URI.create(candidateValue)

            if (isAllowedRedirectUri(candidateUri)) {
                candidateUri.toString()
            } else {
                log.warn("[OAuth2] blocked redirect outside allowed frontend origin")
                frontendUrl
            }
        } catch (_: IllegalArgumentException) {
            log.warn("[Oauth2] invalid OAuth2 state; falling back to frontendUrl")
            frontendUrl
        }
    }

    private fun isAllowedRedirectUri(candidate: URI): Boolean =
        candidate.isAbsolute &&
                candidate.userInfo == null &&
                candidate.scheme?.equals(allowedFrontendUri.scheme, ignoreCase = true) == true &&
                candidate.host?.equals(allowedFrontendUri.host, ignoreCase = true) == true &&
                effectivePort(candidate) == effectivePort(allowedFrontendUri)

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme?.equals("http", ignoreCase = true) == true -> 80
        uri.scheme?.equals("https", ignoreCase = true) == true -> 443

        else -> -1
    }
}
