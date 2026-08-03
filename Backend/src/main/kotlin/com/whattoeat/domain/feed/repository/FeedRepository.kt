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

    @EntityGraph(attributePaths = ["user", "restaurant"])
    fun findByUser_IdNotInOrderByIdDesc(userIds: Collection<Long>, pageable: Pageable): Page<Feed>

    @EntityGraph(attributePaths = ["user", "restaurant"])
    fun findByUser_IdInOrderByIdDesc(userIds: Collection<Long>, pageable: Pageable): Page<Feed>

    @Query(
        "SELECT f.restaurant.id AS restaurantId, COUNT(f) AS voteCount FROM Feed f " +
                "WHERE f.moodTag = :mood AND f.restaurant.id IN :restaurantIds GROUP BY f.restaurant.id"
    )
    fun countMoodVotes(
        @Param("mood") mood: MoodTag,
        @Param("restaurantIds") restaurantIds: List<Long>
    ): List<MoodVoteCount>
}
