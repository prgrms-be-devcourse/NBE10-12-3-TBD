package com.whattoeat.domain.notification.messaging.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.RestaurantListSavedEvent
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListRepository
import com.whattoeat.domain.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ListShareNotificationHandler(
    private val notificationRepository: NotificationRepository,
    private val restaurantListRepository: RestaurantListRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) : NotificationHandler {

    override val type = NotificationType.LIST_SHARE

    @Transactional
    override fun handle(payloadJson: String) {
        val event = objectMapper.readValue(payloadJson, RestaurantListSavedEvent::class.java)

        val list = restaurantListRepository.findById(event.restaurantListId).orElse(null) ?: return
        val actor = userRepository.findById(event.actorId).orElse(null) ?: return
        val receiver = list.user

        if (receiver.id == actor.id) return

        val receiverId = receiver.id ?: return
        val actorId = actor.id ?: return
        val listId = list.id ?: return

        if (notificationRepository.existsByReceiverIdAndActorIdAndRestaurantListIdAndType(
                receiverId, actorId, listId, NotificationType.LIST_SHARE
            )
        ) return

        val message = "${actor.nickname}님이 회원님의 리스트를 저장했습니다."
        notificationRepository.save(
            Notification.of(receiver, actor, null, list, NotificationType.LIST_SHARE, message)
        )
    }
}
