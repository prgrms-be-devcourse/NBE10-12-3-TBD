package com.whattoeat.domain.restaurant.controller

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.service.RestaurantService
import com.whattoeat.domain.restaurant.service.TodayHotPlaceService
import com.whattoeat.global.exception.RestaurantNotFoundException
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetailsService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    controllers = [RestaurantController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var restaurantService: RestaurantService

    @MockitoBean
    lateinit var todayHotPlaceService: TodayHotPlaceService

    @MockitoBean
    lateinit var jwtUtil: JwtUtil

    @MockitoBean
    lateinit var customUserDetailsService: CustomUserDetailsService

    @MockitoBean
    lateinit var redisTemplate: RedisTemplate<String, String>

    private fun createRestaurant(
        kakaoId: String,
        name: String,
        category: Category
    ): Restaurant {
        return Restaurant(
            kakaoId,
            name,
            category,
            "서울시 강남구",
            "서울시 강남구 테헤란로",
            "서울",
            "강남구",
            "역삼동",
            null,
            "02-0000-0000",
            37.5,
            127.0
        )
    }

    @Test
    @DisplayName("kakaoPlaceId로 조회 시 DB에 있으면 200 반환")
    fun `getByKakaoPlaceId 성공`() {
        val restaurant = createRestaurant(
            "kakao-1",
            "맛집",
            Category.KOREAN
        )

        given(
            restaurantService.findByKakaoPlaceId(
                "kakao-1"
            )
        ).willReturn(restaurant)

        mockMvc.perform(
            get("/api/v1/restaurants")
                .param(
                    "kakaoPlaceId",
                    "kakao-1"
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.name")
                    .value("맛집")
            )
            .andExpect(
                jsonPath("$.data.kakaoPlaceId")
                    .value("kakao-1")
            )
    }

    @Test
    @DisplayName("kakaoPlaceId로 조회 시 DB에 없으면 404 반환")
    fun `getByKakaoPlaceId 없음 404`() {
        given(
            restaurantService.findByKakaoPlaceId(
                "unknown"
            )
        ).willThrow(
            RestaurantNotFoundException(
                "DB에 없는 식당입니다."
            )
        )

        mockMvc.perform(
            get("/api/v1/restaurants")
                .param(
                    "kakaoPlaceId",
                    "unknown"
                )
        )
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("존재하는 id로 식당 상세 조회 시 200과 식당 정보를 반환한다")
    fun `getRestaurantById 성공`() {
        val restaurant = createRestaurant(
            "kakao-99",
            "을지삼겹",
            Category.KOREAN
        )

        given(
            restaurantService.findById(1L)
        ).willReturn(restaurant)

        mockMvc.perform(
            get("/api/v1/restaurants/1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.name")
                    .value("을지삼겹")
            )
            .andExpect(
                jsonPath("$.data.category")
                    .value("KOREAN")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("식당 조회가 완료 되었습니다.")
            )
    }

    @Test
    @DisplayName("존재하지 않는 id로 식당 상세 조회 시 404를 반환한다")
    fun `getRestaurantById 존재하지 않는 id 404`() {
        given(
            restaurantService.findById(999L)
        ).willThrow(
            RestaurantNotFoundException(999L)
        )

        mockMvc.perform(
            get("/api/v1/restaurants/999")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("GET /today-hot 은 서비스 결과를 Recommend 배열로 감싸 반환한다")
    fun `getTodayHotPlaces returns top3`() {
        val r1 = createRestaurant("k1", "핫플1", Category.KOREAN)
        val r2 = createRestaurant("k2", "핫플2", Category.JAPANESE)
        val r3 = createRestaurant("k3", "핫플3", Category.WESTERN)
        given(todayHotPlaceService.getTodayHotPlaces())
            .willReturn(listOf(r1, r2, r3))

        mockMvc.perform(get("/api/v1/restaurants/today-hot"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].name").value("핫플1"))
            .andExpect(jsonPath("$.data[2].name").value("핫플3"))
    }
}
