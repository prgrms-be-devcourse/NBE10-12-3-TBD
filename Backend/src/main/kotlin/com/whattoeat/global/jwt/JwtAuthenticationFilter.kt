package com.whattoeat.global.jwt

import com.whattoeat.global.security.CustomUserDetailsService
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val userDetailsService: CustomUserDetailsService,
    private val redisTemplate: RedisTemplate<String, String>,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }
        try {
            val claims = jwtUtil.parseToken(token)
            val tokenType = claims.get("tokenType", String::class.java)

            if ("access" == tokenType) {
                val userId = jwtUtil.getUserId(token)
                if (redisTemplate.hasKey("blacklist:$token") == false) {
                    val userDetails = userDetailsService.loadUserByUsername(userId.toString())
                    val authenticationToken = UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.authorities,
                    )
                    SecurityContextHolder.getContext().authentication = authenticationToken
                }
            }
        } catch (e: JwtException) {
            // 토큰이 만료/위변조/유효하지 않아도 여기서 바로 401을 응답하지 않는다.
            // 이 필터는 permitAll 여부와 상관없이 모든 요청에서 실행되므로, 여기서 응답을
            // 끊어버리면 공개 엔드포인트까지 막힌다. 인증 정보만 설정하지 않고 체인을 계속 진행해서,
            // 실제로 인증이 필요한 요청만 Spring Security 인가 단계에서 401 처리되도록 한다.
        }
        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path == "/api/v1/auth/reissue" || path == "/api/v1/auth/logout"
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }
        return request.cookies?.firstOrNull { it.name == "accessToken" }?.value
    }
}
