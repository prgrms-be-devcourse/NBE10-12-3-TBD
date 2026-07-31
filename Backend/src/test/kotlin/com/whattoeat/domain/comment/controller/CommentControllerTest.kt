package com.whattoeat.domain.comment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.whattoeat.domain.comment.dto.CommentRequest
import com.whattoeat.domain.comment.dto.CommentResponse
import com.whattoeat.domain.comment.service.CommentService
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.security.CustomUserDetailsService
import com.whattoeat.global.exception.CommentNotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDateTime

@WebMvcTest(controllers = [CommentController::class], excludeAutoConfiguration = [SecurityAutoConfiguration::class])
@AutoConfigureMockMvc(addFilters = false)
internal class CommentControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())

    @MockitoBean
    private lateinit var commentService: CommentService

    @MockitoBean
    private lateinit var jwtUtil: JwtUtil

    @MockitoBean
    private lateinit var customUserDetailsService: CustomUserDetailsService

    @MockitoBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @BeforeEach
    fun setupSecurityContext() {
        val user = User(
            nickname = "testUser", email = "test@test.com", provider = Provider.LOCAL, role = Role.USER
        )
        ReflectionTestUtils.setField(user, "id", 1L)
        val userDetails = CustomUserDetails(user)

        val context = SecurityContextHolder.createEmptyContext()
        context.setAuthentication(
            UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
            )
        )
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun cleanSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun createResponse(id: Long, content: String, userId: Long, nickname: String): CommentResponse {
        return CommentResponse(id, content, userId, nickname, LocalDateTime.now())
    }

    @Test
    fun getComments_성공() {
        val responses = listOf(
            createResponse(1L, "첫 번째 댓글", 1L, "user1"),
            createResponse(2L, "두 번째 댓글", 2L, "user2")
        )
        given(commentService.getComments(1L)).willReturn(responses)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/feeds/1/comments"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.success").value(true)
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.message").value("댓글 목록 조회 성공")
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.data.length()").value(2)
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.data[0].content").value("첫 번째 댓글")
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.data[1].content").value("두 번째 댓글")
            )
    }

    @Test
    fun getComments_댓글이_없으면_빈_배열_반환() {
        given(commentService.getComments(1L)).willReturn(listOf())

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/feeds/1/comments"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.success").value(true)
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.data.length()").value(0)
            )
    }

    @Test
    fun createComment_성공() {
        val request = CommentRequest("새 댓글")
        val response = createResponse(1L, "새 댓글", 1L, "user1")
        given(commentService.createComment(1L, 1L, request))
            .willReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/feeds/1/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.success").value(true)
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.message").value("댓글 작성 성공")
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.data.content").value("새 댓글")
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.data.nickname").value("user1")
            )
    }

    @Test
    fun createComment_content가_blank이면_400() {
        val request = CommentRequest("")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/feeds/1/comments")
                .param("userId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
    }

    @Test
    fun createComment_content가_500자_초과이면_400() {
        val request = CommentRequest("a".repeat(501))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/feeds/1/comments")
                .param("userId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
    }

    @Test
    fun deleteComment_성공() {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/feeds/1/comments/1"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.success").value(true)
            )
            .andExpect(
                MockMvcResultMatchers
                    .jsonPath("$.message").value("댓글이 삭제되었습니다.")
            )
        then(commentService).should().deleteComment(1L, 1L, 1L)
    }

    @Test
    fun deleteComment_다른_피드의_댓글이면_404() {
        willThrow(CommentNotFoundException(1L))
            .given(commentService).deleteComment(999L,1L,1L)
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/feeds/999/comments/1"))
        .andExpect(MockMvcResultMatchers.status().isNotFound())
        then(commentService).should().deleteComment(999L, 1L, 1L)
    }

    @Test
    fun deleteComment_타인의_댓글이면_403() {
        willThrow(AccessDeniedException("본인이 작성한 댓글만 삭제할 수 있습니다."))
            .given(commentService).deleteComment(1L, 1L, 1L)
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/feeds/1/comments/1"))
            .andExpect(MockMvcResultMatchers.status().isForbidden)
        then(commentService).should().deleteComment(1L, 1L, 1L)
    }
}
