package com.whattoeat.domain.feed.event

@JvmRecord
data class FeedCreatedEvent(
    val feedId: Long?,
    val authorId: Long?,
)
