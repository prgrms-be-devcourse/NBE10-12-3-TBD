package com.whattoeat.domain.restaurant.service

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.global.exception.RestaurantNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class RestaurantServiceTest {

    @Mock
    lateinit var restaurantRepository: RestaurantRepository

    @InjectMocks
    lateinit var restaurantService: RestaurantService

    private fun createRestaurant(
        kakaoId: String,
        name: String,
        category: Category,
        region1: String
    ): Restaurant {
        return Restaurant(
            kakaoId,
            name,
            category,
            "주소",
            "도로명주소",
            region1,
            "강남구",
            "역삼동",
            null,
            "02-0000-0000",
            37.5,
            127.0
        )
    }

    @Test
    fun `findByKakaoPlaceId 성공`() {
        val restaurant = createRestaurant(
            "kakao-1",
            "식당",
            Category.KOREAN,
            "서울"
        )

        given(
            restaurantRepository.findByKakaoPlaceId("kakao-1")
        ).willReturn(Optional.of(restaurant))

        val result =
            restaurantService.findByKakaoPlaceId("kakao-1")

        assertThat(result).isEqualTo(restaurant)
    }

    @Test
    fun `findByKakaoPlaceId 없으면 예외`() {
        given(
            restaurantRepository.findByKakaoPlaceId("unknown")
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantService.findByKakaoPlaceId("unknown")
        }
            .isInstanceOf(RestaurantNotFoundException::class.java)
            .hasMessageContaining("DB에 없는 식당입니다.")
    }
}
