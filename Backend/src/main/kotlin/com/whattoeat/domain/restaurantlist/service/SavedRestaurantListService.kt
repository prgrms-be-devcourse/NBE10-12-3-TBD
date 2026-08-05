package com.whattoeat.domain.restaurantlist.service

import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.event.RestaurantListSavedEvent
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.restaurantlist.dto.SavedRestaurantListResponse
import com.whattoeat.domain.restaurantlist.entity.SavedRestaurantList
import com.whattoeat.domain.restaurantlist.repository.RestaurantListRepository
import com.whattoeat.domain.restaurantlist.repository.SavedRestaurantListRepository
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.AlreadySavedRestaurantListException
import com.whattoeat.global.exception.ListNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SavedRestaurantListService(
    private val userRepository: UserRepository,
    private val restaurantListRepository: RestaurantListRepository,
    private val savedRestaurantListRepository: SavedRestaurantListRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val notificationRepository: NotificationRepository,
) {
    // 레스토랑 리스트 저장(연결)
    fun save(userId: Long, restaurantListId: Long) {
        // 저장하는 사용자 존재하는지 확인
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException(userId) }

        // 저장하려는 원본 레스토랑 리스트가 존재하는지 확인
        val restaurantList = restaurantListRepository.findById(restaurantListId).orElseThrow { ListNotFoundException(restaurantListId) }

        if(restaurantList.user.id == userId) {
            throw IllegalArgumentException("본인의 리스트는 저장할 수 없습니다.")
        }

        // 같은 사용자가 같은 리스트 이미 저장했는지 확인 (중복 방지)
        if(savedRestaurantListRepository.existsByUserIdAndRestaurantListId(userId, restaurantListId)) {
            throw AlreadySavedRestaurantListException()
        }

        // 사용자의 원본 리스트를 연결하는 저장 기록 생성
        val savedRestaurantList = SavedRestaurantList(user, restaurantList)

        // 저장 기록 DB에 저장
        savedRestaurantListRepository.save(savedRestaurantList)

        eventPublisher.publishEvent(RestaurantListSavedEvent(restaurantListId, userId))
    }


    // 레스토랑 리스트 저장 취소
    fun unsave(userId: Long, restaurantListId: Long) {
        // 현재 사용자가 해당 레스토랑 리스트를 저장한 기록이 있는지 조회
        val savedRestaurantList = savedRestaurantListRepository.findByUserIdAndRestaurantListId(userId, restaurantListId)
                .orElseThrow { ListNotFoundException(restaurantListId) }

        // 저장 기록만 삭제 (원본 레스토랑 리스트는 그대로 유지)
        savedRestaurantListRepository.delete(savedRestaurantList)

        // 저장 취소 시 해당 알림도 지워서, 나중에 다시 저장하면 알림이 다시 가도록 한다.
        notificationRepository.deleteByReceiverIdAndActorIdAndRestaurantListIdAndType(
            savedRestaurantList.restaurantList.user.id!!, userId, restaurantListId, NotificationType.LIST_SHARE
        )
    }

    // 내가 저장한 레스토랑 리스트 목록 조회
    @Transactional(readOnly = true)
    fun findMySavedLists(userId: Long, pageable: Pageable): Page<SavedRestaurantListResponse> {
        return savedRestaurantListRepository.findByUserId(userId, pageable)
                .map(SavedRestaurantListResponse::from)
    }

    // 특정 레스토랑 리스트를 현재 사용자가 저장했는지 확인 (프론트에서 상세 화면 "저장됨/저장안됨" 상태를 표시할때 사용)
    @Transactional(readOnly = true)
    fun isSaved(userId: Long, restaurantListId: Long): Boolean {
        return savedRestaurantListRepository.existsByUserIdAndRestaurantListId(userId, restaurantListId)
    }

}
