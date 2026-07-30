package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurantlist.entity.SavedRestaurantList
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

import java.util.Optional

interface SavedRestaurantListRepository : JpaRepository<SavedRestaurantList, Long> {
    fun existsByUserIdAndRestaurantListId(userId: Long, restaurantListId: Long): Boolean

    fun findByUserIdAndRestaurantListId(userId: Long, id: Long): Optional<SavedRestaurantList>

    @EntityGraph(attributePaths = [
            "restaurantList",
            "restaurantList.user"
    ])
    fun findByUserId(userId: Long, pageable: Pageable): Page<SavedRestaurantList>
}
