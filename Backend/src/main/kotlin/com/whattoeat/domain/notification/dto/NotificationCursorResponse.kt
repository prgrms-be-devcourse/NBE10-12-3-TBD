package com.whattoeat.domain.notification.dto

data class NotificationCursorResponse(
    val content: List<NotificationResponse>,
    val nextCursor: Long?,
    val hasNext: Boolean
)
