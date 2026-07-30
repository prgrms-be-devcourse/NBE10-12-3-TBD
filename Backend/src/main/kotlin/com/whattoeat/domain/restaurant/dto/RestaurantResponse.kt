package com.whattoeat.domain.restaurant.dto

import com.whattoeat.domain.restaurant.entity.Restaurant;

import java.time.LocalDateTime;

class RestaurantResponse {

    data class KakaoRestaurant(
        val id: Long,
        val kakaoPlaceId: String,
        val name: String,
        val category: String,
        val address: String,
        val roadAddress: String,
        val region1: String,
        val region2: String,
        val region3: String,
        val region4: String,
        val phone: String,
        val lat: Double,
        val lng: Double,
    )

    data class Recommend(
    val id: Long?,
    val kakaoPlaceId: String,
    val name: String,
    val category: String,
    val address: String,
    val roadAddress: String?,
    val region1: String?,
    val region2: String?,
    val region3: String?,
    val region4: String?,
    val phone: String?,
    val lat: Double?,
    val lng: Double?,
    val createdAt: LocalDateTime?,
    ) {
        constructor(restaurant: Restaurant) : this(
                restaurant.id,
                restaurant.kakaoPlaceId,
                restaurant.name,
                restaurant.category.name,
                restaurant.address,
                restaurant.roadAddress,
                restaurant.region1,
                restaurant.region2,
                restaurant.region3,
                restaurant.region4,
                restaurant.phone,
                restaurant.lat,
                restaurant.lng,
                restaurant.createdAt
            )
    }
}