package com.whattoeat.domain.notification.messaging.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.FeedLikedEvent
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class FeedLikeNotificationHandler(
    private val notificationRepository: NotificationRepository,
    private val feedRepository: FeedRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) : NotificationHandler {

    override val type = NotificationType.FEED_LIKE

    @Transactional
    override fun handle(payloadJson: String) {
        val event = objectMapper.readValue(payloadJson, FeedLikedEvent::class.java)

        val feed = feedRepository.findById(event.feedId).orElse(null) ?: return
        val actor = userRepository.findById(event.actorId).orElse(null) ?: return
        val receiver = feed.user

        if (receiver.id == actor.id) return

        val receiverId = receiver.id ?: return
        val actorId = actor.id ?: return
        val feedId = feed.id ?: return

        if (notificationRepository.existsByReceiverIdAndActorIdAndFeedIdAndType(
                receiverId, actorId, feedId, NotificationType.FEED_LIKE
            )
        ) return

        val message = "${actor.nickname}님이 회원님의 피드를 좋아합니다."
        notificationRepository.save(
            Notification.of(receiver, actor, feed, null, NotificationType.FEED_LIKE, message)
        )
    }
}
