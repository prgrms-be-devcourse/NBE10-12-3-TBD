package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

import java.util.Optional


interface RestaurantListItemRepository : JpaRepository<RestaurantListItem, Long> {
    @Query("""
        select item
          from RestaurantListItem item
         where item.id = :itemId
           and item.restaurantList.id = :listId
           and item.restaurantList.user.id = :userId
    """)
    fun findListItem(itemId: Long, listId: Long, userId: Long) : Optional<RestaurantListItem>

    fun existsByRestaurantListIdAndRestaurantId(listId: Long, restaurantListId: Long?): Boolean

    @Query("""
        select max(i.orderIndex)
          from RestaurantListItem i
         where i.restaurantList.id = :listId
    """)
    fun findMaxOrderIndexByListId(listId: Long): Int?

    @Modifying(clearAutomatically = true)
    @Query("""
        update RestaurantListItem i
            set i.orderIndex = i.orderIndex + 1
          where i.restaurantList.id = :listId
            and i.orderIndex >= :orderIndex
    """)
    fun incOrderIndex(listId: Long, orderIndex: Int?)

    @Query("""
        select item
          from RestaurantListItem item
         where item.restaurantList.id = :listId
         order by item.orderIndex asc
    """)
    fun findItemsByListId(listId: Long): List<RestaurantListItem>
}
