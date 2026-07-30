package com.whattoeat.domain.user.service

import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.user.dto.UpdateProfileRequest
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.DuplicateEmailException
import com.whattoeat.global.exception.PasswordMismatchException
import com.whattoeat.global.exception.UserNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.BDDMockito.given
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var followRepository: FollowRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks
    private lateinit var userService: UserService

    private fun createUser(
        id: Long,
        nickname: String,
        profileImage: String?,
        password: String? = null,
        provider: Provider = Provider.LOCAL
    ): User {
        val user = User.builder()
            .nickname(nickname)
            .email("test@example.com")
            .provider(provider)
            .role(Role.USER)
            .profileImage(profileImage)
            .password(password)
            .build()
        ReflectionTestUtils.setField(user, "id", id)
        return user
    }

    private fun updateRequest(nickname: String?, profileImage: String?) =
        UpdateProfileRequest(nickname, null, null, null)

    private fun updateEmailRequest(email: String?) =
        UpdateProfileRequest(null, email, null, null)

    private fun updatePasswordRequest(currentPassword: String?, newPassword: String?) =
        UpdateProfileRequest(null, null, currentPassword, newPassword)

    // ===================== getUser 테스트 =====================

    @Test
    fun getUser_본인_프로필_조회시_isOwnProfile이_true() {
        val user = createUser(1L, "testNickname", "profile.jpg")
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(
            followRepository.existsByFollower_IdAndFollowing_Id(1L, 1L)
        ).willReturn(false)

        val response = userService.getUser(1L, 1L)

        assertThat(response.isOwnProfile).isTrue
        assertThat(response.isFollowing).isFalse
        assertThat(response.nickname).isEqualTo("testNickname")
    }

    @Test
    fun getUser_타인_프로필_조회시_팔로우_중이면_isFollowing이_true() {
        val user = createUser(2L, "otherNickname", "other.jpg")
        given(userRepository.findById(2L)).willReturn(Optional.of(user))
        given(
            followRepository.existsByFollower_IdAndFollowing_Id(1L, 2L)
        ).willReturn(true)

        val response = userService.getUser(2L, 1L)

        assertThat(response.isOwnProfile).isFalse
        assertThat(response.isFollowing).isTrue
    }

    @Test
    fun getUser_타인_프로필_조회시_팔로우_안하면_isFollowing이_false() {
        val user = createUser(2L, "otherNickname", "other.jpg")
        given(userRepository.findById(2L)).willReturn(Optional.of(user))
        given(
            followRepository.existsByFollower_IdAndFollowing_Id(1L, 2L)
        ).willReturn(false)

        val response = userService.getUser(2L, 1L)

        assertThat(response.isOwnProfile).isFalse
        assertThat(response.isFollowing).isFalse
    }

    @Test
    fun getUser_존재하지_않는_유저이면_예외발생() {
        given(userRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { userService.getUser(999L, 1L) }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessageContaining("999")
    }

    // ===================== updateProfile 테스트 =====================

    @Test
    fun updateProfile_닉네임을_변경한다() {
        val user = createUser(1L, "oldNickname", "old.jpg", "encodedPassword", Provider.LOCAL)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))

        val response = userService.updateProfile(1L, 1L, updateRequest("newNickname", null))

        assertThat(response.nickname).isEqualTo("newNickname")
    }

    @Test
    fun updateProfile_이미지가_null이면_기존_이미지를_유지한다() {
        val user = createUser(1L, "nickname", "existing.jpg", "encodedPassword", Provider.LOCAL)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))

        val response = userService.updateProfile(1L, 1L, updateRequest("newNickname", null))

        assertThat(response.nickname).isEqualTo("newNickname")
        assertThat(response.profileImage).isEqualTo("existing.jpg")
    }

    @Test
    fun updateProfile_이메일을_변경한다() {
        val user = createUser(1L, "oldNickname", "old.jpg", "encodedPassword", Provider.LOCAL)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(userRepository.existsByEmail("new@example.com")).willReturn(false)

        val response = userService.updateProfile(1L, 1L, updateEmailRequest("new@example.com"))
        assertThat(response.email).isEqualTo("new@example.com")
    }

    @Test
    fun updateProfile_중복된_이메일이면_예외발생() {
        val user = createUser(1L, "nickname", "image.jpg", "encodedPassword", Provider.LOCAL)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(userRepository.existsByEmail("dup@example.com")).willReturn(true)

        assertThatThrownBy {
            userService.updateProfile(1L, 1L, updateEmailRequest("dup@example.com"))
        }
            .isInstanceOf(DuplicateEmailException::class.java)
            .hasMessageContaining("이미 사용 중인 이메일입니다.")
    }

    @Test
    fun updateProfile_비밀번호를_변경한다() {
        val user = createUser(1L, "nickname", "image.jpg", "encodedOldPassword", Provider.LOCAL)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(passwordEncoder.matches("oldPassword123", "encodedOldPassword")).willReturn(true)
        given(passwordEncoder.encode("newPassword123")).willReturn("encodedNewPassword")

        userService.updateProfile(1L, 1L, updatePasswordRequest("oldPassword123", "newPassword123"))

        assertThat(user.password).isEqualTo("encodedNewPassword")
    }

    @Test
    fun updateProfile_현재_비밀번호가_틀리면_예외발생() {
        val user = createUser(1L, "nickname", "image.jpg", "encodedPassword", Provider.LOCAL)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false)

        assertThatThrownBy {
            userService.updateProfile(1L, 1L, updatePasswordRequest("wrongPassword", "newPassword123"))
        }
            .isInstanceOf(PasswordMismatchException::class.java)
            .hasMessageContaining("현재 비밀번호가 일치하지 않습니다.")
    }

    @Test
    fun updateProfile_카카오_사용자는_비밀번호_변경_불가() {
        val user = createUser(1L, "nickname", "image.jpg", null, Provider.KAKAO)
        given(userRepository.findById(1L)).willReturn(Optional.of(user))

        assertThatThrownBy {
            userService.updateProfile(1L, 1L, updatePasswordRequest("any", "newPassword123"))
        }
            .isInstanceOf(PasswordMismatchException::class.java)
            .hasMessageContaining("카카오 계정은 비밀번호를 변경할 수 없습니다.")
    }

    @Test
    fun updateProfile_타인_프로필_수정시_AccessDeniedException_발생() {
        assertThatThrownBy {
            userService.updateProfile(2L, 1L, updateRequest("nickname", null))
        }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun updateProfile_존재하지_않는_유저이면_예외발생() {
        given(userRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy {
            userService.updateProfile(999L, 999L, updateRequest("nickname", null))
        }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessageContaining("999")
    }
}
