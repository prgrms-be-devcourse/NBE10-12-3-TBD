package com.whattoeat.domain.restaurant.service

import com.whattoeat.domain.restaurant.dto.RestaurantRequest
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.global.exception.RestaurantNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import kotlin.random.Random

@Service
class RestaurantService (
    private val restaurantRepository: RestaurantRepository
) {
    private val random = Random.Default

    fun recommend(
        category: Category?,
        region1: String?,
        region2: String?,
        region3: String?,
        region4: String?
    ) : Restaurant {
        val restaurants = restaurantRepository.findRecommended(
            category,
            region1,
            region2,
            region3,
            region4
        )

        if (restaurants.isEmpty()) {
            throw RestaurantNotFoundException("조건에 맞는 식당이 없습니다.");
        }
        return restaurants[random.nextInt(restaurants.size)];
    }

    @Transactional(readOnly = true)
    fun findByKakaoPlaceId(kakaoPlaceId: String) : Restaurant {
        return restaurantRepository.findByKakaoPlaceId(kakaoPlaceId)
                .orElseThrow{
                    RestaurantNotFoundException("DB에 없는 식당입니다.")
                }
    }


    @Transactional
    fun saveGetKakao(request: RestaurantRequest.FromKakao) : Restaurant {
        return restaurantRepository.findByKakaoPlaceId(request.kakaoPlaceId)
                .orElseGet {
                    restaurantRepository.save(
                            Restaurant(
                                    request.kakaoPlaceId,
                            request.name,
                            convertToCategory(request.categoryName),
                            request.address,
                            request.roadAddress,
                            defaultIfBlank(request.region1, "지역없음"),
                            defaultIfBlank(request.region2, "지역없음"),
                            request.region3,
                            request.region4,
                            request.phone,
                            request.lat,
                            request.lng
                        )
                    )
                }
    }

    private fun convertToCategory(categoryName: String?) : Category{
        val name = categoryName.orEmpty()

        return when {
            "한식" in name -> Category.KOREAN
            "중식" in name -> Category.CHINESE
            "일식" in name -> Category.JAPANESE
            "양식" in name -> Category.WESTERN
            "아시아음식" in name -> Category.ASIAN
            "카페" in name || "디저트" in name -> Category.CAFE
            "분식" in name -> Category.SNACK
            else -> Category.ETC
        }
    }

    private fun defaultIfBlank(
        value: String?,
        defaultValue: String
    ) : String {
        return value ?: defaultValue
    }

    @Transactional(readOnly = true)
    fun findAll() : List<Restaurant> {
        return restaurantRepository.findAll();
    }

    @Transactional(readOnly = true)
    fun findById(id: Long) : Restaurant {
        return restaurantRepository.findById(id)
                .orElseThrow{
                    RestaurantNotFoundException(id)
                }
    }
}
