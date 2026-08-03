package com.whattoeat.domain.notification.messaging

import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.CommentCreatedEvent
import com.whattoeat.domain.notification.event.FeedLikedEvent
import com.whattoeat.domain.notification.event.FollowedEvent
import com.whattoeat.domain.notification.event.RestaurantListSavedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NotificationEventListener(
    private val publisher: NotificationStreamPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: FeedLikedEvent) = safePublish(NotificationType.FEED_LIKE, event)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: CommentCreatedEvent) = safePublish(NotificationType.FEED_COMMENT, event)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: FollowedEvent) = safePublish(NotificationType.FOLLOW, event)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: RestaurantListSavedEvent) = safePublish(NotificationType.LIST_SHARE, event)

    private fun safePublish(type: NotificationType, payload: Any) {
        try {
            publisher.publish(type, payload)
        } catch (ex: Exception) {
            log.warn("알림 스트림 발행 실패 type={} payload={}: {}", type, payload, ex.message)
        }
    }
}
