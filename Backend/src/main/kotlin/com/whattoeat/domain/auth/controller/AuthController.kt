package com.whattoeat.domain.auth.controller

import com.whattoeat.domain.auth.dto.AuthUserResponse
import com.whattoeat.domain.auth.dto.LoginRequest
import com.whattoeat.domain.auth.dto.OAuthExchangeRequest
import com.whattoeat.domain.auth.dto.SignUpRequest
import com.whattoeat.domain.auth.service.AuthService
import com.whattoeat.global.exception.InvalidCredentialsException
import com.whattoeat.global.rq.Rq
import com.whattoeat.global.rsData.RsData
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService, private val rq: Rq) {

    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignUpRequest
    ): ResponseEntity<RsData<AuthUserResponse>> {
        val result = authService.signupAndLogin(request)
        rq.setCookie("accessToken", result.accessToken, 60 * 60)
        rq.setCookie("refreshToken", result.refreshToken, 60 * 60 * 24 * 7)
        return ResponseEntity.ok(
            RsData.success(
                result.userProfile,
                "회원가입이 완료되었습니다."
            )
        )
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<RsData<AuthUserResponse>> {
        val result = authService.login(request)
        rq.setCookie("accessToken", result.accessToken, 60 * 60)
        rq.setCookie("refreshToken", result.refreshToken, 60 * 60 * 24 * 7)

        return ResponseEntity.ok(
            RsData.success(
                result.userProfile,
                "로그인 성공"
            )
        )
    }

    @PostMapping("/oauth/exchange")
    fun exchangeOAuthCode(
        @Valid @RequestBody request: OAuthExchangeRequest
    ): ResponseEntity<RsData<AuthUserResponse>> {
        val result = authService.exchangeOAuthCode(request.code)
        rq.setCookie("accessToken", result.accessToken, 60 * 60)
        rq.setCookie("refreshToken", result.refreshToken, 60 * 60 * 24 * 7)

        return ResponseEntity.ok(
            RsData.success(
                result.userProfile,
                "로그인 성공"
            )
        )
    }

    @PostMapping("/reissue")
    fun reissue(): ResponseEntity<RsData<Void?>> {
        val refreshToken = rq.getCookieValue("refreshToken")
        if (refreshToken.isNullOrBlank()) {
            throw InvalidCredentialsException("Refresh Token이 필요합니다.")
        }
        val response = authService.reissue(refreshToken)

        rq.setCookie("accessToken", response.accessToken, 60 * 60)
        rq.setCookie("refreshToken", response.refreshToken, 60 * 60 * 24 * 7)

        return ResponseEntity.ok(RsData.success(null, "토큰이 갱신되었습니다."))
    }

    @PostMapping("/logout")
    fun logout(): RsData<Void?> {
        val accessToken = rq.getCookieValue("accessToken")
        val refreshToken = rq.getCookieValue("refreshToken")

        try {
            authService.logout(accessToken, refreshToken)
        } finally {
            // 토큰 파싱/Redis 처리가 실패하더라도 브라우저 쿠키는 반드시 지운다.
            rq.delCookie("accessToken")
            rq.delCookie("refreshToken")
        }

        return RsData.success(null, "로그아웃 되었습니다.")
    }
}
