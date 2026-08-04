package com.whattoeat.domain.restaurant.service

import com.whattoeat.domain.restaurant.dto.RestaurantRequest
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.mapper.toCategory
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.global.exception.RestaurantNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestaurantService (
    private val restaurantRepository: RestaurantRepository
) {
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
                            toCategory(request.categoryName),
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
