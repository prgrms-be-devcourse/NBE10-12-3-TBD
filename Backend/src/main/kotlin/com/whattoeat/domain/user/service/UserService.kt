package com.whattoeat.domain.user.service

import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.user.dto.UpdateProfileRequest
import com.whattoeat.domain.user.dto.UserProfileResponse
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.DuplicateEmailException
import com.whattoeat.global.exception.DuplicateNicknameException
import com.whattoeat.global.exception.PasswordMismatchException
import com.whattoeat.global.exception.UserNotFoundException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val followRepository: FollowRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun getUser(targetId: Long, currentUserId: Long): UserProfileResponse {
        val user = userRepository.findById(targetId)
            .orElseThrow { UserNotFoundException(targetId) }

        val isFollowing = followRepository
            .existsByFollower_IdAndFollowing_Id(currentUserId, targetId)
        return UserProfileResponse.from(user, currentUserId, isFollowing)
    }

    @Transactional
    fun updateProfile(id: Long, currentUserId: Long, request: UpdateProfileRequest): UserProfileResponse {
        if (id != currentUserId) {
            throw AccessDeniedException("본인의 프로필만 수정 가능합니다.")
        }

        val user = userRepository.findById(id).orElseThrow { UserNotFoundException(id) }

        val newNickname = request.nickname
        if (newNickname != null && newNickname != user.nickname) {
            if (userRepository.existsByNickname(newNickname)) {
                throw DuplicateNicknameException("이미 사용 중인 닉네임입니다.")
            }
        }

        user.updateProfile(request.nickname, user.profileImage)

        val newEmail = request.email
        if (newEmail != null && newEmail != user.email) {
            if (userRepository.existsByEmail(newEmail)) {
                throw DuplicateEmailException("이미 사용 중인 이메일입니다.")
            }
            user.updateEmail(newEmail)

            if (user.provider == Provider.LOCAL) {
                user.updateLoginId(newEmail)
            }
        }

        val newPassword = request.newPassword
        if (newPassword != null && newPassword.isNotBlank()) {
            if (user.provider == Provider.KAKAO) {
                throw PasswordMismatchException("카카오 계정은 비밀번호를 변경할 수 없습니다.")
            }
            val currentPassword = request.currentPassword
            if (currentPassword == null ||
                !passwordEncoder.matches(currentPassword, user.password)
            ) {
                throw PasswordMismatchException("현재 비밀번호가 일치하지 않습니다.")
            }
            // PasswordEncoder.encode()는 @Nullable로 선언되어 있지만
            // non-null 입력에는 non-null 결과를 보장한다(구현체 계약).
            user.updatePassword(passwordEncoder.encode(newPassword)!!)
        }

        return UserProfileResponse.from(user, currentUserId, false)
    }

    @Transactional
    fun kakaoLoginOrSignUp(kakaoId: String, nickname: String?, profileImg: String?, email: String?): User =
        userRepository.findByKakaoId(kakaoId)
            .orElseGet {
                userRepository.save(
                    User.builder()
                        .kakaoId(kakaoId)
                        .nickname(nickname)
                        .profileImage(profileImg)
                        .email(email)
                        .provider(Provider.KAKAO)
                        .role(Role.USER)
                        .build()
                )
            }

    fun findById(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

    @Transactional
    fun updateProfileImage(userId: Long, imageUrl: String): UserProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        user.updateProfile(user.nickname, imageUrl)

        return UserProfileResponse.from(user, userId, false)
    }
}
