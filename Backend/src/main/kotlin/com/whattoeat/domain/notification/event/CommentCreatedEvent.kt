package com.whattoeat.domain.notification.event

data class CommentCreatedEvent(
    val feedId: Long,
    val commentId: Long,
    val actorId: Long
)
