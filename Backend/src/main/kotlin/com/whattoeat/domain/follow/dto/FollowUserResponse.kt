package com.whattoeat.domain.follow.dto

import com.whattoeat.domain.user.entity.User
import java.time.LocalDateTime

@JvmRecord
data class FollowUserResponse(
    val userId: Long,
    val nickname: String,
    val profileImage: String?,
    val isFollowedByMe: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        @JvmStatic
        fun from(user: User,
                 isFollowedByMe: Boolean
        ): FollowUserResponse {
            return FollowUserResponse(
                user.id!!,
                user.nickname,
                user.profileImage,
                isFollowedByMe,
                user.createdAt!!
            )
        }
    }
}
