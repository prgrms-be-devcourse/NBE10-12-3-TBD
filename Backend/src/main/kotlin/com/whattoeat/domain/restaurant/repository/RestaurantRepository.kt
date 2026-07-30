package com.whattoeat.domain.restaurant.repository

import com.whattoeat.domain.restaurant.entity.Category;
import com.whattoeat.domain.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface RestaurantRepository : JpaRepository<Restaurant, Long> {

    @Query("SELECT r FROM Restaurant r WHERE " +
            "(:category IS NULL OR r.category = :category) AND " +
            "(:region1 IS NULL OR r.region1 = :region1) AND " +
            "(:region2 IS NULL OR r.region2 = :region2) AND " +
            "(:region3 IS NULL OR r.region3 = :region3) AND " +
            "(:region4 IS NULL OR r.region4 = :region4)")
    fun findRecommended(
        @Param("category") category: Category?,
        @Param("region1") region1: String?,
        @Param("region2") region2: String?,
        @Param("region3") region3: String?,
        @Param("region4") region4: String?
    ) : List<Restaurant>

     fun findByKakaoPlaceId(kakaoPlaceId: String) : Optional<Restaurant>
}
