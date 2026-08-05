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
import java.time.Duration
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
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
     private val notificationRepository: NotificationRepository,
) {
    companion object {
        // 추천 후보로 가져올 최신 피드 최대 개수 (전체 스캔 방지용 상한)
        private const val RECOMMEND_CANDIDATE_LIMIT = 300
        private const val LIKE_WEIGHT = 1.0
        private const val COMMENT_WEIGHT = 2.0
        private const val SECOND_DEGREE_AUTHOR_BONUS = 6.0
        private const val FOLLOWING_LIKE_BONUS = 3.0
        private const val TIME_DECAY_PER_HOUR = 0.015

        // 최근 이 시간 안에 달린 좋아요/댓글/팔로우만 "급증" 신호로 인정한다.
        private const val SURGE_WINDOW_HOURS = 24L

        // 최근 급증 신호 임계값: 하나라도 넘으면 "떡상 중"으로 간주한다.
        private const val SURGE_RECENT_LIKE_THRESHOLD = 5L //좋아요 5개 이상부트 급증
        private const val SURGE_RECENT_COMMENT_THRESHOLD = 3L // 댓글 3개 이상부터 급증
        private const val SURGE_RECENT_FOLLOWER_THRESHOLD = 3L // 팔로워가 3명이상부터 늘면 급증

        // 팔로워 수가 이 값 이상이면 "인플루언서"로 간주해 노출 가산 대상에 포함한다.
        private const val POPULAR_AUTHOR_FOLLOWER_THRESHOLD = 50L

        // 스포트라이트(급증/인기 작성자) 후보 피드는 점수 정렬과 무관하게
        // 이 간격마다 한 번씩 의도적으로 노출시킨다.
        private const val SPOTLIGHT_INTERVAL = 5

        // 한 페이지 계산에 사용할 스포트라이트 후보 최대 개수
        private const val SPOTLIGHT_POOL_LIMIT = 10
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
        return toCountMap(commentRepository.countByFeedIds(feedIds))
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
        // 팔로잉 탭에는 내가 팔로우하는 사람들의 글뿐 아니라 내 글도 함께 노출한다. 팔로우
        // 목록을 Java로 가져와 IN 파라미터로 넘기지 않고, 팔로우 여부 자체를 쿼리 안에서
        // EXISTS로 판정하므로 팔로우 수가 아무리 많아도 메모리에 올리지 않는다.
        val feeds = feedRepository.findFollowingFeeds(userId, pageable)
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
     fun getRecommendedFeeds(userId: Long?, beforeFeedId: Long? = null): List<FeedListResponse> {
        if (userId == null) return emptyList()

        // 추천 후보: 나 자신도 아니고 내가 팔로우하는 사람도 아닌 글(팔로우 탭에서 이미 보여주므로).
        // 팔로우 목록을 Java로 가져와 NOT IN 파라미터로 넘기지 않고, 쿼리 안에서 EXISTS로
        // 판정하므로 팔로우 수가 아무리 많아도 메모리/파라미터 폭발이 없다. beforeFeedId가
        // 주어지면(새로고침) 이전 배치보다 오래된 다음 300개를 후보로 가져온다.
        val candidatePage = PageRequest.of(0, RECOMMEND_CANDIDATE_LIMIT)
        val candidates = feedRepository.findRecommendCandidates(userId, beforeFeedId, candidatePage)

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val candidateIds = candidates.map { it.id!! }
        val candidateAuthorIds = candidates.map { it.user.id!! }.toHashSet()
        val commentCounts = countCommentByFeedIds(candidates)
        val likedFeedIds = findLikedFeedIds(userId, candidates)

        // 내가 팔로우하는 사람들이 팔로우하는 사람(2차 팔로우)의 글, 그리고 그들이 좋아요한 글을
        // 가산점 신호로 사용한다. 여기서도 내 팔로우 목록을 미리 가져오지 않고, 지금 후보
        // (candidateAuthorIds/candidateIds)로 범위를 좁힌 채 DB에서 직접 판정한다.
        val secondDegreeAuthorIds =
            followRepository.findSecondDegreeAuthorIds(userId, candidateAuthorIds).toHashSet()
        val likedByFollowingFeedIds =
            feedLikeRepository.findFeedIdsLikedByFollowingOf(userId, candidateIds).toHashSet()

        // sortedByDescending은 정렬 비교마다 selector를 다시 호출하므로, recommendationScore를
        // 그 자리에서 계산하면 O(n) 대신 O(n log n)번 호출된다. 점수를 먼저 한 번씩만 계산해 둔다.
        val now = LocalDateTime.now()
        val scoredCandidates =
            candidates.map { feed ->
                feed to
                    recommendationScore(
                        feed = feed,
                        commentCount = commentCounts.getOrDefault(feed.id, 0L),
                        isFromSecondDegreeFollowing = secondDegreeAuthorIds.contains(feed.user.id),
                        isLikedByFollowing = likedByFollowingFeedIds.contains(feed.id),
                        now = now,
                    )
            }
        val ranked = scoredCandidates.sortedByDescending { (_, score) -> score }.map { (feed, _) -> feed }

        val spotlightFeeds = pickSpotlightFeeds(candidates)
        val finalOrder = interleaveSpotlightFeeds(ranked, spotlightFeeds)

        // 정렬 기준(점수)이 실시간 데이터에 따라 요청마다 흔들릴 수 있으므로, 한 배치의 순위는
        // 이 응답 한 번에 전부 확정해서 내려준다. 서버에 offset을 넘겨 여러 번 나눠 받으면
        // 그 사이 점수가 바뀌어 일부 피드가 노출되지 않고 누락될 수 있다.
        return finalOrder.map { feed ->
            FeedListResponse.from(
                feed,
                commentCounts.getOrDefault(feed.id, 0L),
                likedFeedIds.contains(feed.id),
            )
        }
    }

    // 최근 SURGE_WINDOW_HOURS 안에 좋아요/댓글이 급증했거나, 작성자가 팔로워가 많은(혹은
    // 최근 팔로워가 급증한) 인플루언서인 피드를 "스포트라이트" 후보로 뽑는다.
    private fun pickSpotlightFeeds(candidates: List<Feed>): List<Feed> {
        if (candidates.isEmpty()) return emptyList()

        val since = LocalDateTime.now().minusHours(SURGE_WINDOW_HOURS)
        val candidateIds = candidates.map { it.id!! }
        val authorIds = candidates.map { it.user.id!! }.toHashSet()

        val recentLikeCounts = toCountMap(feedLikeRepository.countRecentLikesByFeedIds(candidateIds, since))
        val recentCommentCounts = toCountMap(commentRepository.countRecentCommentsByFeedIds(candidateIds, since))
        val followerCounts = toCountMap(followRepository.countFollowersByUserIds(authorIds))
        val recentFollowerCounts = toCountMap(followRepository.countRecentFollowersByUserIds(authorIds, since))

        return candidates
            .filter { feed ->
                val isSurgingEngagement =
                    recentLikeCounts.getOrDefault(feed.id, 0L) >= SURGE_RECENT_LIKE_THRESHOLD ||
                        recentCommentCounts.getOrDefault(feed.id, 0L) >= SURGE_RECENT_COMMENT_THRESHOLD
                val isPopularAuthor = followerCounts.getOrDefault(feed.user.id, 0L) >= POPULAR_AUTHOR_FOLLOWER_THRESHOLD
                val isSurgingAuthor =
                    recentFollowerCounts.getOrDefault(feed.user.id, 0L) >= SURGE_RECENT_FOLLOWER_THRESHOLD

                isSurgingEngagement || isPopularAuthor || isSurgingAuthor
            }
            .sortedByDescending { feed ->
                recentLikeCounts.getOrDefault(feed.id, 0L) * LIKE_WEIGHT +
                    recentCommentCounts.getOrDefault(feed.id, 0L) * COMMENT_WEIGHT +
                    recentFollowerCounts.getOrDefault(feed.user.id, 0L) +
                    followerCounts.getOrDefault(feed.user.id, 0L)
            }
            .take(SPOTLIGHT_POOL_LIMIT)
    }

    // 점수순으로 정렬된 목록에서 스포트라이트 피드를 제거한 뒤, SPOTLIGHT_INTERVAL개마다
    // 한 번씩 의도적으로 스포트라이트 피드를 끼워 넣는다. 자연 순위와 무관하게 주기적으로
    // 노출시키는 것이 목적이므로, 원래 위치에 남겨두지 않고 슬롯 자리에서만 노출한다.
    private fun interleaveSpotlightFeeds(rankedFeeds: List<Feed>, spotlightFeeds: List<Feed>): List<Feed> {
        if (spotlightFeeds.isEmpty()) return rankedFeeds

        val spotlightIds = spotlightFeeds.mapNotNull { it.id }.toHashSet()
        val remaining = rankedFeeds.filterNot { spotlightIds.contains(it.id) }
        val spotlightQueue = ArrayDeque(spotlightFeeds)

        val result = ArrayList<Feed>(rankedFeeds.size)
        remaining.forEachIndexed { index, feed ->
            result.add(feed)
            val isSpotlightSlot = (index + 1) % SPOTLIGHT_INTERVAL == 0
            if (isSpotlightSlot && spotlightQueue.isNotEmpty()) {
                result.add(spotlightQueue.removeFirst())
            }
        }
        // 슬롯이 모자라 못 끼워 넣은 스포트라이트 피드는 끝에 이어붙인다.
        result.addAll(spotlightQueue)
        return result
    }

    private fun toCountMap(rows: List<Array<Any>>): Map<Long, Long> =
        rows.associate { row ->
            (row[0] as Long) to ((row[1] as? Number)?.toLong() ?: 0L)
        }

    // 좋아요/댓글 수는 로그 스케일로 눌러 소수의 인기글이 점수를 독식하지 않게 하고,
    // 팔로우 신호(2차 팔로우 작성자 / 팔로우한 사람이 좋아요)는 가산점으로 반영,
    // 오래된 글은 시간당 소폭 감쇠시켜 신선도를 유지한다.
    private fun recommendationScore(
        feed: Feed,
        commentCount: Long,
        isFromSecondDegreeFollowing: Boolean,
        isLikedByFollowing: Boolean,
        now: LocalDateTime,
    ): Double {
        val engagementScore =
            LIKE_WEIGHT * ln1p(feed.likeCount.toDouble()) + COMMENT_WEIGHT * ln1p(commentCount.toDouble())

        val socialBonus =
            (if (isFromSecondDegreeFollowing) SECOND_DEGREE_AUTHOR_BONUS else 0.0) +
                (if (isLikedByFollowing) FOLLOWING_LIKE_BONUS else 0.0)

        val freshnessPenalty =
            feed.createdAt?.let { createdAt ->
                val hoursSinceCreated = Duration.between(createdAt, now).toHours()
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
        feed.moodTag = request.moodTag
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
