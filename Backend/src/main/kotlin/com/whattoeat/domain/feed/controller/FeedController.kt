package com.whattoeat.domain.feed.controller

import com.whattoeat.domain.feed.dto.request.FeedCreateRequest
import com.whattoeat.domain.feed.dto.request.FeedUpdateRequest
import com.whattoeat.domain.feed.dto.response.FeedDetailResponse
import com.whattoeat.domain.feed.dto.response.FeedListPageResponse
import com.whattoeat.domain.feed.service.FeedService
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import jakarta.validation.Valid
import java.io.IOException
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/feeds")
class FeedController(
    private val feedService: FeedService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Throws(IOException::class)
    fun createFeed(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestPart("feed") @Valid feedCreateRequest: FeedCreateRequest,
        @RequestPart(value = "image", required = false) image: MultipartFile?,
    ): RsData<FeedDetailResponse> {
        val response = feedService.createFeed(userDetails.user, feedCreateRequest, image)
        return RsData.success(response, "피드가 생성되었습니다.")
    }

    @GetMapping
    fun getFeeds(
        @AuthenticationPrincipal userDetails: CustomUserDetails?,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) restaurantId: Long?,
        pageable: Pageable,
    ): RsData<FeedListPageResponse> {
        val currentUserId = userDetails?.userId
        val page = feedService.getFeeds(currentUserId, userId, restaurantId, pageable)
        return RsData.success(FeedListPageResponse.from(page), "피드 목록 조회가 완료되었습니다.")
    }

    @GetMapping("/following")
    fun getFollowingFeeds(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        pageable: Pageable,
    ): RsData<FeedListPageResponse> {
        val page = feedService.getFollowingFeeds(userDetails.userId, pageable)
        return RsData.success(FeedListPageResponse.from(page), "팔로잉 피드 조회가 완료되었습니다.")
    }

    @GetMapping("/recommend")
    fun getRecommendedFeeds(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        pageable: Pageable,
    ): RsData<FeedListPageResponse> {
        val page = feedService.getRandomRecommendedFeeds(userDetails.userId, pageable)
        return RsData.success(FeedListPageResponse.from(page), "추천 피드 조회가 완료되었습니다.")
    }

    @PutMapping(value = ["/{id}"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Throws(IOException::class)
    fun updateFeed(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable id: Long,
        @RequestParam("content") content: String,
        @RequestParam(value = "restaurantId", required = false) restaurantId: Long?,
        @RequestParam(value = "deleteImage", required = false, defaultValue = "false") deleteImage: Boolean,
        @RequestParam(value = "moodTag", required = false) moodTag: MoodTag?,
        @RequestPart(value = "image", required = false) image: MultipartFile?,
    ): RsData<FeedDetailResponse> {
        val feedUpdateRequest = FeedUpdateRequest(content, restaurantId, deleteImage, moodTag)
        val response = feedService.updateFeed(id, userDetails.userId, feedUpdateRequest, image)
        return RsData.success(response, "피드가 수정되었습니다.")
    }

    @DeleteMapping("/{id}")
    fun deleteFeed(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable id: Long,
    ): RsData<Void> {
        feedService.deleteFeed(id, userDetails.userId)
        return RsData.success<Void>(null, "피드가 삭제되었습니다.")
    }
}
