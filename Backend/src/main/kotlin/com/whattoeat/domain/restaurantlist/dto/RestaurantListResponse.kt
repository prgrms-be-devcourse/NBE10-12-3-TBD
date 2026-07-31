package com.whattoeat.domain.restaurantlist.dto

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem

import java.time.LocalDateTime

class RestaurantListResponse {
    // 목록 조회 전체 응답
    data class RestaurantListsResponse(
        val lists: List<RestaurantLists>,
        val totalPages: Int,
        val totalElements: Long,
    )

    // 다건 조회용
    data class RestaurantLists(
        val id: Long?,
        val userId: Long?,
        val nickname: String,
        val title: String,
        val description: String,
        val moodTag: MoodTag?,
        val itemCount: Int,
        val createdAt: LocalDateTime?,
    ) {
        constructor(restaurantList: RestaurantList) : this(
            restaurantList.id,
            restaurantList.user.id,
            restaurantList.user.nickname,
            restaurantList.title,
            restaurantList.description,
            restaurantList.moodTag,
            restaurantList.items.size,
            restaurantList.createdAt
        )
    }

    // 단건조회용
    data class RestaurantListDetail(
        val listId: Long?,
        val userId: Long?,
        val nickname: String,
        val title: String,
        val description: String,
        val moodTag: MoodTag?,
        val items: List<RestaurantListItemDetail>,
        val createdAt: LocalDateTime?,
    ) {
        constructor(restaurantList: RestaurantList) : this(
            restaurantList.id,
            restaurantList.user.id,
            restaurantList.user.nickname,
            restaurantList.title,
            restaurantList.description,
            restaurantList.moodTag,
            restaurantList.items.map { item -> RestaurantListItemDetail(item) },
            restaurantList.createdAt
        )
    }

    // 단건 상세 안의 식당 아이템
    data class RestaurantListItemDetail(
        val id: Long?,
        val listId: Long?,
        val restaurantId: Long?,
        val restaurantName: String,
        val category: Category,

        val address: String,
        val roadAddress: String?,
        val lat: Double,
        val lng: Double,

        val orderIndex: Int,
        val memo: String?,
        val createdAt: LocalDateTime?,
    ) {
        constructor(item: RestaurantListItem): this(
            item.id,
            item.restaurantList.id,
            item.restaurant.id,
            item.restaurant.name,
            item.restaurant.category,

            item.restaurant.address,
            item.restaurant.roadAddress,
            item.restaurant.lat,
            item.restaurant.lng,

            item.orderIndex,
            item.memo,
            item.createdAt
        )
    }

    data class PopularRestaurantListsResponse(
        val lists: List<PopularRestaurantList>,
    )

    data class PopularRestaurantList(
        val id: Long,
        val userId: Long,
        val nickname: String,
        val title: String,
        val description: String,
        val moodTag: MoodTag?,
        val itemCount: Long,
        val createdAt: LocalDateTime?,
        val saveCount: Long,
    )
}
