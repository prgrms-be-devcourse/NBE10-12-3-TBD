package com.whattoeat.domain.restaurant.repository

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.global.config.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(JpaConfig::class)
class RestaurantRepositoryTest {

    @Autowired
    lateinit var restaurantRepository: RestaurantRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    private lateinit var koreanSeoul: Restaurant
    private lateinit var westernSeoul: Restaurant
    private lateinit var koreanBusan: Restaurant

    @BeforeEach
    fun setUp() {
        koreanSeoul = entityManager.persistAndFlush(
            Restaurant(
                "kakao-1",
                "서울한식당",
                Category.KOREAN,
                "서울시 강남구",
                "서울시 강남구 테헤란로",
                "서울",
                "강남구",
                "역삼동",
                null,
                "02-1111-1111",
                37.5,
                127.0
            )
        )

        westernSeoul = entityManager.persistAndFlush(
            Restaurant(
                "kakao-2",
                "서울양식당",
                Category.WESTERN,
                "서울시 서초구",
                "서울시 서초구 서초대로",
                "서울",
                "서초구",
                "서초동",
                null,
                "02-2222-2222",
                37.5,
                127.1
            )
        )

        koreanBusan = entityManager.persistAndFlush(
            Restaurant(
                "kakao-3",
                "부산한식당",
                Category.KOREAN,
                "부산시 해운대구",
                "부산시 해운대구 해운대로",
                "부산",
                "해운대구",
                "우동",
                null,
                "051-3333-3333",
                35.1,
                129.1
            )
        )
    }

    @Test
    fun `findRecommended 필터없이 전체 조회`() {
        val result = restaurantRepository.findRecommended(
            null,
            null,
            null,
            null,
            null
        )

        assertThat(result).hasSize(3)
    }

    @Test
    fun `findRecommended 카테고리 필터만 적용`() {
        val result = restaurantRepository.findRecommended(
            Category.KOREAN,
            null,
            null,
            null,
            null
        )

        assertThat(result).hasSize(2)
        assertThat(result)
            .extracting("category")
            .containsOnly(Category.KOREAN)
    }

    @Test
    fun `findRecommended 지역1 필터만 적용`() {
        val result = restaurantRepository.findRecommended(
            null,
            "서울",
            null,
            null,
            null
        )

        assertThat(result).hasSize(2)
        assertThat(result)
            .extracting("region1")
            .containsOnly("서울")
    }

    @Test
    fun `findRecommended 카테고리와 지역1 필터 조합`() {
        val result = restaurantRepository.findRecommended(
            Category.KOREAN,
            "서울",
            null,
            null,
            null
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("서울한식당")
    }

    @Test
    fun `findRecommended 모든 필터 조합`() {
        val result = restaurantRepository.findRecommended(
            Category.KOREAN,
            "서울",
            "강남구",
            "역삼동",
            null
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].kakaoPlaceId).isEqualTo("kakao-1")
    }

    @Test
    fun `findRecommended 조건에 맞는 데이터 없으면 빈 리스트`() {
        val result = restaurantRepository.findRecommended(
            Category.CAFE,
            null,
            null,
            null,
            null
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `findRecommended 지역2 필터만 적용`() {
        val result = restaurantRepository.findRecommended(
            null,
            null,
            "강남구",
            null,
            null
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].region2).isEqualTo("강남구")
    }

    @Test
    fun `findByKakaoPlaceId 성공`() {
        val result = restaurantRepository.findByKakaoPlaceId("kakao-1")

        assertThat(result).isPresent
        assertThat(result.get().name).isEqualTo("서울한식당")
    }

    @Test
    fun `findByKakaoPlaceId 없으면 empty`() {
        val result = restaurantRepository.findByKakaoPlaceId("unknown")

        assertThat(result).isEmpty
    }
}