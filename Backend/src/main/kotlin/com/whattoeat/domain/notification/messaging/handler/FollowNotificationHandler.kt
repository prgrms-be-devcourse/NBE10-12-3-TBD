package com.whattoeat.domain.notification.messaging.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.follow.repository.FollowRepository
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
    private val followRepository: FollowRepository,
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

        // 팔로우 알림도 커밋 후 비동기로 처리되므로, 이 핸들러가 실행되기 전에 이미
        // 언팔로우했을 수 있다. 그 경우 지금은 존재하지 않는 팔로우에 대한 알림을 뒤늦게
        // 만들지 않도록, 저장 직전에 팔로우 관계가 아직 남아 있는지 다시 확인한다.
        if (!followRepository.existsByFollower_IdAndFollowing_Id(actorId, receiverId)) return

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
