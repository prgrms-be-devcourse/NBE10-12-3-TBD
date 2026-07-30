package com.whattoeat.domain.restaurantlist.dto

data class SavedRestaurantListStatusResponse(
    val restaurantListId: Long?,
    val saved: Boolean,
) {
    companion object {
        @JvmStatic
        fun of(restaurantListId: Long?, saved: Boolean) : SavedRestaurantListStatusResponse {
            return SavedRestaurantListStatusResponse(restaurantListId, saved);
        }
    }
}
