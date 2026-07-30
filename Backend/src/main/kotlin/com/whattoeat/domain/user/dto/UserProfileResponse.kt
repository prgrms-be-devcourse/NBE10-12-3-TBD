package com.whattoeat.domain.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.User
import java.time.LocalDateTime

data class UserProfileResponse(
    val id: Long?,
    val nickname: String?,
    val profileImage: String?,
    val email: String?,
    val provider: Provider?,
    val createdAt: LocalDateTime?,
    @get:JsonProperty("isOwnProfile") val isOwnProfile: Boolean,
    @get:JsonProperty("isFollowing") val isFollowing: Boolean
) {
    companion object {
        @JvmStatic
        fun from(user: User, currentUserId: Long?, isFollowing: Boolean): UserProfileResponse {
            val isOwn = user.id == currentUserId
            return UserProfileResponse(
                user.id,
                user.nickname,
                user.profileImage,
                user.email,
                user.provider,
                user.createdAt,
                isOwn,
                if (isOwn) false else isFollowing
            )
        }
    }
}
