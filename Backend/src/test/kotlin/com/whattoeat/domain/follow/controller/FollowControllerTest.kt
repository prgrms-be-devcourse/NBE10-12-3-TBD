package com.whattoeat.domain.follow.controller

import com.whattoeat.domain.follow.entity.Follow
import com.whattoeat.domain.follow.service.FollowService
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.AlreadyFollowingException
import com.whattoeat.global.exception.FollowNotFoundException
import com.whattoeat.global.exception.SelfFollowNotAllowedException
import com.whattoeat.global.exception.UserNotFoundException
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.security.CustomUserDetailsService
import java.time.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willDoNothing
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.MethodParameter
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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
    controllers = [FollowController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
)
@AutoConfigureMockMvc(addFilters = false)
@Import(FollowControllerTest.AuthenticationPrincipalTestConfig::class)
class FollowControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var followService: FollowService

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
    fun follow_success() {
        val follow = createFollow(10L, 1L, "me", "me.jpg", 2L, "target", "target.jpg")
        given(followService.follow(1L, 2L)).willReturn(follow)

        mockMvc.perform(post("/api/v1/follows/2").with(userDetails(1L)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("팔로우했습니다."))
            .andExpect(jsonPath("$.data.followId").value(10L))
            .andExpect(jsonPath("$.data.followerId").value(1L))
            .andExpect(jsonPath("$.data.followingId").value(2L))
            .andExpect(jsonPath("$.data.createdAt").exists())
    }

    @Test
    fun unfollow_success() {
        willDoNothing().given(followService).unfollow(1L, 2L)

        mockMvc.perform(delete("/api/v1/follows/2").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("언팔로우했습니다."))
    }

    @Test
    fun getFollowings_success() {
        val follow = createFollow(10L, 1L, "me", "me.jpg", 2L, "target", "target.jpg")
        given(followService.getFollowings(eq(1L), any(Pageable::class.java) ?: Pageable.unpaged()))
            .willReturn(PageImpl(listOf(follow)))
        given(followService.isFollowing(1L, 2L)).willReturn(true)

        mockMvc.perform(get("/api/v1/follows/followings").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].userId").value(2L))
            .andExpect(jsonPath("$.data.content[0].nickname").value("target"))
            .andExpect(jsonPath("$.data.content[0].profileImage").value("target.jpg"))
            .andExpect(jsonPath("$.data.content[0].isFollowedByMe").value(true))
            .andExpect(jsonPath("$.data.content[0].createdAt").exists())
            .andExpect(jsonPath("$.data.totalElements").value(1))
    }

    @Test
    fun getFollowers_success() {
        val follow = createFollow(10L, 2L, "follower", "follower.jpg", 1L, "me", "me.jpg")
        given(followService.getFollowers(eq(1L), any(Pageable::class.java) ?: Pageable.unpaged()))
            .willReturn(PageImpl(listOf(follow)))
        given(followService.isFollowing(1L, 2L)).willReturn(false)

        mockMvc.perform(get("/api/v1/follows/followers").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].userId").value(2L))
            .andExpect(jsonPath("$.data.content[0].nickname").value("follower"))
            .andExpect(jsonPath("$.data.content[0].profileImage").value("follower.jpg"))
            .andExpect(jsonPath("$.data.content[0].isFollowedByMe").value(false))
            .andExpect(jsonPath("$.data.content[0].createdAt").exists())
            .andExpect(jsonPath("$.data.totalElements").value(1))
    }

    @Test
    fun follow_self_fails() {
        given(followService.follow(1L, 1L))
            .willThrow(SelfFollowNotAllowedException())

        mockMvc.perform(post("/api/v1/follows/1").with(userDetails(1L)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("자기 자신을 팔로우할 수 없습니다."))
    }

    @Test
    fun follow_already_following_fails() {
        given(followService.follow(1L, 2L))
            .willThrow(AlreadyFollowingException())

        mockMvc.perform(post("/api/v1/follows/2").with(userDetails(1L)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 팔로우 중인 사용자입니다."))
    }

    @Test
    fun unfollow_not_found_fails() {
        willThrow(FollowNotFoundException())
            .given(followService).unfollow(1L, 2L)

        mockMvc.perform(delete("/api/v1/follows/2").with(userDetails(1L)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("팔로우 관계가 존재하지 않습니다."))
    }

    @Test
    fun follow_user_not_found_fails() {
        given(followService.follow(1L, 999L))
            .willThrow(UserNotFoundException(999L))

        mockMvc.perform(post("/api/v1/follows/999").with(userDetails(1L)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("User not found: 999"))
    }

    private fun userDetails(userId: Long): RequestPostProcessor =
        RequestPostProcessor { request ->
            val user = createUser(userId, "user$userId", "profile.jpg")
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

    private fun createFollow(
        id: Long,
        followerId: Long,
        followerNickname: String,
        followerProfileImage: String,
        followingId: Long,
        followingNickname: String,
        followingProfileImage: String,
    ): Follow {
        val follow =
            Follow.of(
                createUser(followerId, followerNickname, followerProfileImage),
                createUser(followingId, followingNickname, followingProfileImage),
            )
        ReflectionTestUtils.setField(follow, "id", id)
        ReflectionTestUtils.setField(follow, "createdAt", LocalDateTime.of(2026, 6, 30, 12, 0))
        return follow
    }

    private fun createUser(id: Long, nickname: String, profileImage: String): User {
        val user =
            User.builder()
                .loginId("user$id")
                .password("password")
                .nickname(nickname)
                .profileImage(profileImage)
                .email("user$id@test.com")
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .build()
        ReflectionTestUtils.setField(user, "id", id)
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 6, 30, 12, 0))
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
