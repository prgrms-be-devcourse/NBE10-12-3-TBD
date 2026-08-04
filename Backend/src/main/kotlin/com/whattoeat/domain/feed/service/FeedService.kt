package com.whattoeat.domain.feed.service

import com.whattoeat.domain.comment.repository.CommentRepository
import com.whattoeat.domain.feed.dto.request.FeedCreateRequest
import com.whattoeat.domain.feed.dto.request.FeedUpdateRequest
import com.whattoeat.domain.feed.dto.response.FeedDetailResponse
import com.whattoeat.domain.feed.dto.response.FeedListResponse
import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feed.event.FeedCreatedEvent
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.feedlike.repository.FeedLikeRepository
import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.FeedNotFoundException
import com.whattoeat.global.upload.ImageUploadService
import java.io.IOException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
 class FeedService(
    private val feedRepository: FeedRepository,
    private val restaurantRepository: RestaurantRepository,
    private val followRepository: FollowRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val commentRepository: CommentRepository,
    private val feedLikeRepository: FeedLikeRepository,
    private val imageUploadService: ImageUploadService,
     private val notificationRepository: NotificationRepository,
) {
    @Transactional
    @Throws(IOException::class)
     fun createFeed(
        user: User,
        feedCreateRequest: FeedCreateRequest,
        image: MultipartFile?,
    ): FeedDetailResponse {
        val restaurant =
            feedCreateRequest.restaurantId?.let {
                restaurantRepository.findById(it).orElse(null)
            }

        val imageUrl =
            if (image != null && !image.isEmpty) {
                imageUploadService.upload(image)
            } else {
                null
            }

        val feed = feedRepository.save(feedCreateRequest.toEntity(user, restaurant, imageUrl))
        eventPublisher.publishEvent(FeedCreatedEvent(feed.id, user.id))
        return FeedDetailResponse.from(feed)
    }

    @Transactional(readOnly = true)
     fun getFeeds(
        currentUserId: Long?,
        userId: Long?,
        restaurantId: Long?,
        pageable: Pageable,
    ): Page<FeedListResponse> {
        val feeds =
            if (userId != null) {
                feedRepository.findByUserId(userId, pageable)
            } else if (restaurantId != null) {
                feedRepository.findByRestaurantId(restaurantId, pageable)
            } else {
                feedRepository.findAllByOrderByIdDesc(pageable)
            }

        val feedContents = feeds.content
        val commentCounts = countCommentByFeedIds(feedContents)
        val likedFeedIds = findLikedFeedIds(currentUserId, feedContents)
        return feeds.map { feed ->
            FeedListResponse.from(
                feed,
                commentCounts.getOrDefault(feed.id, 0L),
                likedFeedIds.contains(feed.id),
            )
        }
    }

    private fun countCommentByFeedIds(feeds: List<Feed>): Map<Long, Long> {
        val feedIds = feeds.map { it.id!! }
        if (feedIds.isEmpty()) return emptyMap()
        return commentRepository.countByFeedIds(feedIds).associate { row ->
            (row[0] as Long) to ((row[1] as? Number)?.toLong() ?: 0L)
        }
    }

    private fun findLikedFeedIds(currentUserId: Long?, feeds: List<Feed>): Set<Long> {
        if (currentUserId == null || feeds.isEmpty()) {
            return emptySet()
        }

        val feedIds = feeds.map { it.id!! }
        return feedLikeRepository
            .findLikedFeedIdsByUserIdAndFeedIds(currentUserId, feedIds)
            .toHashSet()
    }

    @Transactional(readOnly = true)
     fun getFollowingFeeds(userId: Long, pageable: Pageable): Page<FeedListResponse> {
        val followingUserIds =
            followRepository
                .findByFollower_Id(userId, Pageable.unpaged())
                .content
                .map { it.following.id!! }

        if (followingUserIds.isEmpty()) {
            return Page.empty(pageable)
        }

        val feeds = feedRepository.findByUser_IdInOrderByIdDesc(followingUserIds, pageable)
        val feedContents = feeds.content
        val commentCounts = countCommentByFeedIds(feedContents)
        val likedFeedIds = findLikedFeedIds(userId, feedContents)

        return feeds.map { feed ->
            FeedListResponse.from(
                feed,
                commentCounts.getOrDefault(feed.id, 0L),
                likedFeedIds.contains(feed.id),
            )
        }
    }

    @Transactional(readOnly = true)
     fun getRandomRecommendedFeeds(userId: Long?, pageable: Pageable): Page<FeedListResponse> {
        if (userId == null) return Page.empty(pageable)

        val followingUserIds =
            followRepository
                .findByFollower_Id(userId, Pageable.unpaged())
                .content
                .map { it.following.id!! }

        val feeds =
            if (followingUserIds.isEmpty()) {
                feedRepository.findAllByOrderByIdDesc(pageable)
            } else {
                feedRepository.findByUser_IdNotInOrderByIdDesc(followingUserIds, pageable)
            }

        val feedContents = feeds.content
        val commentCounts = countCommentByFeedIds(feedContents)
        val likedFeedIds = findLikedFeedIds(userId, feedContents)

        return feeds.map { feed ->
            FeedListResponse.from(
                feed,
                commentCounts.getOrDefault(feed.id, 0L),
                likedFeedIds.contains(feed.id),
            )
        }
    }

    @Transactional
    @Throws(IOException::class)
     fun updateFeed(
        feedId: Long,
        currentUserId: Long,
        request: FeedUpdateRequest,
        image: MultipartFile?,
    ): FeedDetailResponse {
        val feed =
            feedRepository.findById(feedId).orElseThrow {
                FeedNotFoundException(feedId)
            }

        if (feed.user.id != currentUserId) {
            throw AccessDeniedException("본인 피드만 수정할 수 있습니다.")
        }

        val restaurant =
            request.restaurantId?.let {
                restaurantRepository.findById(it).orElse(null)
            }

        var imageUrl = feed.imageUrl
        if (request.deleteImage) {
            imageUrl = null
        }
        if (image != null && !image.isEmpty) {
            imageUrl = imageUploadService.upload(image)
        }

        feed.update(request.content, restaurant, imageUrl)
        // moodTag는 값이 있을 때만 변경 (미전송 시 기존 값 유지)
        request.moodTag?.let { feed.moodTag = it }
        return FeedDetailResponse.from(feedRepository.save(feed))
    }

    @Transactional
     fun deleteFeed(feedId: Long, currentUserId: Long) {
        val feed =
            feedRepository.findById(feedId).orElseThrow {
                FeedNotFoundException(feedId)
            }

        if (feed.user.id != currentUserId) {
            throw AccessDeniedException("본인 피드만 삭제할 수 있습니다.")
        }

        notificationRepository.clearFeedReference(feedId)

        feedLikeRepository.deleteAllByFeed_Id(feedId)

        commentRepository.deleteAllByFeed_Id(feedId)

        feedRepository.delete(feed)

    }
}
