package com.whattoeat.domain.follow.dto

import com.whattoeat.domain.follow.entity.Follow
import java.time.LocalDateTime

@JvmRecord
data class FollowResponse(
    val followId: Long,
    val followerId: Long,
    val followingId: Long,
    val createdAt: LocalDateTime
) {

    companion object {
        @JvmStatic
        fun from(follow: Follow): FollowResponse {
            return FollowResponse(
                follow.id!!,
                follow.follower.id!!,
                follow.following.id!!,
                follow.createdAt!!
            )
        }
    }
}
