package com.whattoeat.domain.recommendation.dto

import com.whattoeat.domain.restaurant.dto.RestaurantRequest
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class RecommendRequest(
    @field:NotEmpty(message = "추천 후보 목록은 비어있을 수 없습니다.")
    val candidates: List<@Valid RestaurantRequest.FromKakao>,
    val category: Category? = null,
    val sort: RecommendSort = RecommendSort.RANDOM,
    val lat: Double? = null,
    val lng: Double? = null,
    val exclude: List<String> = emptyList(),
    val mood: MoodTag? = null,
)
