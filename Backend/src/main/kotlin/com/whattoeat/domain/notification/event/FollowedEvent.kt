package com.whattoeat.domain.notification.event

data class FollowedEvent(
    val followerId: Long,
    val followingId: Long
)
