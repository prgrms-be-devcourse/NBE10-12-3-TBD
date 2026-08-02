package com.whattoeat.domain.feedlike.controller

import com.whattoeat.domain.feedlike.dto.FeedLikeResponse
import com.whattoeat.domain.feedlike.service.FeedLikeService
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.AlreadyLikedFeedException
import com.whattoeat.global.exception.FeedLikeNotFoundException
import com.whattoeat.global.exception.FeedNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.security.CustomUserDetailsService
import java.time.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.MethodParameter
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@WebMvcTest(
    controllers = [FeedLikeController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
)
@AutoConfigureMockMvc(addFilters = false)
@Import(FeedLikeControllerTest.AuthenticationPrincipalTestConfig::class)
class FeedLikeControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var feedLikeService: FeedLikeService

    @MockitoBean
    private lateinit var jwtUtil: JwtUtil

    @MockitoBean
    private lateinit var customUserDetailsService: CustomUserDetailsService

    @MockitoBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun like_success() {
        val response = FeedLikeResponse.of(2L, 1, true)
        given(feedLikeService.like(1L, 2L)).willReturn(response)

        mockMvc.perform(post("/api/v1/feeds/2/like").with(userDetails(1L)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("좋아요를 눌렀습니다."))
            .andExpect(jsonPath("$.data.feedId").value(2L))
            .andExpect(jsonPath("$.data.likeCount").value(1))
            .andExpect(jsonPath("$.data.isLikedByMe").value(true))
    }

    @Test
    fun unlike_success() {
        val response = FeedLikeResponse.of(2L, 0, false)
        given(feedLikeService.unlike(1L, 2L)).willReturn(response)

        mockMvc.perform(delete("/api/v1/feeds/2/like").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("좋아요를 취소했습니다."))
            .andExpect(jsonPath("$.data.feedId").value(2L))
            .andExpect(jsonPath("$.data.likeCount").value(0))
            .andExpect(jsonPath("$.data.isLikedByMe").value(false))
    }

    @Test
    fun isLiked_true() {
        val response = FeedLikeResponse.of(2L, 1, true)
        given(feedLikeService.getLikeStatus(1L, 2L)).willReturn(response)

        mockMvc.perform(get("/api/v1/feeds/2/like").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.feedId").value(2L))
            .andExpect(jsonPath("$.data.likeCount").value(1))
            .andExpect(jsonPath("$.data.isLikedByMe").value(true))
    }

    @Test
    fun isLiked_false() {
        val response = FeedLikeResponse.of(2L, 0, false)
        given(feedLikeService.getLikeStatus(1L, 2L)).willReturn(response)

        mockMvc.perform(get("/api/v1/feeds/2/like").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.feedId").value(2L))
            .andExpect(jsonPath("$.data.likeCount").value(0))
            .andExpect(jsonPath("$.data.isLikedByMe").value(false))
    }

    @Test
    fun like_already_liked_fails() {
        given(feedLikeService.like(1L, 2L))
            .willThrow(AlreadyLikedFeedException())

        mockMvc.perform(post("/api/v1/feeds/2/like").with(userDetails(1L)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 좋아요한 피드입니다."))
    }

    @Test
    fun unlike_not_found_fails() {
        willThrow(FeedLikeNotFoundException())
            .given(feedLikeService).unlike(1L, 2L)

        mockMvc.perform(delete("/api/v1/feeds/2/like").with(userDetails(1L)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("좋아요 관계가 존재하지 않습니다."))
    }

    @Test
    fun like_user_not_found_fails() {
        given(feedLikeService.like(1L, 2L))
            .willThrow(UserNotFoundException(1L))

        mockMvc.perform(post("/api/v1/feeds/2/like").with(userDetails(1L)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("User not found: 1"))
    }

    @Test
    fun like_feed_not_found_fails() {
        given(feedLikeService.like(1L, 999L))
            .willThrow(FeedNotFoundException(999L))

        mockMvc.perform(post("/api/v1/feeds/999/like").with(userDetails(1L)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Feed not found: 999"))
    }

    private fun userDetails(userId: Long): RequestPostProcessor =
        RequestPostProcessor { request ->
            val user = createUser(userId)
            val userDetails = CustomUserDetails(user)
            val authentication =
                UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities,
                )
            SecurityContextHolder.getContext().authentication = authentication
            request.userPrincipal = authentication
            request
        }

    private fun createUser(id: Long): User {
        val user =
            User.builder()
                .loginId("user$id")
                .password("password")
                .nickname("user$id")
                .profileImage("profile.jpg")
                .email("user$id@test.com")
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .build()
        ReflectionTestUtils.setField(user, "id", id)
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 7, 2, 12, 0))
        return user
    }

    @TestConfiguration
    class AuthenticationPrincipalTestConfig : WebMvcConfigurer {
        override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
            resolvers.add(
                object : HandlerMethodArgumentResolver {
                    override fun supportsParameter(parameter: MethodParameter): Boolean =
                        parameter.hasParameterAnnotation(AuthenticationPrincipal::class.java) &&
                            CustomUserDetails::class.java.isAssignableFrom(parameter.parameterType)

                    override fun resolveArgument(
                        parameter: MethodParameter,
                        mavContainer: ModelAndViewContainer?,
                        webRequest: NativeWebRequest,
                        binderFactory: WebDataBinderFactory?,
                    ): Any =
                        SecurityContextHolder.getContext().authentication!!.principal!!
                },
            )
        }
    }
}
