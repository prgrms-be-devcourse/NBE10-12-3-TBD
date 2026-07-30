package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

import java.util.Optional

interface RestaurantListRepository : JpaRepository<RestaurantList, Long> {
    // 내 리스트 조회 : 최신 생성 리스트가 위로 오게 id 내림차순
    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<RestaurantList>

    fun findByIdAndUserId(id: Long, userId: Long): Optional<RestaurantList>

    @Query("""
        select distinct rl
          from RestaurantList rl
          left join fetch rl.items i
          left join fetch i.restaurant
         where rl.id = :id
    """)
    fun findByIdWithItems(@Param("id") id: Long?): Optional<RestaurantList>

    fun findByUserIdNot(userId: Long, pageable: Pageable): Page<RestaurantList>
}
