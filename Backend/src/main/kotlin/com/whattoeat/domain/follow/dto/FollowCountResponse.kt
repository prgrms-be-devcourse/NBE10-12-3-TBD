package com.whattoeat.domain.follow.dto

@JvmRecord
data class FollowCountResponse(
    val userId: Long,
    val followerCount: Long,
    val followingCount: Long
)
