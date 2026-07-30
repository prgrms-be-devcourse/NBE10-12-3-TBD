package com.whattoeat.domain.follow.controller

import com.whattoeat.domain.follow.dto.FollowCountResponse
import com.whattoeat.domain.follow.dto.FollowResponse
import com.whattoeat.domain.follow.dto.FollowUserResponse
import com.whattoeat.domain.follow.entity.Follow
import com.whattoeat.domain.follow.service.FollowService
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/follows")

class FollowController (
    private val followService: FollowService
){
    @PostMapping("/{followingId}")
    fun follow(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable followingId: Long
    ): ResponseEntity<RsData<FollowResponse>> {
        val follow :Follow  = followService.follow(userDetails.userId, followingId)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                RsData.success(
                    FollowResponse.from(follow),
                    "팔로우했습니다."))
    }

    @DeleteMapping("/{followingId}")
    fun unfollow(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable followingId: Long
    ): ResponseEntity<RsData<Void>> {
        followService.unfollow(userDetails.userId, followingId)
        return ResponseEntity.ok(
            RsData.success<Void>(null, "언팔로우했습니다.")
        )
    }

    @GetMapping("/followings")
    fun getFollowings(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        pageable: Pageable
    ): ResponseEntity<RsData<Page<FollowUserResponse>>> {
        val currentUserId: Long = userDetails.userId
        val response: Page<FollowUserResponse> = followService.getFollowings(currentUserId, pageable)
            .map { follow ->
                toUserResponse(
                    follow.following,
                    currentUserId
                )
            }
        return ResponseEntity.ok(RsData.success(response))
    }

    @GetMapping("/followers")
    fun getFollowers(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        pageable: Pageable
    ): ResponseEntity<RsData<Page<FollowUserResponse>>> {
        val currentUserId: Long = userDetails.userId
        val response:Page<FollowUserResponse> = followService.getFollowers(currentUserId, pageable)
            .map { follow ->
                toUserResponse(
                    follow.follower,
                    currentUserId
                )
            }
        return ResponseEntity.ok(RsData.success(response))
    }

    @GetMapping("/users/{userId}/count")
    fun getFollowerCounts(
        @PathVariable userId: Long
    ): ResponseEntity<RsData<FollowCountResponse>> {
        val followerCount : Long = followService.getFollowerCount(userId)
        val followingCount : Long = followService.getFollowingCount(userId)
        return ResponseEntity.ok(
            RsData.success(
                FollowCountResponse(
                    userId,
                    followerCount,
                    followingCount
                )
            )
        )
    }

    @GetMapping("/users/{userId}/followings")
    fun getUserFollowings(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable userId: Long,
        pageable: Pageable
    ): ResponseEntity<RsData<Page<FollowUserResponse>>> {
        val currentUserId : Long = userDetails.userId
        val response: Page<FollowUserResponse> = followService.getFollowings(userId, pageable)
            .map { follow ->
                toUserResponse(
                    follow.following,
                    currentUserId
                )
            }
        return ResponseEntity.ok(RsData.success(response))
    }

    @GetMapping("/users/{userId}/followers")
    fun getUserFollowers(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable userId: Long,
        pageable: Pageable
    ): ResponseEntity<RsData<Page<FollowUserResponse>>> {
        val currentUserId: Long = userDetails.userId
        val response: Page<FollowUserResponse> = followService.getFollowers(userId, pageable)
            .map { follow ->
                toUserResponse(
                    follow.follower,
                    currentUserId
                )
            }
        return ResponseEntity.ok(RsData.success(response))
    }


    private fun toUserResponse(user: User, currentUserId: Long): FollowUserResponse {
        return FollowUserResponse.from(
            user,
            followService.isFollowing(currentUserId, user.id!!)
        )
    }
}
