package com.whattoeat.domain.notification.messaging.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.FollowedEvent
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class FollowNotificationHandler(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) : NotificationHandler {

    override val type = NotificationType.FOLLOW

    @Transactional
    override fun handle(payloadJson: String) {
        val event = objectMapper.readValue(payloadJson, FollowedEvent::class.java)

        if (event.followerId == event.followingId) return

        val actor = userRepository.findById(event.followerId).orElse(null) ?: return
        val receiver = userRepository.findById(event.followingId).orElse(null) ?: return

        val receiverId = receiver.id ?: return
        val actorId = actor.id ?: return

        if (notificationRepository.existsByReceiverIdAndActorIdAndTypeAndFeedIsNullAndRestaurantListIsNull(
                receiverId, actorId, NotificationType.FOLLOW
            )
        ) return

        val message = "${actor.nickname}님이 회원님을 팔로우했습니다."
        notificationRepository.save(
            Notification.of(receiver, actor, null, null, NotificationType.FOLLOW, message)
        )
    }
}
