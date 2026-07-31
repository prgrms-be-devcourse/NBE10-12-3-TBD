package com.whattoeat.domain.restaurantlist.service

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.restaurantlist.repository.RestaurantListItemRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListRepository
import com.whattoeat.domain.restaurantlist.repository.SavedRestaurantListRepository
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.DuplicateRestaurantListItemException
import com.whattoeat.global.exception.ListNotFoundException
import com.whattoeat.global.exception.RestaurantListItemNotFoundException
import com.whattoeat.global.exception.RestaurantNotFoundException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class RestaurantListItemServiceTest {

    @Mock
    lateinit var entityManager: EntityManager

    @Mock
    lateinit var restaurantListRepository: RestaurantListRepository

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var restaurantListItemRepository: RestaurantListItemRepository

    @Mock
    lateinit var restaurantRepository: RestaurantRepository

    @Mock
    lateinit var savedRestaurantListRepository: SavedRestaurantListRepository

    @InjectMocks
    lateinit var restaurantListService: RestaurantListService

    private fun createUser(): User {
        return mock(User::class.java)
    }

    private fun createRestaurantList(
        id: Long,
        user: User
    ): RestaurantList {
        val restaurantList = RestaurantList(
            user,
            "맛집 리스트",
            "설명",
            MoodTag.DATE
        )

        ReflectionTestUtils.setField(
            restaurantList,
            "id",
            id
        )

        return restaurantList
    }

    private fun createRestaurant(
        id: Long,
        name: String
    ): Restaurant {
        val restaurant = mock(Restaurant::class.java)

        given(restaurant.id).willReturn(id)
        given(restaurant.name).willReturn(name)

        return restaurant
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

        return item
    }

    @Test
    fun `addItem 성공`() {
        val user = createUser()
        val restaurantList = createRestaurantList(1L, user)
        val restaurant = createRestaurant(10L, "초밥집")

        val savedItem = createRestaurantListItem(
            id = 100L,
            restaurantList = restaurantList,
            restaurant = restaurant,
            memo = "한줄평",
            orderIndex = 1
        )

        given(
            restaurantListRepository.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(Optional.of(restaurantList))

        given(
            restaurantRepository.findById(10L)
        ).willReturn(Optional.of(restaurant))

        given(
            restaurantListItemRepository.save(
                any(RestaurantListItem::class.java)
            )
        ).willReturn(savedItem)

        val result = restaurantListService.addItem(
            1L,
            1L,
            10L,
            "한줄평",
            1
        )

        assertThat(result.id).isEqualTo(100L)
        assertThat(result.restaurantList.id).isEqualTo(1L)
        assertThat(result.restaurant.id).isEqualTo(10L)
        assertThat(result.restaurant.name).isEqualTo("초밥집")
        assertThat(result.memo).isEqualTo("한줄평")
        assertThat(result.orderIndex).isEqualTo(1)
    }

    @Test
    fun `addItem 리스트가 없으면 예외발생`() {
        given(
            restaurantListRepository.findByIdAndUserId(
                999L,
                1L
            )
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.addItem(
                1L,
                999L,
                10L,
                "한줄평",
                1
            )
        }.isInstanceOf(ListNotFoundException::class.java)
    }

    @Test
    fun `addItem 식당이 없으면 예외발생`() {
        val user = createUser()
        val restaurantList = createRestaurantList(1L, user)

        given(
            restaurantListRepository.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(Optional.of(restaurantList))

        given(
            restaurantRepository.findById(999L)
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.addItem(
                1L,
                1L,
                999L,
                "한줄평",
                1
            )
        }.isInstanceOf(RestaurantNotFoundException::class.java)
    }

    @Test
    fun `updateItem 성공`() {
        val user = createUser()
        val restaurantList = createRestaurantList(1L, user)
        val restaurant = mock(Restaurant::class.java)

        val item = createRestaurantListItem(
            id = 100L,
            restaurantList = restaurantList,
            restaurant = restaurant,
            memo = "기존 한줄평",
            orderIndex = 1
        )

        given(
            restaurantListRepository.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(Optional.of(restaurantList))

        given(
            restaurantListItemRepository.findListItem(
                100L,
                1L,
                1L
            )
        ).willReturn(Optional.of(item))

        val result = restaurantListService.updateItem(
            1L,
            100L,
            1L,
            2,
            "수정된 한줄평"
        )

        assertThat(result.id).isEqualTo(100L)
        assertThat(result.memo).isEqualTo("수정된 한줄평")
        assertThat(result.orderIndex).isEqualTo(2)
    }

    @Test
    fun `updateItem 리스트가 없으면 예외발생`() {
        given(
            restaurantListRepository.findByIdAndUserId(
                999L,
                1L
            )
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.updateItem(
                999L,
                100L,
                1L,
                2,
                "수정된 한줄평"
            )
        }.isInstanceOf(ListNotFoundException::class.java)
    }

    @Test
    fun `updateItem 아이템이 없으면 예외발생`() {
        val user = createUser()
        val restaurantList = createRestaurantList(1L, user)

        given(
            restaurantListRepository.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(Optional.of(restaurantList))

        given(
            restaurantListItemRepository.findListItem(
                999L,
                1L,
                1L
            )
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.updateItem(
                1L,
                999L,
                1L,
                2,
                "수정된 한줄평"
            )
        }.isInstanceOf(
            RestaurantListItemNotFoundException::class.java
        )
    }

    @Test
    fun `addItem 같은 리스트에 같은 식당이면 예외발생`() {
        val user = createUser()
        val restaurantList = createRestaurantList(1L, user)
        val restaurant = mock(Restaurant::class.java)

        given(
            restaurantListRepository.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(Optional.of(restaurantList))

        given(
            restaurantRepository.findById(10L)
        ).willReturn(Optional.of(restaurant))

        given(
            restaurantListItemRepository
                .existsByRestaurantListIdAndRestaurantId(
                    1L,
                    10L
                )
        ).willReturn(true)

        assertThatThrownBy {
            restaurantListService.addItem(
                1L,
                1L,
                10L,
                "중복 한줄평",
                2
            )
        }.isInstanceOf(
            DuplicateRestaurantListItemException::class.java
        )
    }

    @Test
    fun `copyList 성공 아이템도 복사된다`() {
        val userId = 1L
        val originalListId = 10L
        val copyListId = 20L

        val user = mock(User::class.java)

        val originalList = mock(RestaurantList::class.java)

        given(originalList.title)
            .willReturn("혼밥 맛집")

        given(originalList.description)
            .willReturn("혼자 먹기 좋은 곳")

        given(originalList.moodTag)
            .willReturn(MoodTag.SOLO)

        val savedCopyList = mock(RestaurantList::class.java)

        given(savedCopyList.id)
            .willReturn(copyListId)

        val fetchedCopyList =
            mock(RestaurantList::class.java)

        val restaurant =
            mock(Restaurant::class.java)

        val originalItem =
            mock(RestaurantListItem::class.java)

        given(originalItem.restaurant)
            .willReturn(restaurant)

        given(originalItem.memo)
            .willReturn("한줄평")

        given(originalItem.orderIndex)
            .willReturn(1)

        given(
            userRepository.findById(userId)
        ).willReturn(Optional.of(user))

        given(
            restaurantListRepository.findById(originalListId)
        ).willReturn(Optional.of(originalList))

        given(
            restaurantListRepository.save(
                any(RestaurantList::class.java)
            )
        ).willReturn(savedCopyList)

        given(
            restaurantListItemRepository
                .findItemsByListId(originalListId)
        ).willReturn(listOf(originalItem))

        given(
            restaurantListRepository
                .findByIdWithItems(copyListId)
        ).willReturn(Optional.of(fetchedCopyList))

        val itemCaptor =
            ArgumentCaptor.forClass(RestaurantListItem::class.java)

        val result = restaurantListService.copyList(
            userId,
            originalListId
        )

        assertThat(result)
            .isEqualTo(fetchedCopyList)

        verify(restaurantListItemRepository)
            .save(itemCaptor.capture())

        verify(restaurantListItemRepository)
            .flush()

        verify(entityManager)
            .clear()

        verify(restaurantListRepository)
            .findByIdWithItems(copyListId)

        val copiedItem = itemCaptor.value

        assertThat(copiedItem.restaurant)
            .isEqualTo(restaurant)

        assertThat(copiedItem.memo)
            .isEqualTo("한줄평")

        assertThat(copiedItem.orderIndex)
            .isEqualTo(1)
    }
}
