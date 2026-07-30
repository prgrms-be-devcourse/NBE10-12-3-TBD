package com.whattoeat.domain.restaurantlist.dto

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurantlist.entity.SavedRestaurantList

import java.time.LocalDateTime

data class SavedRestaurantListResponse(
    val listId: Long?,
    val userId: Long?,
    val nickname: String,
    val title: String,
    val description: String,
    val moodTag: MoodTag?,
    val items: List<RestaurantListResponse.RestaurantListItemDetail>,
    val savedAt: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun from(
            savedRestaurantList: SavedRestaurantList
        ): SavedRestaurantListResponse {
            val restaurantList = savedRestaurantList.restaurantList

            return SavedRestaurantListResponse(
                restaurantList.id,
                restaurantList.user.id,
                restaurantList.user.nickname,
                restaurantList.title,
                restaurantList.description,
                restaurantList.moodTag,
                restaurantList.items.map { item -> RestaurantListResponse.RestaurantListItemDetail(item) },
                savedRestaurantList.createdAt
            );
        }
    }
}
