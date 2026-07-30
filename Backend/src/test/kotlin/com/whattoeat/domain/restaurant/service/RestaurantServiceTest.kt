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
import org.mockito.BDDMockito.then
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
    fun `recommend 필터없이 전체에서 랜덤 추천`() {
        val r1 = createRestaurant(
            "kakao-1",
            "식당A",
            Category.KOREAN,
            "서울"
        )
        val r2 = createRestaurant(
            "kakao-2",
            "식당B",
            Category.WESTERN,
            "서울"
        )

        given(
            restaurantRepository.findRecommended(
                null,
                null,
                null,
                null,
                null
            )
        ).willReturn(listOf(r1, r2))

        val result = restaurantService.recommend(
            null,
            null,
            null,
            null,
            null
        )

        assertThat(result).isIn(r1, r2)

        then(restaurantRepository)
            .should()
            .findRecommended(
                null,
                null,
                null,
                null,
                null
            )
    }

    @Test
    fun `recommend 카테고리 필터로 추천`() {
        val r1 = createRestaurant(
            "kakao-1",
            "한식당A",
            Category.KOREAN,
            "서울"
        )
        val r2 = createRestaurant(
            "kakao-2",
            "한식당B",
            Category.KOREAN,
            "부산"
        )

        given(
            restaurantRepository.findRecommended(
                Category.KOREAN,
                null,
                null,
                null,
                null
            )
        ).willReturn(listOf(r1, r2))

        val result = restaurantService.recommend(
            Category.KOREAN,
            null,
            null,
            null,
            null
        )

        assertThat(result).isIn(r1, r2)
        assertThat(result.category).isEqualTo(Category.KOREAN)
    }

    @Test
    fun `recommend 결과가 하나면 그것을 반환`() {
        val r1 = createRestaurant(
            "kakao-1",
            "식당A",
            Category.KOREAN,
            "서울"
        )

        given(
            restaurantRepository.findRecommended(
                Category.KOREAN,
                "서울",
                "강남구",
                "역삼동",
                null
            )
        ).willReturn(listOf(r1))

        val result = restaurantService.recommend(
            Category.KOREAN,
            "서울",
            "강남구",
            "역삼동",
            null
        )

        assertThat(result).isEqualTo(r1)
    }

    @Test
    fun `recommend 조건에 맞는 식당 없으면 예외발생`() {
        given(
            restaurantRepository.findRecommended(
                Category.CAFE,
                "부산",
                null,
                null,
                null
            )
        ).willReturn(emptyList())

        assertThatThrownBy {
            restaurantService.recommend(
                Category.CAFE,
                "부산",
                null,
                null,
                null
            )
        }
            .isInstanceOf(RestaurantNotFoundException::class.java)
            .hasMessage("조건에 맞는 식당이 없습니다.")
    }

    @Test
    fun `recommend 모든 필터 적용하여 추천`() {
        val r1 = createRestaurant(
            "kakao-1",
            "식당A",
            Category.JAPANESE,
            "서울"
        )

        given(
            restaurantRepository.findRecommended(
                Category.JAPANESE,
                "서울",
                "강남구",
                null,
                null
            )
        ).willReturn(listOf(r1))

        val result = restaurantService.recommend(
            Category.JAPANESE,
            "서울",
            "강남구",
            null,
            null
        )

        assertThat(result).isEqualTo(r1)
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