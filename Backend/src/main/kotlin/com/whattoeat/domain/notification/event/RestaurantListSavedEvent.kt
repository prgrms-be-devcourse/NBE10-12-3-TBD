package com.whattoeat.domain.notification.event

data class RestaurantListSavedEvent(
    val restaurantListId: Long,
    val actorId: Long
)
