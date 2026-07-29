package com.whattoeat.global.rq

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class Rq(
    private val request: HttpServletRequest,
    private val response: HttpServletResponse,
) {
    @Value("\${cookie.secure:false}")
    private var secure: Boolean = false

    @Value("\${cookie.same-site:Lax}")
    private var cookieSameSite: String = "Lax"

    @JvmOverloads
    fun getCookieValue(name: String, defaultValue: String? = null): String? =
        request.cookies
            ?.firstOrNull { it.name == name && !it.value.isNullOrBlank() }
            ?.value
            ?: defaultValue

    @JvmOverloads
    fun setCookie(name: String, value: String?, maxAge: Int = 60 * 60 * 24) {
        val cookieValue = value ?: ""
        val cookie = Cookie(name, cookieValue).apply {
            isHttpOnly = true
            secure = this@Rq.secure
            path = "/"
            this.maxAge = if (cookieValue.isBlank()) 0 else maxAge
            setAttribute("SameSite", cookieSameSite)
        }
        response.addCookie(cookie)
    }

    fun delCookie(name: String) {
        setCookie(name, null)
    }

    fun setReadableCookie(name: String, value: String?, maxAge: Int) {
        val cookieValue = value ?: ""
        val cookie = Cookie(name, cookieValue).apply {
            isHttpOnly = false
            secure = this@Rq.secure
            path = "/"
            this.maxAge = if (cookieValue.isBlank()) 0 else maxAge
            setAttribute("SameSite", cookieSameSite)
        }
        response.addCookie(cookie)
    }
}
