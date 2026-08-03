package com.whattoeat.domain.notification.messaging.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.CommentCreatedEvent
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CommentNotificationHandler(
    private val notificationRepository: NotificationRepository,
    private val feedRepository: FeedRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) : NotificationHandler {

    override val type = NotificationType.FEED_COMMENT

    @Transactional
    override fun handle(payloadJson: String) {
        val event = objectMapper.readValue(payloadJson, CommentCreatedEvent::class.java)

        val feed = feedRepository.findById(event.feedId).orElse(null) ?: return
        val actor = userRepository.findById(event.actorId).orElse(null) ?: return
        val receiver = feed.user

        if (receiver.id == actor.id) return

        val message = "${actor.nickname}님이 회원님의 피드에 댓글을 남겼습니다."
        notificationRepository.save(
            Notification.of(receiver, actor, feed, null, NotificationType.FEED_COMMENT, message)
        )
    }
}
