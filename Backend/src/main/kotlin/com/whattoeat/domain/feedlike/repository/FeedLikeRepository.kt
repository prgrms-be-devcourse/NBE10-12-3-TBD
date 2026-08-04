package com.whattoeat.domain.feedlike.repository

import com.whattoeat.domain.feedlike.entity.FeedLike
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FeedLikeRepository : JpaRepository<FeedLike, Long> {
    fun existsByFeed_IdAndUser_Id(feedId: Long, userId: Long): Boolean

    fun findByFeed_IdAndUser_Id(feedId: Long, userId: Long): Optional<FeedLike>

    fun deleteByFeed_IdAndUser_Id(feedId: Long, userId: Long)

    fun deleteAllByFeed_Id(feedId: Long)

    @Query(
        """
        select fl.feed.id
        from FeedLike fl
        where fl.user.id = :userId
          and fl.feed.id in :feedIds
        """,
    )
    fun findLikedFeedIdsByUserIdAndFeedIds(
        @Param("userId") userId: Long?,
        @Param("feedIds") feedIds: List<Long>?,
    ): List<Long>

    // 내가 팔로우하는 사람이 좋아요한 후보 글 판별. 내 팔로우 목록을 Java로 가져와 IN
    // 파라미터로 넘기면 대량 팔로우 사용자에게서 메모리/파라미터 폭발이 생기므로, 팔로우
    // 여부를 EXISTS 서브쿼리로 DB에서 직접 판정한다. feedIds는 지금 후보(최대 300개)로
    // 이미 범위가 좁혀져 있어 IN으로 넘겨도 문제 없다.
    @Query(
        """
        select distinct fl.feed.id
        from FeedLike fl
        where fl.feed.id in :feedIds
          and exists (
                select 1 from Follow fo
                where fo.follower.id = :userId and fo.following.id = fl.user.id
              )
        """,
    )
    fun findFeedIdsLikedByFollowingOf(
        @Param("userId") userId: Long,
        @Param("feedIds") feedIds: Collection<Long>,
    ): List<Long>

    @Query(
        """
        select fl.feed.id, count(fl)
        from FeedLike fl
        where fl.feed.id in :feedIds
          and fl.createdAt >= :since
        group by fl.feed.id
        """,
    )
    fun countRecentLikesByFeedIds(
        @Param("feedIds") feedIds: List<Long>,
        @Param("since") since: LocalDateTime,
    ): List<Array<Any>>
}
