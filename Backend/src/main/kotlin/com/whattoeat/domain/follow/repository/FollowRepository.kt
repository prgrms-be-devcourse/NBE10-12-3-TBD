package com.whattoeat.domain.follow.repository

import com.whattoeat.domain.follow.entity.Follow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface FollowRepository : JpaRepository<Follow, Long> {
    fun findByFollower_IdAndFollowing_Id(followerId: Long, followingId: Long): Optional<Follow>

    fun existsByFollower_IdAndFollowing_Id(followerId: Long, followingId: Long): Boolean

    @EntityGraph(attributePaths = ["following"])
    fun findByFollower_Id(followerId: Long, pageable: Pageable): Page<Follow>

    @EntityGraph(attributePaths = ["follower"])
    fun findByFollowing_Id(followingId: Long, pageable: Pageable): Page<Follow>

    fun deleteByFollower_IdAndFollowing_Id(followerId: Long, followingId: Long)

    fun countByFollower_Id(followerId: Long): Long

    fun countByFollowing_Id(followingId: Long): Long

    @Query(
        """
    select distinct f.following.id
    from Follow f
    where f.follower.id in :followerIds
      and f.following.id in :authorIds
""",
    )
    fun findFollowingIdsByFollowerIds(
        @Param("followerIds") followerIds: Collection<Long>,
        @Param("authorIds") authorIds: Collection<Long>,
    ): List<Long>

    @Query(
        """
    select f.following.id, count(f)
    from Follow f
    where f.following.id in :userIds
    group by f.following.id
""",
    )
    fun countFollowersByUserIds(@Param("userIds") userIds: Collection<Long>): List<Array<Any>>

    @Query(
        """
    select f.following.id, count(f)
    from Follow f
    where f.following.id in :userIds
      and f.createdAt >= :since
    group by f.following.id
""",
    )
    fun countRecentFollowersByUserIds(
        @Param("userIds") userIds: Collection<Long>,
        @Param("since") since: LocalDateTime,
    ): List<Array<Any>>
}
