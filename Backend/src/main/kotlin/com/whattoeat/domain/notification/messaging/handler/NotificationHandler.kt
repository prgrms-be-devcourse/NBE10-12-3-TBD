package com.whattoeat.domain.notification.messaging.handler

import com.whattoeat.domain.notification.entity.NotificationType
import org.springframework.stereotype.Component

interface NotificationHandler {
    val type: NotificationType
    fun handle(payloadJson: String)
}

@Component
class NotificationHandlerRegistry(handlers: List<NotificationHandler>) {
    private val map: Map<NotificationType, NotificationHandler> = handlers.associateBy { it.type }

    fun forType(type: NotificationType): NotificationHandler =
        map[type] ?: error("No handler registered for $type")
}
