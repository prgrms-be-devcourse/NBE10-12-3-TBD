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

    @Query(
        """
        select distinct fl.feed.id
        from FeedLike fl
        where fl.user.id in :userIds
          and fl.feed.id in :feedIds
        """,
    )
    fun findFeedIdsLikedByUserIds(
        @Param("userIds") userIds: Collection<Long>,
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
