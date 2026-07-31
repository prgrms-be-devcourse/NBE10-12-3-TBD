package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurantlist.entity.SavedRestaurantList
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface SavedRestaurantListRepository : JpaRepository<SavedRestaurantList, Long> {
    fun existsByUserIdAndRestaurantListId(userId: Long, restaurantListId: Long): Boolean

    fun findByUserIdAndRestaurantListId(userId: Long, id: Long): Optional<SavedRestaurantList>

    @EntityGraph(attributePaths = [
            "restaurantList",
            "restaurantList.user"
    ])
    fun findByUserId(userId: Long, pageable: Pageable): Page<SavedRestaurantList>
    fun deleteAllByRestaurantListId(restaurantListId: Long): Long

    @Query(
        """
      select
          rl.id as id,
          rl.user.id as userId,
          rl.user.nickname as nickname,
          rl.title as title,
          rl.description as description,
          rl.moodTag as moodTag,
          count(distinct item.id) as itemCount,
          rl.createdAt as createdAt,
          count(distinct saved.id) as saveCount
      from SavedRestaurantList saved
      join saved.restaurantList rl
      join rl.items item
      where rl.user.id <> :excludedOwnerId
      group by
          rl.id,
          rl.user.id,
          rl.user.nickname,
          rl.title,
          rl.description,
          rl.moodTag,
          rl.createdAt
      order by
          count(distinct saved.id) desc,
          rl.createdAt desc,
          rl.id desc
      """
    )
    fun findPopularLists(
        excludedOwnerId: Long,
        pageable: Pageable
    ): List<PopularRestaurantListProjection>
}
