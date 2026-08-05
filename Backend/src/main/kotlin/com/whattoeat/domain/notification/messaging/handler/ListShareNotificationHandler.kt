package com.whattoeat.domain.notification.messaging.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.RestaurantListSavedEvent
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListRepository
import com.whattoeat.domain.restaurantlist.repository.SavedRestaurantListRepository
import com.whattoeat.domain.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ListShareNotificationHandler(
    private val notificationRepository: NotificationRepository,
    private val restaurantListRepository: RestaurantListRepository,
    private val userRepository: UserRepository,
    private val savedRestaurantListRepository: SavedRestaurantListRepository,
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

        // 리스트 저장 알림도 커밋 후 비동기로 처리되므로, 이 핸들러가 실행되기 전에 이미
        // 저장을 취소했을 수 있다. 그 경우 지금은 존재하지 않는 저장 기록에 대한 알림을
        // 뒤늦게 만들지 않도록, 저장 직전에 저장 기록이 아직 남아 있는지 다시 확인한다.
        if (!savedRestaurantListRepository.existsByUserIdAndRestaurantListId(actorId, listId)) return

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
