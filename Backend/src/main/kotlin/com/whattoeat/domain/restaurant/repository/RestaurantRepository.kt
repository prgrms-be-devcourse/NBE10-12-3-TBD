package com.whattoeat.domain.restaurant.repository

import com.whattoeat.domain.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RestaurantRepository : JpaRepository<Restaurant, Long> {
     fun findByKakaoPlaceId(kakaoPlaceId: String) : Optional<Restaurant>
    fun findByKakaoPlaceIdIn(kakaoPlaceIds: List<String>) : List<Restaurant>
}
