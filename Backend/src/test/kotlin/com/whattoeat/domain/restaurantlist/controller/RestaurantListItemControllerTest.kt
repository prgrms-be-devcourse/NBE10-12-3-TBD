package com.whattoeat.domain.restaurantlist.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurantlist.dto.RestaurantListRequest
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.restaurantlist.service.RestaurantListService
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.GlobalExceptionHandler
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.security.CustomUserDetailsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willDoNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.core.MethodParameter
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [RestaurantListController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantListItemControllerTest {

    companion object {
        private const val TEST_USER_ID = 1L
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    private val objectMapper: ObjectMapper =
        ObjectMapper().registerModule(JavaTimeModule())

    @MockitoBean
    lateinit var restaurantListService: RestaurantListService

    @MockitoBean
    lateinit var jwtUtil: JwtUtil

    @MockitoBean
    lateinit var customUserDetailsService: CustomUserDetailsService

    @MockitoBean
    lateinit var redisTemplate: RedisTemplate<String, String>

    private lateinit var userDetails: CustomUserDetails

    @BeforeEach
    fun setUp() {
        restaurantListService = mock(RestaurantListService::class.java)
        userDetails = mock(CustomUserDetails::class.java)

        given(userDetails.userId)
            .willReturn(TEST_USER_ID)

        val controller =
            RestaurantListController(restaurantListService)

        val authenticationPrincipalResolver =
            object : HandlerMethodArgumentResolver {

                override fun supportsParameter(
                    parameter: MethodParameter
                ): Boolean {
                    return parameter.hasParameterAnnotation(
                        AuthenticationPrincipal::class.java
                    )
                }

                override fun resolveArgument(
                    parameter: MethodParameter,
                    mavContainer: ModelAndViewContainer?,
                    webRequest: NativeWebRequest,
                    binderFactory: WebDataBinderFactory?
                ): Any {
                    return userDetails
                }
            }

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticationPrincipalResolver)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun authenticationToken(): UsernamePasswordAuthenticationToken {
        return UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            emptyList<GrantedAuthority>()
        )
    }

    private fun mockRestaurant(
        id: Long,
        name: String,
        category: Category
    ): Restaurant {
        val restaurant = mock(Restaurant::class.java)

        given(restaurant.id).willReturn(id)
        given(restaurant.name).willReturn(name)
        given(restaurant.category).willReturn(category)

        given(restaurant.address).willReturn("서울 강남구")
        given(restaurant.roadAddress).willReturn("서울 강남구 테헤란로 1")
        given(restaurant.lat).willReturn(37.123)
        given(restaurant.lng).willReturn(127.123)

        return restaurant
    }

    private fun createRestaurantList(id: Long): RestaurantList {
        val user = mock(User::class.java)

        val restaurantList = RestaurantList(
            user,
            "데이트 맛집",
            "분위기 좋은 곳",
            MoodTag.DATE
        )

        ReflectionTestUtils.setField(
            restaurantList,
            "id",
            id
        )

        ReflectionTestUtils.setField(
            restaurantList,
            "createdAt",
            LocalDateTime.now()
        )

        return restaurantList
    }

    private fun createRestaurantListItem(
        id: Long,
        restaurantList: RestaurantList,
        restaurant: Restaurant,
        memo: String,
        orderIndex: Int
    ): RestaurantListItem {
        val item = RestaurantListItem(
            restaurantList,
            restaurant,
            memo,
            orderIndex
        )

        ReflectionTestUtils.setField(
            item,
            "id",
            id
        )

        ReflectionTestUtils.setField(
            item,
            "createdAt",
            LocalDateTime.now()
        )

        return item
    }

    @Test
    fun `맛집리스트 아이템 추가 성공`() {
        val request = RestaurantListRequest.RestaurantListItem(
            10L,
            "한줄평",
            1
        )

        val restaurant = mockRestaurant(
            id = 10L,
            name = "초밥집",
            category = Category.JAPANESE
        )

        val restaurantList = createRestaurantList(1L)

        val item = createRestaurantListItem(
            id = 100L,
            restaurantList = restaurantList,
            restaurant = restaurant,
            memo = "한줄평",
            orderIndex = 1
        )

        given(
            restaurantListService.addItem(
                1L,
                1L,
                10L,
                "한줄평",
                1
            )
        ).willReturn(item)

        mockMvc.perform(
            post("/api/v1/lists/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100))
            .andExpect(jsonPath("$.data.listId").value(1))
            .andExpect(jsonPath("$.data.restaurantId").value(10))
            .andExpect(
                jsonPath("$.data.restaurantName")
                    .value("초밥집")
            )
            .andExpect(
                jsonPath("$.data.category")
                    .value("JAPANESE")
            )
            .andExpect(
                jsonPath("$.data.orderIndex")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.data.memo")
                    .value("한줄평")
            )
            .andExpect(
                jsonPath("$.data.createdAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$.message")
                    .value("맛집 리스트에 식당이 추가되었습니다.")
            )
    }

    @Test
    fun `맛집리스트 아이템 수정 성공`() {
        val request = RestaurantListRequest.RestaurantListItem(
            10L,
            "수정된 한줄평",
            2
        )

        val restaurant = mockRestaurant(
            id = 10L,
            name = "초밥집",
            category = Category.JAPANESE
        )

        val restaurantList = createRestaurantList(1L)

        val item = createRestaurantListItem(
            id = 100L,
            restaurantList = restaurantList,
            restaurant = restaurant,
            memo = "수정된 한줄평",
            orderIndex = 2
        )

        given(
            restaurantListService.updateItem(
                TEST_USER_ID,
                100L,
                1L,
                2,
                "수정된 한줄평"
            )
        ).willReturn(item)

        mockMvc.perform(
            put("/api/v1/lists/1/items/100")
                .with(authentication(authenticationToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100))
            .andExpect(jsonPath("$.data.listId").value(1))
            .andExpect(jsonPath("$.data.restaurantId").value(10))
            .andExpect(
                jsonPath("$.data.restaurantName")
                    .value("초밥집")
            )
            .andExpect(
                jsonPath("$.data.category")
                    .value("JAPANESE")
            )
            .andExpect(
                jsonPath("$.data.orderIndex")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.data.memo")
                    .value("수정된 한줄평")
            )
            .andExpect(
                jsonPath("$.data.createdAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$.message")
                    .value("리스트 아이템 정보가 변경되었습니다.")
            )

        verify(restaurantListService).updateItem(
            TEST_USER_ID,
            100L,
            1L,
            2,
            "수정된 한줄평"
        )
    }

    @Test
    fun `맛집리스트 아이템 삭제 성공`() {
        val listId = 1L
        val itemId = 100L
        val userId = 1L

        willDoNothing()
            .given(restaurantListService)
            .deleteItem(
                listId,
                itemId,
                userId
            )

        mockMvc.perform(
            delete(
                "/api/v1/lists/{id}/items/{itemId}",
                listId,
                itemId
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        then(restaurantListService)
            .should()
            .deleteItem(
                listId,
                itemId,
                userId
            )
    }

    @Test
    fun `맛집리스트 아이템 추가 restaurantId null이면 400`() {
        val requestJson = """
            {
                "restaurantId": null,
                "memo": "한줄평",
                "orderIndex": 1
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/lists/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `맛집리스트 아이템 추가 orderIndex null이면 맨뒤에 추가`() {
        val request = RestaurantListRequest.RestaurantListItem(
            10L,
            "한줄평",
            null
        )

        val restaurant = mockRestaurant(
            id = 10L,
            name = "초밥집",
            category = Category.JAPANESE
        )

        val restaurantList = createRestaurantList(1L)

        val item = createRestaurantListItem(
            id = 100L,
            restaurantList = restaurantList,
            restaurant = restaurant,
            memo = "한줄평",
            orderIndex = 1
        )

        given(
            restaurantListService.addItem(
                1L,
                1L,
                10L,
                "한줄평",
                null
            )
        ).willReturn(item)

        mockMvc.perform(
            post("/api/v1/lists/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100))
            .andExpect(jsonPath("$.data.listId").value(1))
            .andExpect(jsonPath("$.data.restaurantId").value(10))
            .andExpect(
                jsonPath("$.data.restaurantName")
                    .value("초밥집")
            )
            .andExpect(
                jsonPath("$.data.category")
                    .value("JAPANESE")
            )
            .andExpect(
                jsonPath("$.data.orderIndex")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.data.memo")
                    .value("한줄평")
            )
            .andExpect(
                jsonPath("$.data.createdAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$.message")
                    .value("맛집 리스트에 식당이 추가되었습니다.")
            )
    }
}