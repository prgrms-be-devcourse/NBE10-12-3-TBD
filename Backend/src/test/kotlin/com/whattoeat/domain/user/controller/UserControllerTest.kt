package com.whattoeat.domain.user.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.whattoeat.domain.user.dto.UpdateProfileRequest
import com.whattoeat.domain.user.dto.UserProfileResponse
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.service.UserService
import com.whattoeat.global.exception.UserNotFoundException
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.security.CustomUserDetailsService
import com.whattoeat.global.upload.ImageUploadService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [UserController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var jwtUtil: JwtUtil

    @MockitoBean
    private lateinit var customUserDetailsService: CustomUserDetailsService

    @MockitoBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @MockitoBean
    private lateinit var imageUploadService: ImageUploadService

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    // 테스트 요청에 인증 정보를 직접 SecurityContextHolder에 주입하는 헬퍼
    private fun withUserId(userId: Long): RequestPostProcessor =
        RequestPostProcessor { request ->
            val user = User.builder()
                .nickname("testUser")
                .email("test@example.com")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build()
            ReflectionTestUtils.setField(user, "id", userId)
            val userDetails = CustomUserDetails(user)

            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.authorities
            )
            SecurityContextHolder.setContext(context)
            request
        }

    private fun createResponse(
        id: Long,
        nickname: String,
        profileImage: String?,
        isOwnProfile: Boolean,
        isFollowing: Boolean
    ) = UserProfileResponse(
        id, nickname, profileImage, "test@example.com",
        Provider.LOCAL, LocalDateTime.now(), isOwnProfile, isFollowing
    )

    private fun updateRequest(nickname: String?, profileImage: String?) =
        UpdateProfileRequest(nickname, null, null, null)

    // ===================== getMe 테스트 =====================

    @Test
    fun getMe_본인_프로필_조회_성공() {
        val response = createResponse(1L, "testNickname", "profile.jpg", true, false)
        given(userService.getUser(1L, 1L)).willReturn(response)

        mockMvc.perform(get("/api/v1/users/me").with(withUserId(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isOwnProfile").value(true))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
            .andExpect(jsonPath("$.data.nickname").value("testNickname"))
    }

    // ===================== getUser 테스트 =====================

    @Test
    fun getUser_타인_프로필_조회시_팔로우_중() {
        val response = createResponse(2L, "otherUser", "other.jpg", false, true)
        given(userService.getUser(2L, 1L)).willReturn(response)

        mockMvc.perform(get("/api/v1/users/2").with(withUserId(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isOwnProfile").value(false))
            .andExpect(jsonPath("$.data.isFollowing").value(true))
    }

    @Test
    fun getUser_타인_프로필_조회시_팔로우_안함() {
        val response = createResponse(2L, "otherUser", "other.jpg", false, false)
        given(userService.getUser(2L, 1L)).willReturn(response)

        mockMvc.perform(get("/api/v1/users/2").with(withUserId(1L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isOwnProfile").value(false))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
    }

    @Test
    fun getUser_존재하지_않으면_404() {
        given(userService.getUser(999L, 1L)).willThrow(UserNotFoundException(999L))

        mockMvc.perform(get("/api/v1/users/999").with(withUserId(1L)))
            .andExpect(status().isNotFound)
    }

    // ===================== updateProfile 테스트 =====================

    @Test
    fun updateProfile_성공() {
        val request = updateRequest("newNickname", "new.jpg")
        val response = createResponse(1L, "newNickname", "new.jpg", true, false)
        given(userService.updateProfile(1L, 1L, request)).willReturn(response)

        mockMvc.perform(
            patch("/api/v1/users/me")
                .with(withUserId(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("newNickname"))
            .andExpect(jsonPath("$.data.profileImage").value("new.jpg"))
            .andExpect(jsonPath("$.data.isOwnProfile").value(true))
    }

    @Test
    fun updateProfile_존재하지_않으면_404() {
        val request = updateRequest("nickname", null)
        given(userService.updateProfile(999L, 999L, request))
            .willThrow(UserNotFoundException(999L))

        mockMvc.perform(
            patch("/api/v1/users/me")
                .with(withUserId(999L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun updateProfile_닉네임이_blank이면_400() {
        val request = updateRequest("", "image.jpg")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .with(withUserId(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun updateProfile_닉네임이_20자_초과이면_400() {
        val request = updateRequest("a".repeat(21), "image.jpg")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .with(withUserId(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun updateProfileImage_성공() {
        val response = createResponse(1L, "testUser", "/uploads/uuid.jpg", true, false)
        given(imageUploadService.uploadProfileImage(any(MultipartFile::class.java))).willReturn("/uploads/uuid.jpg")
        given(userService.updateProfileImage(1L, "/uploads/uuid.jpg")).willReturn(response)

        val image = MockMultipartFile(
            "image",
            "profile.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "image-content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/v1/users/me/image")
                .file(image).with { request ->
                    request.method = "PATCH"
                    request
                }
                .with(withUserId(1L))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.profileImage").value("/uploads/uuid.jpg"))
    }
}
