package com.whattoeat.domain.notification.controller

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.notification.dto.NotificationCursorResponse
import com.whattoeat.domain.notification.dto.NotificationResponse
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.service.NotificationService
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.NotificationNotFoundException
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.security.CustomUserDetailsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [NotificationController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = false)
@Import(NotificationControllerTest.AuthenticationPrincipalTestConfig::class)
class NotificationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var notificationService: NotificationService

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

    private fun createUser(id: Long, nickname: String): User {
        val user = User.builder().nickname(nickname).email("$nickname@test.com").provider(Provider.LOCAL).build()
        ReflectionTestUtils.setField(user, "id", id)
        return user
    }

    private fun userDetails(userId: Long): RequestPostProcessor =
        RequestPostProcessor { request ->
            val user = createUser(userId, "user$userId")
            val userDetails = CustomUserDetails(user)
            val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
            SecurityContextHolder.getContext().authentication = authentication
            request.userPrincipal = authentication
            request
        }

    private fun createNotification(id: Long, receiver: User, actor: User, feed: Feed): Notification {
        val notification = Notification.of(
            receiver, actor, feed, NotificationType.NEW_FEED, "${actor.nickname}님이 새 글을 작성했습니다."
        )
        ReflectionTestUtils.setField(notification, "id", id)
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.now())
        return notification
    }

    @Test
    fun 알림_목록_커서_조회_성공() {
        val receiver = createUser(1L, "받는사람")
        val actor = createUser(2L, "작성자")
        val feed = Feed.builder().user(actor).content("맛집").build()
        ReflectionTestUtils.setField(feed, "id", 10L)

        val notification = createNotification(100L, receiver, actor, feed)
        val response = NotificationCursorResponse(
            content = listOf(NotificationResponse(notification)),
            nextCursor = 99L,
            hasNext = true
        )

        given(notificationService.getNotificationsByCursor(1L, null, 20)).willReturn(response)

        mockMvc.perform(get("/api/v1/notifications").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].id").value(100))
            .andExpect(jsonPath("$.data.content[0].actorId").value(2))
            .andExpect(jsonPath("$.data.content[0].actorNickname").value("작성자"))
            .andExpect(jsonPath("$.data.content[0].feedId").value(10))
            .andExpect(jsonPath("$.data.content[0].restaurantListId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].type").value("NEW_FEED"))
            .andExpect(jsonPath("$.data.content[0].isRead").value(false))
            .andExpect(jsonPath("$.data.nextCursor").value(99))
            .andExpect(jsonPath("$.data.hasNext").value(true))
    }

    @Test
    fun 알림_목록_커서_파라미터_전달() {
        val response = NotificationCursorResponse(emptyList(), null, false)
        given(notificationService.getNotificationsByCursor(1L, 50L, 10)).willReturn(response)

        mockMvc.perform(get("/api/v1/notifications?cursor=50&size=10").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun 안읽은_알림_개수_조회() {
        given(notificationService.getUnreadCount(1L)).willReturn(7L)

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.count").value(7))
    }

    @Test
    fun 알림_읽음_처리_성공() {
        val receiver = createUser(1L, "받는사람")
        val actor = createUser(2L, "작성자")
        val feed = Feed.builder().user(actor).content("맛집").build()
        ReflectionTestUtils.setField(feed, "id", 10L)

        val notification = createNotification(100L, receiver, actor, feed)
        notification.markAsRead()

        given(notificationService.markAsRead(1L, 100L)).willReturn(notification)

        mockMvc.perform(put("/api/v1/notifications/100/read").with(userDetails(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isRead").value(true))
            .andExpect(jsonPath("$.message").value("알림을 읽음 처리했습니다."))
    }

    @Test
    fun 알림_읽음_처리_존재하지않으면_404() {
        given(notificationService.markAsRead(1L, 999L)).willThrow(NotificationNotFoundException(999L))

        mockMvc.perform(put("/api/v1/notifications/999/read").with(userDetails(1L)))
            .andExpect(status().isNotFound)
    }

    @TestConfiguration
    class AuthenticationPrincipalTestConfig : WebMvcConfigurer {

        override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
            resolvers.add(object : HandlerMethodArgumentResolver {
                override fun supportsParameter(parameter: MethodParameter): Boolean =
                    parameter.hasParameterAnnotation(AuthenticationPrincipal::class.java) &&
                        CustomUserDetails::class.java.isAssignableFrom(parameter.parameterType)

                override fun resolveArgument(
                    parameter: MethodParameter,
                    mavContainer: ModelAndViewContainer?,
                    webRequest: NativeWebRequest,
                    binderFactory: WebDataBinderFactory?
                ): Any? = SecurityContextHolder.getContext().authentication?.principal
            })
        }
    }
}
