package com.whattoeat.domain.restaurantlist.controller

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurantlist.dto.SavedRestaurantListResponse
import com.whattoeat.domain.restaurantlist.service.SavedRestaurantListService
import com.whattoeat.global.security.CustomUserDetails
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willDoNothing
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.LocalDateTime

class SavedRestaurantListControllerTest {

    companion object {
        private const val TEST_USER_ID = 2L
    }

    private lateinit var mockMvc: MockMvc

    private lateinit var savedRestaurantListService: SavedRestaurantListService
    private lateinit var userDetails: CustomUserDetails

    @BeforeEach
    fun setUp() {
        savedRestaurantListService =
            mock(SavedRestaurantListService::class.java)

        userDetails =
            mock(CustomUserDetails::class.java)

        given(userDetails.userId)
            .willReturn(TEST_USER_ID)

        val controller =
            SavedRestaurantListController(savedRestaurantListService)

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
            .setCustomArgumentResolvers(
                authenticationPrincipalResolver,
                PageableHandlerMethodArgumentResolver()
            )
            .build()
    }

    @Test
    fun `save 성공`() {
        val restaurantListId = 10L

        willDoNothing()
            .given(savedRestaurantListService)
            .save(TEST_USER_ID, restaurantListId)

        mockMvc.perform(
            post(
                "/api/v1/restaurant_lists/{restaurantListId}/save",
                restaurantListId
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("레스토랑 리스트를 저장했습니다.")
            )

        then(savedRestaurantListService)
            .should()
            .save(TEST_USER_ID, restaurantListId)
    }

    @Test
    fun `unsave 성공`() {
        val restaurantListId = 10L

        willDoNothing()
            .given(savedRestaurantListService)
            .unsave(TEST_USER_ID, restaurantListId)

        mockMvc.perform(
            delete(
                "/api/v1/restaurant_lists/{restaurantListId}/save",
                restaurantListId
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("레스토랑 리스트 저장을 취소했습니다.")
            )

        then(savedRestaurantListService)
            .should()
            .unsave(TEST_USER_ID, restaurantListId)
    }

    @Test
    fun `findMySavedLists 성공`() {
        val response = SavedRestaurantListResponse(
            10L,
            2L,
            "작성자",
            "혼밥 맛집",
            "혼자 먹기 좋은 곳",
            MoodTag.SOLO,
            emptyList(),
            LocalDateTime.of(2026, 7, 4, 2, 30)
        )

        val pageable = PageRequest.of(0, 10)

        val page = PageImpl(
            listOf(response),
            pageable,
            1L
        )

        given(
            savedRestaurantListService.findMySavedLists(
                TEST_USER_ID,
                pageable
            )
        ).willReturn(page)

        mockMvc.perform(
            get("/api/v1/restaurant_lists/saved")
                .param("page", "0")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("저장한 레스토랑 리스트 조회가 완료되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.content[0].listId")
                    .value(10L)
            )
            .andExpect(
                jsonPath("$.data.content[0].userId")
                    .value(2L)
            )
            .andExpect(
                jsonPath("$.data.content[0].nickname")
                    .value("작성자")
            )
            .andExpect(
                jsonPath("$.data.content[0].title")
                    .value("혼밥 맛집")
            )
            .andExpect(
                jsonPath("$.data.content[0].description")
                    .value("혼자 먹기 좋은 곳")
            )
            .andExpect(
                jsonPath("$.data.content[0].moodTag")
                    .value("SOLO")
            )
            .andExpect(
                jsonPath("$.data.content[0].items")
                    .isArray
            )
            .andExpect(
                jsonPath("$.data.content[0].items")
                    .isEmpty
            )
            .andExpect(
                jsonPath("$.data.content[0].savedAt")
                    .value("2026-07-04T02:30:00")
            )

        then(savedRestaurantListService)
            .should()
            .findMySavedLists(
                TEST_USER_ID,
                pageable
            )
    }

    @Test
    fun `isSaved 저장되어있으면 true`() {
        val restaurantListId = 10L

        given(
            savedRestaurantListService.isSaved(
                TEST_USER_ID,
                restaurantListId
            )
        ).willReturn(true)

        mockMvc.perform(
            get(
                "/api/v1/restaurant_lists/{restaurantListId}/saved",
                restaurantListId
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.restaurantListId")
                    .value(10L)
            )
            .andExpect(
                jsonPath("$.data.saved")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value("저장한 레스토랑 리스트입니다.")
            )

        then(savedRestaurantListService)
            .should()
            .isSaved(TEST_USER_ID, restaurantListId)
    }

    @Test
    fun `isSaved 저장되어있지않으면 false`() {
        val restaurantListId = 10L

        given(
            savedRestaurantListService.isSaved(
                TEST_USER_ID,
                restaurantListId
            )
        ).willReturn(false)

        mockMvc.perform(
            get(
                "/api/v1/restaurant_lists/{restaurantListId}/saved",
                restaurantListId
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.restaurantListId")
                    .value(10L)
            )
            .andExpect(
                jsonPath("$.data.saved")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.message")
                    .value("저장하지 않은 레스토랑 리스트입니다.")
            )

        then(savedRestaurantListService)
            .should()
            .isSaved(TEST_USER_ID, restaurantListId)
    }
}