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
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class CustomOAuth2LoginSuccessHandler(
    private val authService: AuthService,
    //application.yaml 파일에 app: fonrtend: url: 없으면 localhost:3000 동작
    @Value("\${app.frontend.url:http://localhost:3000}")
    private val frontendUrl: String
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(CustomOAuth2LoginSuccessHandler::class.java)

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
        val code = authService.createOAuthCode(user.id)

        var redirectUri = frontendUrl
        val stateParam = request.getParameter("state")
        log.info("[OAuth2] stateParam={}", stateParam)

        if (stateParam != null && !stateParam.isBlank()) {
            // Base64 URL-safe 디코딩
            val decodeState = String(
                Base64.getUrlDecoder().decode(stateParam),
                StandardCharsets.UTF_8
            )
            redirectUri = decodeState.split("#".toRegex(), limit = 2).toTypedArray()[0]
        }
        redirectUri = redirectUri + "/oauth/callback?code=" + UriUtils.encode(code, StandardCharsets.UTF_8)
        log.info("[OAuth2] redirecting to {}", redirectUri)
        response.sendRedirect(redirectUri)
    }
}
