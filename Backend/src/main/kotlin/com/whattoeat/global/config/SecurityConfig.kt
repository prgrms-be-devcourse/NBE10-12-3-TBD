package com.whattoeat.global.config

import com.whattoeat.global.jwt.JwtAuthenticationFilter
import com.whattoeat.global.security.CustomOAuth2AuthorizationRequestResolver
import com.whattoeat.global.security.CustomOAuth2LoginSuccessHandler
import com.whattoeat.global.security.CustomOAuth2UserService
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val customOAuth2LoginSuccessHandler: CustomOAuth2LoginSuccessHandler,
    private val customOAuth2AuthorizationRequestResolver: CustomOAuth2AuthorizationRequestResolver,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.frontend.url:http://localhost:3000}")
    private lateinit var frontendUrl: String

    @Bean
    @Profile("dev")
    fun devSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { userinfo -> userinfo.userService(customOAuth2UserService) }
                    .authorizationEndpoint { endpoint ->
                        endpoint.authorizationRequestResolver(customOAuth2AuthorizationRequestResolver)
                    }
                    .successHandler(customOAuth2LoginSuccessHandler)
            }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Profile("!dev")
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS).permitAll()
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/signup",
                        "/api/v1/auth/oauth/exchange",
                        "/api/v1/auth/reissue",
                        "/api/v1/auth/logout",
                        "/api/v1/restaurants",
                        "/api/v1/restaurants/**",
                        "/uploads/**",
                        "/oauth2/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                    )
                    .permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { userinfo -> userinfo.userService(customOAuth2UserService) }
                    .authorizationEndpoint { endpoint ->
                        endpoint.authorizationRequestResolver(customOAuth2AuthorizationRequestResolver)
                    }
                    .successHandler(customOAuth2LoginSuccessHandler)
                    .failureHandler { _, response, exception ->
                        log.error("[OAuth2] login failed: {}", exception.message, exception)
                        response.sendRedirect("/login?error")
                    }
            }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:3000", frontendUrl)
            allowedMethods = listOf("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
