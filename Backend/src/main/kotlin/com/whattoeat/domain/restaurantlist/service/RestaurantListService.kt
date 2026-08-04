package com.whattoeat.domain.restaurantlist.service

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.restaurantlist.repository.RestaurantListItemRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListRepository
import com.whattoeat.domain.restaurantlist.repository.SavedRestaurantListRepository
import com.whattoeat.domain.restaurantlist.dto.RestaurantListResponse
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.*
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
@Transactional
class RestaurantListService(
    private val restaurantListRepository: RestaurantListRepository,
    private val userRepository: UserRepository,
    private val restaurantListItemRepository: RestaurantListItemRepository,
    private val restaurantRepository: RestaurantRepository,
    private val entityManager: EntityManager,
    private val savedRestaurantListRepository: SavedRestaurantListRepository
) {
    fun create(userId: Long, title: String, description: String, moodTag: MoodTag?): RestaurantList {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        val restaurantList = RestaurantList(
            user,
            title,
            description,
            moodTag
        );

        return restaurantListRepository.save(restaurantList)
    }

    // 맛집 리스트 다건 조회
    @Transactional(readOnly = true)
    fun findAllByUserId(userId: Long, pageable: Pageable): Page<RestaurantList> {
        return restaurantListRepository.findByUserIdOrderByIdDesc(userId, pageable)
    }


    // 맛집 리스트 단건 조회
    @Transactional(readOnly = true)
    fun findByIdAndUserId(id: Long, userId: Long): RestaurantList {
        return restaurantListRepository.findByIdAndUserId(id, userId)
            .orElseThrow { ListNotFoundException(id) }
    }

    fun addItem(
        userId: Long,
        listId: Long,
        restaurantId: Long,
        memo: String?,
        orderIndex: Int?
    ): RestaurantListItem {
        val restaurantList = restaurantListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow { ListNotFoundException(listId) }

        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { RestaurantNotFoundException(restaurantId) }

        // 식당리스트아이템에 식당 중복으로 넣는지 체크 / 수정때는 메모, 순서만 바꾸기 때문에 삭제 후 다시 추가해야함
        if (restaurantListItemRepository.existsByRestaurantListIdAndRestaurantId(listId, restaurantId)) {
            throw DuplicateRestaurantListItemException(restaurantId)
        }


        // 인덱스 값 없을 경우 처리 N + 1
        val nextOrderIndex = if (orderIndex == null) {
            val maxOrderIndex =
                restaurantListItemRepository.findMaxOrderIndexByListId(listId) ?: 0

            maxOrderIndex + 1
        } else {
            restaurantListItemRepository.incOrderIndex(
                listId,
                orderIndex
            )

            orderIndex
        }

        val restaurantListItem = RestaurantListItem(
            restaurantList,
            restaurant,
            memo,
            nextOrderIndex
        )

        return restaurantListItemRepository.save(restaurantListItem);
    }

    fun updateItem(
        listId: Long,
        itemId: Long,
        userId: Long,
        orderIndex: Int?,
        memo: String?,
    ): RestaurantListItem {
        // 식당 있는지 확인
        restaurantListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow { ListNotFoundException(listId) }

        val item = restaurantListItemRepository.findListItem(itemId, listId, userId)
            .orElseThrow { RestaurantListItemNotFoundException(itemId) }

        item.updateListItem(orderIndex, memo)

        return item
    }

    // 식당 리스트 아이템 삭제
    fun deleteItem(listId: Long, itemId: Long, userId: Long) {
        val item = restaurantListItemRepository.findListItem(itemId, listId, userId)
            .orElseThrow { RestaurantListItemNotFoundException(itemId) }

        restaurantListItemRepository.delete(item)
    }

    // ============================== 전체 조회 =================================
    // 전체 맛집 리스트 다건 조회
    @Transactional(readOnly = true)
    fun findAll(moodTag: MoodTag?, pageable: Pageable): Page<RestaurantList> {
        return if (moodTag == null) {
            restaurantListRepository.findAll(pageable)
        } else {
            restaurantListRepository.findAllByMoodTag(moodTag, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun findPublicByUserId(userId: Long, pageable: Pageable): Page<RestaurantList> {
        return restaurantListRepository.findByUserId(userId, pageable)
    }

    // 전체 식당 리스트 단건 조회
    @Transactional(readOnly = true)
    fun findById(id: Long): RestaurantList {
        return restaurantListRepository.findById(id)
            .orElseThrow { ListNotFoundException(id) }
    }

    // ================= 리스트 저장 ====================

    // ================= 리스트 복사 ====================
    fun copyList(userId: Long, id: Long): RestaurantList {
        // 복사하는 사용자 조회
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        // 원본 리스트 조회 - 다른 사람 리스트도 복사할 수 있어야해서 userId 조건을 걸지는 않음
        val originalList = restaurantListRepository.findById(id)
            .orElseThrow { ListNotFoundException(id) }

        // 리스트 복사
        val copyList = restaurantListRepository.save(
            RestaurantList(
                user,
                originalList.title,
                originalList.description,
                originalList.moodTag
            )
        )

        // 원본 리스트 아이템 조회
        val originalListItems = restaurantListItemRepository.findItemsByListId(id)

        // 원본 아이템들을 새 리스트의 아이템으로 복사
        for (originalListItem in originalListItems) {
            val copyListItem = RestaurantListItem(
                copyList,
                originalListItem.restaurant,
                originalListItem.memo,
                originalListItem.orderIndex
            )

            restaurantListItemRepository.save(copyListItem)
        }
        restaurantListItemRepository.flush()

        val copyListId = copyList.id
            ?: throw IllegalStateException("복사된 맛집 리스트의 ID가 생성되지 않았습니다.")

        entityManager.clear()

        return restaurantListRepository.findByIdWithItems(copyListId)
            .orElseThrow { ListNotFoundException(copyListId) }
    }

    fun update(
        listId: Long,
        userId: Long,
        title: String,
        description: String,
        moodTag: MoodTag?,
    ): RestaurantList {
        val restaurantList = restaurantListRepository.findById(listId)
            .orElseThrow { ListNotFoundException(listId) }

        if (restaurantList.user.id != userId) {
            throw IllegalArgumentException("본인의 리스트만 수정할 수 있습니다.");
        }

        restaurantList.update(
            title,
            description,
            moodTag
        )

        return restaurantList
    }

    fun delete(listId: Long, userId: Long) {
        val restaurantList =
            restaurantListRepository.findByIdAndUserId(listId, userId).orElseThrow { ListNotFoundException(listId) }

        savedRestaurantListRepository.deleteAllByRestaurantListId(listId)
        restaurantListRepository.delete(restaurantList)
    }


    @Transactional(readOnly = true)
    fun findAllExceptUser(
        userId: Long,
        moodTag: MoodTag?,
        pageable: Pageable
    ): Page<RestaurantList> {
        return if (moodTag == null) {
            restaurantListRepository.findByUserIdNot(userId, pageable)
        } else {
            restaurantListRepository.findByUserIdNotAndMoodTag(userId, moodTag, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun findPopularLists(
        userId: Long,
        size: Int
    ): List<RestaurantListResponse.PopularRestaurantList> {
        val limit = size.coerceIn(1, 5)
        val pageable = PageRequest.of(0, limit)

        return savedRestaurantListRepository
            .findPopularLists(
                userId,
                pageable
            )
            .map { projection ->
                RestaurantListResponse.PopularRestaurantList(
                    id = projection.id,
                    userId = projection.userId,
                    nickname = projection.nickname,
                    title = projection.title,
                    description = projection.description,
                    moodTag = projection.moodTag,
                    itemCount = projection.itemCount,
                    createdAt = projection.createdAt,
                    saveCount = projection.saveCount
                )
            }
    }
}
