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

    @BeforeEach
    fun setUp() {
        entityManager.persistAndFlush(
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
