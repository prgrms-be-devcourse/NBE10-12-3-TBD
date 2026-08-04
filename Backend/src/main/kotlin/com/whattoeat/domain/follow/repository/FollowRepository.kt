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

    // 2차 팔로우(내가 팔로우하는 사람이 팔로우하는 사람) 작성자 판별. 내 팔로우 목록을 Java로
    // 가져와 IN 파라미터로 넘기면 대량 팔로우 사용자에게서 메모리/파라미터 폭발이 생기므로,
    // Follow-Follow 셀프 조인으로 DB에서 직접 2차 팔로우를 계산한다. authorIds는 지금 추천
    // 후보(최대 300명)로 이미 범위가 좁혀져 있어 IN으로 넘겨도 문제 없다.
    @Query(
        """
    select distinct fo2.following.id
    from Follow fo1
    join Follow fo2 on fo2.follower.id = fo1.following.id
    where fo1.follower.id = :userId
      and fo2.following.id in :authorIds
""",
    )
    fun findSecondDegreeAuthorIds(
        @Param("userId") userId: Long,
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
