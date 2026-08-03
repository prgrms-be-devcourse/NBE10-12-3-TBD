package com.whattoeat.domain.notification.event

data class FeedLikedEvent(
    val feedId: Long,
    val actorId: Long
)
