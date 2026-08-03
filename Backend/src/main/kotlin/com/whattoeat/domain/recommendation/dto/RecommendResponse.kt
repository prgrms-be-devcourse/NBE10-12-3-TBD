package com.whattoeat.domain.recommendation.dto

import com.whattoeat.domain.restaurant.entity.Category

data class RecommendResponse(
    val recommendations: List<RecommendItem>
)


