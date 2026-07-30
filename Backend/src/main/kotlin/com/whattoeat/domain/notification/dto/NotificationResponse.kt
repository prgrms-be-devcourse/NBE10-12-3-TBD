package com.whattoeat.domain.notification.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long?,
    val actorId: Long?,
    val actorNickname: String?,
    val feedId: Long?,
    val type: NotificationType,
    val message: String,
    @get:JsonProperty("isRead") val isRead: Boolean,
    val createdAt: LocalDateTime?
) {
    constructor(notification: Notification) : this(
        notification.id,
        notification.actor.id,
        notification.actor.nickname,
        notification.feed?.id,
        notification.type,
        notification.message,
        notification.isRead,
        notification.createdAt
    )
}
