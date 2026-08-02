package com.whattoeat.domain.feed.repository

import com.whattoeat.domain.feed.entity.Feed
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

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
}
