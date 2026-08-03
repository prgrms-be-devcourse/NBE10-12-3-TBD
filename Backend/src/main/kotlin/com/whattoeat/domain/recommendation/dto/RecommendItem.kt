package com.whattoeat.domain.recommendation.dto

import com.whattoeat.domain.restaurant.entity.Category

data class RecommendItem(
    val kakaoPlaceId: String,
    val category: Category,
    val categoryLabel: String,
    val distanceMeter: Int?,
    val name: String,
    val categoryName: String?,
)
