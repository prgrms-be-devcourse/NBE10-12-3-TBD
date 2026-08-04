package com.whattoeat.domain.feed.repository

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.restaurant.entity.MoodTag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FeedRepository : JpaRepository<Feed, Long> {
    @EntityGraph(attributePaths = ["user", "restaurant"])
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<Feed>

    @EntityGraph(attributePaths = ["user", "restaurant"])
    fun findByUserId(userId: Long, pageable: Pageable): Page<Feed>

    @EntityGraph(attributePaths = ["user", "restaurant"])
    fun findByRestaurantId(restaurantId: Long, pageable: Pageable): Page<Feed>

    // 팔로잉 탭: 나 자신이거나 내가 팔로우하는 사람의 글. 팔로우 목록을 Java로 통째로 가져와
    // IN 파라미터로 넘기면(대량 팔로우 사용자의 경우 메모리/파라미터 폭발) 문제가 되므로,
    // 팔로우 여부 자체를 EXISTS 서브쿼리로 DB에서 판정한다.
    @EntityGraph(attributePaths = ["user", "restaurant"])
    @Query(
        """
        select f from Feed f
        where f.user.id = :userId
           or exists (
                select 1 from Follow fo
                where fo.follower.id = :userId and fo.following.id = f.user.id
              )
        order by f.id desc
        """,
        countQuery = """
        select count(f) from Feed f
        where f.user.id = :userId
           or exists (
                select 1 from Follow fo
                where fo.follower.id = :userId and fo.following.id = f.user.id
              )
        """,
    )
    fun findFollowingFeeds(@Param("userId") userId: Long, pageable: Pageable): Page<Feed>

    // 추천 후보: 나 자신이 아니고 내가 팔로우하는 사람도 아닌 글. 위와 같은 이유로 팔로우
    // 목록을 Java로 가져오지 않고 EXISTS 서브쿼리로 판정한다. beforeFeedId가 없으면(최초 조회)
    // 최신순 상한만 적용하고, 있으면(새로고침) 그보다 오래된 글만 다음 후보로 가져온다.
    // content만 쓰고 totalElements/totalPages는 쓰지 않으므로 List로 선언해 COUNT 쿼리를 없앤다.
    @EntityGraph(attributePaths = ["user", "restaurant"])
    @Query(
        """
        select f from Feed f
        where f.user.id <> :userId
          and not exists (
                select 1 from Follow fo
                where fo.follower.id = :userId and fo.following.id = f.user.id
              )
          and (:beforeFeedId is null or f.id < :beforeFeedId)
        order by f.id desc
        """,
    )
    fun findRecommendCandidates(
        @Param("userId") userId: Long,
        @Param("beforeFeedId") beforeFeedId: Long?,
        pageable: Pageable,
    ): List<Feed>

    @Query(
        "SELECT f.restaurant.id AS restaurantId, COUNT(f) AS voteCount FROM Feed f " +
                "WHERE f.moodTag = :mood AND f.restaurant.id IN :restaurantIds GROUP BY f.restaurant.id"
    )
    fun countMoodVotes(
        @Param("mood") mood: MoodTag,
        @Param("restaurantIds") restaurantIds: List<Long>
    ): List<MoodVoteCount>
}
