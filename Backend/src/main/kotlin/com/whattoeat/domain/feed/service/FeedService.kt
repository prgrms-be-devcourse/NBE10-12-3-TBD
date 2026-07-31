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
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.FeedNotFoundException
import com.whattoeat.global.upload.ImageUploadService
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
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
) {
    companion object {
        // 추천 후보로 가져올 최신 피드 최대 개수 (전체 스캔 방지용 상한)
        private const val RECOMMEND_CANDIDATE_LIMIT = 300
        private const val LIKE_WEIGHT = 1.0
        private const val COMMENT_WEIGHT = 2.0
        private const val SECOND_DEGREE_AUTHOR_BONUS = 6.0
        private const val FOLLOWING_LIKE_BONUS = 3.0
        private const val TIME_DECAY_PER_HOUR = 0.015
    }

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
     fun getRecommendedFeeds(userId: Long?, pageable: Pageable): Page<FeedListResponse> {
        if (userId == null) return Page.empty(pageable)

        val followingUserIds =
            followRepository
                .findByFollower_Id(userId, Pageable.unpaged())
                .content
                .map { it.following.id!! }

        // 팔로우 탭(getFollowingFeeds)에서 이미 보여주는 글이므로 추천 후보에서는 나 자신과
        // 내가 팔로우하는 사람들의 글을 모두 제외한다.
        val excludedUserIds = (followingUserIds + userId).toHashSet()

        val candidates =
            feedRepository
                .findByUser_IdNotInOrderByIdDesc(excludedUserIds, PageRequest.of(0, RECOMMEND_CANDIDATE_LIMIT))
                .content

        if (candidates.isEmpty()) {
            return Page.empty(pageable)
        }

        val commentCounts = countCommentByFeedIds(candidates)
        val likedFeedIds = findLikedFeedIds(userId, candidates)

        // 내가 팔로우하는 사람들이 팔로우하는 사람(2차 팔로우)의 글, 그리고 그들이 좋아요한 글을 가산점 신호로 사용한다.
        val secondDegreeAuthorIds =
            if (followingUserIds.isEmpty()) {
                emptySet()
            } else {
                followRepository.findFollowingIdsByFollowerIds(followingUserIds).toHashSet()
            }
        val likedByFollowingFeedIds =
            if (followingUserIds.isEmpty()) {
                emptySet()
            } else {
                feedLikeRepository.findFeedIdsLikedByUserIds(followingUserIds).toHashSet()
            }

        val ranked =
            candidates.sortedByDescending { feed ->
                recommendationScore(
                    feed = feed,
                    commentCount = commentCounts.getOrDefault(feed.id, 0L),
                    isFromSecondDegreeFollowing = secondDegreeAuthorIds.contains(feed.user.id),
                    isLikedByFollowing = likedByFollowingFeedIds.contains(feed.id),
                )
            }

        val start = pageable.offset.toInt()
        if (start >= ranked.size) {
            return PageImpl(emptyList(), pageable, ranked.size.toLong())
        }
        val end = minOf(start + pageable.pageSize, ranked.size)

        val content =
            ranked.subList(start, end).map { feed ->
                FeedListResponse.from(
                    feed,
                    commentCounts.getOrDefault(feed.id, 0L),
                    likedFeedIds.contains(feed.id),
                )
            }

        return PageImpl(content, pageable, ranked.size.toLong())
    }

    // 좋아요/댓글 수는 로그 스케일로 눌러 소수의 인기글이 점수를 독식하지 않게 하고,
    // 팔로우 신호(2차 팔로우 작성자 / 팔로우한 사람이 좋아요)는 가산점으로 반영,
    // 오래된 글은 시간당 소폭 감쇠시켜 신선도를 유지한다.
    private fun recommendationScore(
        feed: Feed,
        commentCount: Long,
        isFromSecondDegreeFollowing: Boolean,
        isLikedByFollowing: Boolean,
    ): Double {
        val engagementScore =
            LIKE_WEIGHT * ln1p(feed.likeCount.toDouble()) + COMMENT_WEIGHT * ln1p(commentCount.toDouble())

        val socialBonus =
            (if (isFromSecondDegreeFollowing) SECOND_DEGREE_AUTHOR_BONUS else 0.0) +
                (if (isLikedByFollowing) FOLLOWING_LIKE_BONUS else 0.0)

        val freshnessPenalty =
            feed.createdAt?.let { createdAt ->
                val hoursSinceCreated = Duration.between(createdAt, LocalDateTime.now()).toHours()
                TIME_DECAY_PER_HOUR * maxOf(hoursSinceCreated, 0L).toDouble()
            } ?: 0.0

        return engagementScore + socialBonus - freshnessPenalty
    }

    private fun ln1p(value: Double): Double = kotlin.math.ln(1 + value)

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

        feedRepository.delete(feed)
    }
}
