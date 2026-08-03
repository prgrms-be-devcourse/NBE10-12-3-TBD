package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurant.entity.MoodTag
import java.time.LocalDateTime

interface PopularRestaurantListProjection {
    val id: Long
    val userId: Long
    val nickname: String
    val title: String
    val description: String
    val moodTag: MoodTag?
    val itemCount: Long
    val createdAt: LocalDateTime?
    val saveCount: Long
}
