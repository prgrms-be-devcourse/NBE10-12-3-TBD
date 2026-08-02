package com.whattoeat.domain.feedlike.controller

import com.whattoeat.domain.feedlike.dto.FeedLikeResponse
import com.whattoeat.domain.feedlike.service.FeedLikeService
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/feeds/{feedId}/like")
class FeedLikeController(
    private val feedLikeService: FeedLikeService,
) {
    @PostMapping
    fun like(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable feedId: Long,
    ): ResponseEntity<RsData<FeedLikeResponse>> {
        val response = feedLikeService.like(userDetails.userId, feedId)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(RsData.success(response, "좋아요를 눌렀습니다."))
    }

    @DeleteMapping
    fun unlike(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable feedId: Long,
    ): ResponseEntity<RsData<FeedLikeResponse>> {
        val response = feedLikeService.unlike(userDetails.userId, feedId)
        return ResponseEntity.ok(RsData.success(response, "좋아요를 취소했습니다."))
    }

    @GetMapping
    fun isLiked(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable feedId: Long,
    ): ResponseEntity<RsData<FeedLikeResponse>> {
        val response = feedLikeService.getLikeStatus(userDetails.userId, feedId)

        return ResponseEntity.ok(RsData.success(response))
    }
}
