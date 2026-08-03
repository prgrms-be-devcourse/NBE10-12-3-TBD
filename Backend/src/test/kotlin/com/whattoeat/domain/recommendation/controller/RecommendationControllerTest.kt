package com.whattoeat.domain.recommendation.controller

import com.whattoeat.domain.recommendation.dto.RecommendItem
import com.whattoeat.domain.recommendation.service.RecommendService
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.global.exception.RestaurantNotFoundException
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import com.whattoeat.domain.recommendation.dto.RecommendRequest

@WebMvcTest(
    controllers = [RecommendationController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class]
)

@AutoConfigureMockMvc(addFilters = false)
class RecommendationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var recommendService: RecommendService

    @MockitoBean
    lateinit var jwtUtil: JwtUtil

    @MockitoBean
    lateinit var customUserDetailsService: CustomUserDetailsService

    @MockitoBean
    lateinit var redisTemplate: RedisTemplate<String, String>

    @Test
    fun `recommend 성공 - 정렬된 recommendation 반환`() {
        given(recommendService.recommend(any() ?: RecommendRequest(emptyList()))).willReturn(
            listOf(
                RecommendItem(
                    kakaoPlaceId = "k1",
                    category = Category.CAFE,
                    categoryLabel = "커피전문점",
                    distanceMeter = null,
                    name = "행궁카페",
                    categoryName = "카페 > 커피전문점"
                ),
                RecommendItem(
                    kakaoPlaceId = "k2",
                    category = Category.CAFE,
                    categoryLabel = "전통찻집",
                    distanceMeter = 350,
                    name = "행궁전통찻집",
                    categoryName = "카페 > 전통찻집"
                )
            )
        )
        mockMvc.perform(
            post("/api/v1/restaurants/recommend")
                .contentType(MediaType.APPLICATION_JSON).content(
                    """
                    {
                      "candidates": [
                        {
                          "kakaoPlaceId": "k1",
                          "name": "행궁카페",
                          "categoryName": "카페 > 커피전문점",
                          "address": "수원시 팔달구 행궁동 1-1",
                          "lat": 37.2826,
                          "lng": 127.0135
                        }
                      ],
                      "category": "CAFE",
                      "sort": "RANDOM"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recommendations").isArray)
            .andExpect(
                jsonPath("$.data.recommendations[0].kakaoPlaceId")
                    .value("k1")
            )
            .andExpect(
                jsonPath("$.data.recommendations[0].categoryLabel")
                    .value("커피전문점")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("식당 추천이 완료되었습니다.")
            )
    }

    @Test
    fun `recommned 잘못된 sort 값이면 400`() {
        mockMvc.perform(
            post("/api/v1/restaurants/recommend")
                .contentType(MediaType.APPLICATION_JSON).content(
                    """
                    {
                      "candidates": [
                        {
                          "kakaoPlaceId": "k1",
                          "name": "식당",
                          "categoryName": "음식점 > 한식",
                          "address": "주소",
                          "lat": 37.5,
                          "lng": 127.0
                        }
                      ],
                      "sort": "POPULAR"
                    }
                    """.trimIndent()

                )
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `recommend 결과 없으면 404`() {
        given(recommendService.recommend(any() ?: RecommendRequest(emptyList())))
            .willThrow(RestaurantNotFoundException("조건에 맞는 식당이 없습니다."))

        mockMvc.perform(
            post("/api/v1/restaurants/recommend")
                .contentType(MediaType.APPLICATION_JSON).content(
                    """
                    {
                      "candidates": [
                        {
                          "kakaoPlaceId": "k1",
                          "name": "식당",
                          "categoryName": "음식점 > 한식",
                          "address": "주소",
                          "lat": 37.5,
                          "lng": 127.0
                        }
                      ],
                      "sort": "RANDOM"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isNotFound)
            .andExpect(
                jsonPath("$.message")
                    .value("조건에 맞는 식당이 없습니다.")
            )
    }
}
