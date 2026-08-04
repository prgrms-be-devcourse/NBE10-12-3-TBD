package com.whattoeat.domain.recommendation.controller

import com.whattoeat.domain.recommendation.dto.RecommendRequest
import com.whattoeat.domain.recommendation.dto.RecommendResponse
import com.whattoeat.domain.recommendation.service.RecommendService
import com.whattoeat.global.rsData.RsData
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RecommendationController(
    private val recommendService: RecommendService
) {
    @PostMapping("/api/v1/restaurants/recommend")
    fun recommend(@Valid @RequestBody request: RecommendRequest): RsData<RecommendResponse> {
        val recommendations = recommendService.recommend(request)
        return RsData.success(
            RecommendResponse(recommendations),
            "식당 추천이 완료되었습니다."
        )
    }
}
