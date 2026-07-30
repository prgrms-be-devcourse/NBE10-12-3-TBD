package com.whattoeat.domain.restaurantlist.dto

import com.whattoeat.domain.restaurant.entity.MoodTag
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class RestaurantListRequest {
    data class RestaurantList(
        @field:NotBlank(message = "제목은 필수입니다.")
        val title: String,
        val description: String,
        val moodTag: MoodTag?
    )

    data class RestaurantListItem(
        @field:NotNull(message = "식당 ID는 필수입니다.")
        var restaurantId: Long,
        val memo: String?,
        @field:Min(value = 1, message = "순서는 1 이상이어야 합니다.")
        val orderIndex: Int?
    )
}
