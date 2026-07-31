package com.whattoeat.domain.restaurantlist.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurantlist.dto.RestaurantListRequest
import com.whattoeat.domain.restaurantlist.dto.RestaurantListResponse
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.restaurantlist.service.RestaurantListService
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.jwt.JwtUtil
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.security.CustomUserDetailsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete

@WebMvcTest(
    controllers = [RestaurantListController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
    properties = [
        "app.upload.path=uploads",
        "app.upload.url-prefix=/uploads"
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantListControllerTest {

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
        userDetails = mock(CustomUserDetails::class.java)

        given(userDetails.userId)
            .willReturn(TEST_USER_ID)

        val authentication = UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            emptyList()
        )

        SecurityContextHolder.getContext().authentication = authentication
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun mockUser(
        id: Long,
        nickname: String
    ): User {
        val user = mock(User::class.java)

        given(user.id).willReturn(id)
        given(user.nickname).willReturn(nickname)

        return user
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

    private fun createRestaurantList(
        id: Long,
        user: User,
        title: String,
        description: String,
        moodTag: MoodTag
    ): RestaurantList {
        val restaurantList = RestaurantList(
            user,
            title,
            description,
            moodTag
        )

        ReflectionTestUtils.setField(restaurantList, "id", id)
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
        memo: String?,
        orderIndex: Int
    ): RestaurantListItem {
        val item = RestaurantListItem(
            restaurantList,
            restaurant,
            memo,
            orderIndex
        )

        ReflectionTestUtils.setField(item, "id", id)
        ReflectionTestUtils.setField(
            item,
            "createdAt",
            LocalDateTime.now()
        )

        return item
    }

    @Test
    fun `맛집리스트 생성 성공`() {
        val request = RestaurantListRequest.RestaurantList(
            "데이트 맛집",
            "분위기 좋은 곳",
            MoodTag.DATE
        )

        val user = mockUser(
            id = 1L,
            nickname = "user1"
        )

        val restaurantList = createRestaurantList(
            id = 1L,
            user = user,
            title = "데이트 맛집",
            description = "분위기 좋은 곳",
            moodTag = MoodTag.DATE
        )

        given(
            restaurantListService.create(
                1L,
                "데이트 맛집",
                "분위기 좋은 곳",
                MoodTag.DATE
            )
        ).willReturn(restaurantList)

        mockMvc.perform(
            post("/api/v1/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.nickname").value("user1"))
            .andExpect(jsonPath("$.data.title").value("데이트 맛집"))
            .andExpect(jsonPath("$.data.description").value("분위기 좋은 곳"))
            .andExpect(jsonPath("$.data.moodTag").value("DATE"))
            .andExpect(jsonPath("$.data.itemCount").value(0))
            .andExpect(jsonPath("$.data.createdAt").exists())
            .andExpect(
                jsonPath("$.message")
                    .value("맛집 리스트가 생성되었습니다.")
            )
    }

    @Test
    fun `맛집리스트 생성 시 title이 blank면 400`() {
        val request = RestaurantListRequest.RestaurantList(
            "",
            "설명",
            MoodTag.DATE
        )

        mockMvc.perform(
            post("/api/v1/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `내 맛집리스트 다건조회 성공`() {
        val user = mockUser(
            id = 1L,
            nickname = "user1"
        )

        val list1 = createRestaurantList(
            id = 1L,
            user = user,
            title = "데이트 맛집",
            description = "분위기 좋은 곳",
            moodTag = MoodTag.DATE
        )

        val list2 = createRestaurantList(
            id = 2L,
            user = user,
            title = "혼밥 맛집",
            description = "혼자 먹기 좋은 곳",
            moodTag = MoodTag.SOLO
        )

        val pageable = PageRequest.of(0, 10)

        given(
            restaurantListService.findAllByUserId(
                1L,
                pageable
            )
        ).willReturn(
            PageImpl(
                listOf(list2, list1),
                pageable,
                2L
            )
        )

        mockMvc.perform(
            get("/api/v1/lists")
                .param("page", "0")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.lists.length()").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.lists[0].id").value(2))
            .andExpect(jsonPath("$.data.lists[0].userId").value(1))
            .andExpect(jsonPath("$.data.lists[0].nickname").value("user1"))
            .andExpect(jsonPath("$.data.lists[0].title").value("혼밥 맛집"))
            .andExpect(
                jsonPath("$.data.lists[0].description")
                    .value("혼자 먹기 좋은 곳")
            )
            .andExpect(jsonPath("$.data.lists[0].moodTag").value("SOLO"))
            .andExpect(jsonPath("$.data.lists[0].itemCount").value(0))
            .andExpect(jsonPath("$.data.lists[0].createdAt").exists())
            .andExpect(jsonPath("$.data.lists[1].id").value(1))
            .andExpect(jsonPath("$.data.lists[1].title").value("데이트 맛집"))
            .andExpect(jsonPath("$.data.lists[1].moodTag").value("DATE"))
            .andExpect(jsonPath("$.data.lists[1].itemCount").value(0))
            .andExpect(jsonPath("$.data.lists[1].createdAt").exists())
            .andExpect(
                jsonPath("$.message")
                    .value("맛집 리스트 목록 조회가 완료되었습니다.")
            )
    }

    @Test
    fun `내 맛집리스트 단건조회 성공`() {
        val user = mockUser(
            id = 1L,
            nickname = "user1"
        )

        val restaurant = mockRestaurant(
            id = 10L,
            name = "초밥집",
            category = Category.JAPANESE
        )

        val restaurantList = createRestaurantList(
            id = 1L,
            user = user,
            title = "데이트 맛집",
            description = "분위기 좋은 곳",
            moodTag = MoodTag.DATE
        )

        val item = createRestaurantListItem(
            id = 100L,
            restaurantList = restaurantList,
            restaurant = restaurant,
            memo = "한줄평",
            orderIndex = 1
        )

        restaurantList.items.add(item)

        given(
            restaurantListService.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(restaurantList)

        mockMvc.perform(get("/api/v1/lists/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.listId").value(1))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.nickname").value("user1"))
            .andExpect(jsonPath("$.data.title").value("데이트 맛집"))
            .andExpect(
                jsonPath("$.data.description")
                    .value("분위기 좋은 곳")
            )
            .andExpect(jsonPath("$.data.moodTag").value("DATE"))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(100))
            .andExpect(jsonPath("$.data.items[0].listId").value(1))
            .andExpect(jsonPath("$.data.items[0].restaurantId").value(10))
            .andExpect(
                jsonPath("$.data.items[0].restaurantName")
                    .value("초밥집")
            )
            .andExpect(
                jsonPath("$.data.items[0].category")
                    .value("JAPANESE")
            )
            .andExpect(jsonPath("$.data.items[0].orderIndex").value(1))
            .andExpect(jsonPath("$.data.items[0].memo").value("한줄평"))
            .andExpect(jsonPath("$.data.items[0].createdAt").exists())
            .andExpect(jsonPath("$.data.createdAt").exists())
            .andExpect(
                jsonPath("$.message")
                    .value("맛집 리스트 조회가 완료되었습니다.")
            )
    }

    @Test
    fun `전체 맛집리스트 다건조회 성공`() {
        val user = mockUser(
            id = 1L,
            nickname = "user1"
        )

        val list1 = createRestaurantList(
            id = 1L,
            user = user,
            title = "데이트 맛집",
            description = "분위기 좋은 곳",
            moodTag = MoodTag.DATE
        )

        val list2 = createRestaurantList(
            id = 2L,
            user = user,
            title = "혼밥 맛집",
            description = "혼자 먹기 좋은 곳",
            moodTag = MoodTag.SOLO
        )

        val pageable = PageRequest.of(
            0,
            10,
            Sort.by(Sort.Direction.DESC, "createdAt")
        )

        given(
            restaurantListService.findAll(pageable)
        ).willReturn(
            PageImpl(
                listOf(list2, list1),
                pageable,
                2L
            )
        )

        mockMvc.perform(
            get("/api/v1/lists/all")
                .param("page", "0")
                .param("size", "10")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            // 나머지 검증 계속
            .andExpect(jsonPath("$.data.lists.length()").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(2))

            .andExpect(jsonPath("$.data.lists[0].id").value(2))
            .andExpect(jsonPath("$.data.lists[0].userId").value(1))
            .andExpect(jsonPath("$.data.lists[0].nickname").value("user1"))
            .andExpect(jsonPath("$.data.lists[0].title").value("혼밥 맛집"))
            .andExpect(
                jsonPath("$.data.lists[0].description")
                    .value("혼자 먹기 좋은 곳")
            )
            .andExpect(jsonPath("$.data.lists[0].moodTag").value("SOLO"))
            .andExpect(jsonPath("$.data.lists[0].itemCount").value(0))
            .andExpect(jsonPath("$.data.lists[0].createdAt").exists())

            .andExpect(jsonPath("$.data.lists[1].id").value(1))
            .andExpect(jsonPath("$.data.lists[1].userId").value(1))
            .andExpect(jsonPath("$.data.lists[1].nickname").value("user1"))
            .andExpect(jsonPath("$.data.lists[1].title").value("데이트 맛집"))
            .andExpect(
                jsonPath("$.data.lists[1].description")
                    .value("분위기 좋은 곳")
            )
            .andExpect(jsonPath("$.data.lists[1].moodTag").value("DATE"))
            .andExpect(jsonPath("$.data.lists[1].itemCount").value(0))
            .andExpect(jsonPath("$.data.lists[1].createdAt").exists())

            .andExpect(
                jsonPath("$.message")
                    .value("전체 맛집 리스트 목록 조회가 완료되었습니다.")
            )
    }

    @Test
    fun `전체 맛집리스트 단건조회 성공`() {
        val user = mockUser(
            id = 1L,
            nickname = "user1"
        )

        val restaurantList = createRestaurantList(
            id = 1L,
            user = user,
            title = "데이트 맛집",
            description = "분위기 좋은 곳",
            moodTag = MoodTag.DATE
        )

        given(
            restaurantListService.findById(1L)
        ).willReturn(restaurantList)

        mockMvc.perform(get("/api/v1/lists/all/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.listId").value(1))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.nickname").value("user1"))
            .andExpect(jsonPath("$.data.title").value("데이트 맛집"))
            .andExpect(
                jsonPath("$.data.description")
                    .value("분위기 좋은 곳")
            )
            .andExpect(jsonPath("$.data.moodTag").value("DATE"))
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.createdAt").exists())
            .andExpect(
                jsonPath("$.message")
                    .value("전체 맛집 리스트 조회가 완료되었습니다.")
            )
    }

    @Test
    fun `맛집리스트 복사 성공`() {
        val originalListId = 1L
        val userId = 1L

        val copyUserDetails = mock(CustomUserDetails::class.java)
        given(copyUserDetails.userId).willReturn(userId)

        val user = mock(User::class.java)
        given(user.id).willReturn(userId)
        given(user.nickname).willReturn("user1")

        val copiedList = mock(RestaurantList::class.java)

        given(copiedList.id).willReturn(2L)
        given(copiedList.user).willReturn(user)
        given(copiedList.title).willReturn("혼밥 맛집")
        given(copiedList.description).willReturn("혼자 먹기 좋은 곳")
        given(copiedList.moodTag).willReturn(MoodTag.SOLO)
        given(copiedList.items)
            .willReturn(mutableListOf<RestaurantListItem>())
        given(copiedList.createdAt)
            .willReturn(LocalDateTime.of(2026, 7, 5, 10, 0))

        given(
            restaurantListService.copyList(
                userId,
                originalListId
            )
        ).willReturn(copiedList)

        mockMvc.perform(
            post("/api/v1/lists/{id}/copy", originalListId)
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            copyUserDetails,
                            null,
                            emptyList()
                        )
                    )
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("맛집 리스트가 복사되었습니다.")
            )

        verify(restaurantListService)
            .copyList(userId, originalListId)
    }

    @Test
    fun `식당 리스트 기본 정보 수정 성공`() {
        val listId = 1L
        val userId = 1L

        val restaurantList = mock(RestaurantList::class.java)
        val user = mock(User::class.java)

        given(restaurantList.id).willReturn(listId)
        given(restaurantList.user).willReturn(user)

        given(user.id).willReturn(userId)
        given(user.nickname).willReturn("푸디")

        given(restaurantList.title)
            .willReturn("수정된 리스트 제목")

        given(restaurantList.description)
            .willReturn("수정된 리스트 설명")

        given(restaurantList.moodTag)
            .willReturn(MoodTag.DATE)

        given(restaurantList.items)
            .willReturn(mutableListOf<RestaurantListItem>())

        given(
            restaurantListService.update(
                listId,
                userId,
                "수정된 리스트 제목",
                "수정된 리스트 설명",
                MoodTag.DATE
            )
        ).willReturn(restaurantList)

        val requestBody = """
            {
                "title": "수정된 리스트 제목",
                "description": "수정된 리스트 설명",
                "moodTag": "DATE"
            }
        """.trimIndent()

        mockMvc.perform(
            put("/api/v1/lists/{id}", listId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("리스트 정보가 변경되었습니다.")
            )
            .andExpect(jsonPath("$.data.listId").value(1))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.nickname").value("푸디"))
            .andExpect(
                jsonPath("$.data.title")
                    .value("수정된 리스트 제목")
            )
            .andExpect(
                jsonPath("$.data.description")
                    .value("수정된 리스트 설명")
            )
            .andExpect(jsonPath("$.data.moodTag").value("DATE"))
            .andExpect(jsonPath("$.data.items").isArray)
            .andExpect(jsonPath("$.data.items").isEmpty)

        then(restaurantListService)
            .should()
            .update(
                listId,
                userId,
                "수정된 리스트 제목",
                "수정된 리스트 설명",
                MoodTag.DATE
            )
    }

    @Test
    fun `맛집리스트 삭제 성공`() {
        val listId = 1L
        mockMvc.perform(delete("/api/v1/lists/{id}", listId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(
                jsonPath("$.message").value("맛집 리스트가 삭제되었습니다.")
            )

        then(restaurantListService).should().delete(listId,TEST_USER_ID)
    }

    @Test
    fun `가장 많이 저장된 맛집 리스트 조회 성공`() {
        val popularList =
            RestaurantListResponse.PopularRestaurantList(
                id = 10L,
                userId = 2L,
                nickname = "맛집탐험가",
                title = "서울 데이트 맛집",
                description = "분위기 좋은 식당",
                moodTag = MoodTag.DATE,
                itemCount = 8L,
                createdAt = LocalDateTime.of(
                    2026,
                    7,
                    31,
                    12,
                    0
                ),
                saveCount = 4L
            )

        given(
            restaurantListService.findPopularLists(
                TEST_USER_ID,
                5
            )
        ).willReturn(listOf(popularList))

        mockMvc.perform(
            get("/api/v1/lists/popular")
                .param("size", "5")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.lists").isArray)
            .andExpect(jsonPath("$.data.lists.length()").value(1))
            .andExpect(jsonPath("$.data.lists[0].id").value(10))
            .andExpect(jsonPath("$.data.lists[0].userId").value(2))
            .andExpect(
                jsonPath("$.data.lists[0].nickname")
                    .value("맛집탐험가")
            )
            .andExpect(
                jsonPath("$.data.lists[0].title")
                    .value("서울 데이트 맛집")
            )
            .andExpect(
                jsonPath("$.data.lists[0].itemCount")
                    .value(8)
            )
            .andExpect(
                jsonPath("$.data.lists[0].saveCount")
                    .value(4)
            )
            .andExpect(
                jsonPath("$.message")
                    .value("인기 맛집 리스트 조회가 완료되었습니다.")
            )
    }

    @Test
    fun `인기 리스트가 없으면 빈 배열을 반환한다`() {
        given(
            restaurantListService.findPopularLists(
                TEST_USER_ID,
                5
            )
        ).willReturn(emptyList())

        mockMvc.perform(
            get("/api/v1/lists/popular")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.lists").isArray)
            .andExpect(jsonPath("$.data.lists").isEmpty)
    }


    companion object {
        private const val TEST_USER_ID = 1L
    }
}