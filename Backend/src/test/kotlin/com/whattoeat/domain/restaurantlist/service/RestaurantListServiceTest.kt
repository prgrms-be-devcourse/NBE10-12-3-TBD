package com.whattoeat.domain.restaurantlist.service

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.restaurantlist.repository.PopularRestaurantListProjection
import com.whattoeat.domain.restaurantlist.repository.RestaurantListItemRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListRepository
import com.whattoeat.domain.restaurantlist.repository.SavedRestaurantListRepository
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.ListNotFoundException
import com.whattoeat.global.exception.RestaurantListItemNotFoundException
import com.whattoeat.global.exception.RestaurantNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class RestaurantListServiceTest {

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

    @InjectMocks
    lateinit var restaurantListService: RestaurantListService

    @Mock
    lateinit var savedRestaurantListRepository: SavedRestaurantListRepository

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
        name: String
    ): Restaurant {
        val restaurant = mock(Restaurant::class.java)

        given(restaurant.id).willReturn(id)
        given(restaurant.name).willReturn(name)

        return restaurant
    }

    private fun createRestaurantList(
        id: Long,
        user: User
    ): RestaurantList {
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

        return restaurantList
    }

    private fun createRestaurantListItem(
        id: Long,
        restaurantList: RestaurantList,
        restaurant: Restaurant
    ): RestaurantListItem {
        val item = RestaurantListItem(
            restaurantList,
            restaurant,
            "한줄평",
            1
        )

        ReflectionTestUtils.setField(
            item,
            "id",
            id
        )

        return item
    }

    @Test
    fun `create 성공`() {
        val user = mock(User::class.java)

        val savedRestaurantList = RestaurantList(
            user,
            "데이트 맛집",
            "분위기 좋은 곳",
            MoodTag.DATE
        )

        ReflectionTestUtils.setField(
            savedRestaurantList,
            "id",
            1L
        )

        given(
            userRepository.findById(1L)
        ).willReturn(Optional.of(user))

        given(
            restaurantListRepository.save(
                any(RestaurantList::class.java)
            )
        ).willReturn(savedRestaurantList)

        val result = restaurantListService.create(
            1L,
            "데이트 맛집",
            "분위기 좋은 곳",
            MoodTag.DATE
        )

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.title).isEqualTo("데이트 맛집")
        assertThat(result.description).isEqualTo("분위기 좋은 곳")
        assertThat(result.moodTag).isEqualTo(MoodTag.DATE)
    }

    @Test
    fun `create 유저가 없으면 예외`() {
        given(
            userRepository.findById(999L)
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.create(
                999L,
                "데이트 맛집",
                "분위기 좋은 곳",
                MoodTag.DATE
            )
        }.isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun `findAllByUserId 성공`() {
        val user = mock(User::class.java)

        val list1 = createRestaurantList(1L, user)
        val list2 = createRestaurantList(2L, user)

        val pageable = PageRequest.of(0, 10)

        given(
            restaurantListRepository.findByUserIdOrderByIdDesc(
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

        val result = restaurantListService.findAllByUserId(
            1L,
            pageable
        )

        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].id).isEqualTo(2L)
        assertThat(result.content[1].id).isEqualTo(1L)

        assertThat(result.totalElements).isEqualTo(2L)
        assertThat(result.totalPages).isEqualTo(1)
        assertThat(result.number).isEqualTo(0)
        assertThat(result.size).isEqualTo(10)
    }

    @Test
    fun `특정 사용자의 공개 맛집리스트를 페이지로 조회한다`() {
        val targetUser = mock(User::class.java)
        val restaurantList = createRestaurantList(3L, targetUser)
        val pageable = PageRequest.of(
            1,
            5,
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            )
        )
        val page = PageImpl(
            listOf(restaurantList),
            pageable,
            6L
        )

        given(
            restaurantListRepository.findByUserId(
                2L,
                pageable
            )
        ).willReturn(page)

        val result = restaurantListService.findPublicByUserId(
            2L,
            pageable
        )

        assertThat(result.content).containsExactly(restaurantList)
        assertThat(result.totalElements).isEqualTo(6L)
        assertThat(result.totalPages).isEqualTo(2)

        then(restaurantListRepository)
            .should()
            .findByUserId(2L, pageable)
    }

    @Test
    fun `findByIdAndUserId 성공`() {
        val user = mock(User::class.java)
        val restaurantList = createRestaurantList(1L, user)

        given(
            restaurantListRepository.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(Optional.of(restaurantList))

        val result = restaurantListService.findByIdAndUserId(
            1L,
            1L
        )

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.title).isEqualTo("데이트 맛집")
    }

    @Test
    fun `findByIdAndUserId 없으면 예외`() {
        given(
            restaurantListRepository.findByIdAndUserId(
                999L,
                1L
            )
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.findByIdAndUserId(
                999L,
                1L
            )
        }.isInstanceOf(ListNotFoundException::class.java)
    }

    @Test
    fun `addItem 성공`() {
        val user = mock(User::class.java)
        val restaurantList = createRestaurantList(1L, user)
        val restaurant = mockRestaurant(10L, "초밥집")

        val savedItem = createRestaurantListItem(
            id = 1L,
            restaurantList = restaurantList,
            restaurant = restaurant
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

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.restaurantList.id).isEqualTo(1L)
        assertThat(result.restaurant.id).isEqualTo(10L)
        assertThat(result.restaurant.name).isEqualTo("초밥집")
        assertThat(result.memo).isEqualTo("한줄평")
        assertThat(result.orderIndex).isEqualTo(1)
    }

    @Test
    fun `addItem 리스트가 없으면 예외`() {
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
    fun `addItem 식당이 없으면 예외`() {
        val user = mock(User::class.java)
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
        val user = mock(User::class.java)
        val restaurantList = createRestaurantList(1L, user)
        val restaurant = mock(Restaurant::class.java)

        val item = createRestaurantListItem(
            id = 1L,
            restaurantList = restaurantList,
            restaurant = restaurant
        )

        given(
            restaurantListRepository.findByIdAndUserId(
                1L,
                1L
            )
        ).willReturn(Optional.of(restaurantList))

        given(
            restaurantListItemRepository.findListItem(
                1L,
                1L,
                1L
            )
        ).willReturn(Optional.of(item))

        val result = restaurantListService.updateItem(
            1L,
            1L,
            1L,
            2,
            "수정된 한줄평"
        )

        assertThat(result.orderIndex).isEqualTo(2)
        assertThat(result.memo).isEqualTo("수정된 한줄평")
    }

    @Test
    fun `updateItem 리스트가 없으면 예외`() {
        given(
            restaurantListRepository.findByIdAndUserId(
                999L,
                1L
            )
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.updateItem(
                999L,
                1L,
                1L,
                2,
                "수정된 한줄평"
            )
        }.isInstanceOf(ListNotFoundException::class.java)
    }

    @Test
    fun `updateItem 아이템이 없으면 예외`() {
        val user = mock(User::class.java)
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
    fun `copyList 성공`() {
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

        val restaurant1 = mock(Restaurant::class.java)
        val restaurant2 = mock(Restaurant::class.java)

        val originalItem1 =
            mock(RestaurantListItem::class.java)

        val originalItem2 =
            mock(RestaurantListItem::class.java)

        given(originalItem1.restaurant)
            .willReturn(restaurant1)

        given(originalItem1.memo)
            .willReturn("맛있음")

        given(originalItem1.orderIndex)
            .willReturn(1)

        given(originalItem2.restaurant)
            .willReturn(restaurant2)

        given(originalItem2.memo)
            .willReturn("또 갈 곳")

        given(originalItem2.orderIndex)
            .willReturn(2)

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
        ).willReturn(
            listOf(originalItem1, originalItem2)
        )

        given(
            restaurantListRepository
                .findByIdWithItems(copyListId)
        ).willReturn(Optional.of(fetchedCopyList))

        val result = restaurantListService.copyList(
            userId,
            originalListId
        )

        assertThat(result).isEqualTo(fetchedCopyList)

        verify(userRepository)
            .findById(userId)

        verify(restaurantListRepository)
            .findById(originalListId)

        verify(restaurantListRepository)
            .save(any(RestaurantList::class.java))

        verify(restaurantListItemRepository)
            .findItemsByListId(originalListId)

        verify(
            restaurantListItemRepository,
            times(2)
        ).save(any(RestaurantListItem::class.java))

        verify(restaurantListItemRepository)
            .flush()

        verify(entityManager)
            .clear()

        verify(restaurantListRepository)
            .findByIdWithItems(copyListId)
    }

    @Test
    fun `copyList 원본 리스트가 없으면 예외`() {
        val userId = 1L
        val originalListId = 999L

        val user = mock(User::class.java)

        given(
            userRepository.findById(userId)
        ).willReturn(Optional.of(user))

        given(
            restaurantListRepository.findById(originalListId)
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.copyList(
                userId,
                originalListId
            )
        }.isInstanceOf(ListNotFoundException::class.java)

        verify(
            restaurantListRepository,
            never()
        ).save(any(RestaurantList::class.java))

        verify(
            restaurantListItemRepository,
            never()
        ).save(any(RestaurantListItem::class.java))
    }

    @Test
    fun `copyList 사용자가 없으면 예외`() {
        val userId = 999L
        val originalListId = 10L

        given(
            userRepository.findById(userId)
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.copyList(
                userId,
                originalListId
            )
        }.isInstanceOf(UserNotFoundException::class.java)

        verify(
            restaurantListRepository,
            never()
        ).findById(originalListId)

        verify(
            restaurantListRepository,
            never()
        ).save(any(RestaurantList::class.java))

        verify(
            restaurantListItemRepository,
            never()
        ).save(any(RestaurantListItem::class.java))
    }

    @Test
    fun `맛집리스트 기본정보 수정 성공`() {
        val listId = 1L
        val userId = 1L

        val title = "수정된 리스트 제목"
        val description = "수정된 리스트 설명"
        val moodTag = MoodTag.DATE

        val restaurantList =
            mock(RestaurantList::class.java)

        val user = mock(User::class.java)

        given(
            restaurantListRepository.findById(listId)
        ).willReturn(Optional.of(restaurantList))

        given(restaurantList.user)
            .willReturn(user)

        given(user.id)
            .willReturn(userId)

        val result = restaurantListService.update(
            listId,
            userId,
            title,
            description,
            moodTag
        )

        assertThat(result)
            .isSameAs(restaurantList)

        then(restaurantList)
            .should()
            .update(
                title,
                description,
                moodTag
            )
    }

    @Test
    fun `맛집리스트 기본정보 수정 실패 리스트없음`() {
        val listId = 1L
        val userId = 1L

        given(
            restaurantListRepository.findById(listId)
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            restaurantListService.update(
                listId,
                userId,
                "수정된 제목",
                "수정된 설명",
                MoodTag.DATE
            )
        }
            .isInstanceOf(ListNotFoundException::class.java)
            .hasMessage("리스트를 찾을 수 없습니다: 1")
    }

    @Test
    fun `맛집리스트 기본정보 수정 실패 본인리스트아님`() {
        val listId = 1L
        val requestUserId = 1L
        val ownerUserId = 2L

        val title = "수정된 제목"
        val description = "수정된 설명"
        val moodTag = MoodTag.DATE

        val restaurantList =
            mock(RestaurantList::class.java)

        val owner = mock(User::class.java)

        given(
            restaurantListRepository.findById(listId)
        ).willReturn(Optional.of(restaurantList))

        given(restaurantList.user)
            .willReturn(owner)

        given(owner.id)
            .willReturn(ownerUserId)

        assertThatThrownBy {
            restaurantListService.update(
                listId,
                requestUserId,
                title,
                description,
                moodTag
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("본인의 리스트만 수정할 수 있습니다.")

        then(restaurantList)
            .should(never())
            .update(
                title,
                description,
                moodTag
            )
    }

    @Test
    fun `맛집리스트 삭제성공`() {
        val listId = 1L
        val userId = 1L
        val user = mock(User::class.java)
        val restaurantList = createRestaurantList(listId, user)

        given(restaurantListRepository.findByIdAndUserId(listId,userId))
            .willReturn(Optional.of(restaurantList))
        given(savedRestaurantListRepository.deleteAllByRestaurantListId(listId)).willReturn(2L)

        restaurantListService.delete(listId, userId)

        then(savedRestaurantListRepository).should().deleteAllByRestaurantListId(listId)
        then(restaurantListRepository).should().delete(restaurantList)
    }

    @Test
    fun `맛집리스트가 없거나 본인 소유가 아니면 삭제 실패`(){
        val listId = 1L
        val userId = 1L

        given(restaurantListRepository.findByIdAndUserId(listId, userId))
            .willReturn(Optional.empty())
        assertThatThrownBy {restaurantListService.delete(listId, userId)}
            .isInstanceOf(ListNotFoundException::class.java).hasMessage("리스트를 찾을 수 없습니다: 1")

        then(savedRestaurantListRepository).shouldHaveNoInteractions()
        then(restaurantListRepository).should(never())
            .delete(any(RestaurantList::class.java))

    }
    @Test
    fun `인기 리스트 조회 결과를 응답 DTO로 변환한다`() {
        val requestUserId = 1L
        val createdAtValue = LocalDateTime.of(
            2026,
            7,
            31,
            12,
            0
        )

        val projection = object : PopularRestaurantListProjection {
            override val id = 10L
            override val userId = 2L
            override val nickname = "맛집탐험가"
            override val title = "서울 데이트 맛집"
            override val description = "분위기 좋은 식당"
            override val moodTag = MoodTag.DATE
            override val itemCount = 8L
            override val createdAt = createdAtValue
            override val saveCount = 4L
        }

        val pageable = PageRequest.of(0, 5)

        given(
            savedRestaurantListRepository.findPopularLists(
                requestUserId,
                pageable
            )
        ).willReturn(listOf(projection))

        val result = restaurantListService.findPopularLists(
            userId = requestUserId,
            size = 100
        )

        assertThat(result).hasSize(1)


        val popularList = result.first()

        assertThat(popularList.id).isEqualTo(10L)
        assertThat(popularList.userId).isEqualTo(2L)
        assertThat(popularList.nickname).isEqualTo("맛집탐험가")
        assertThat(popularList.title).isEqualTo("서울 데이트 맛집")
        assertThat(popularList.itemCount).isEqualTo(8L)
        assertThat(popularList.saveCount).isEqualTo(4L)
        assertThat(popularList.createdAt).isEqualTo(createdAtValue)
    }

    @Test
    fun `인기 리스트 후보가 없으면 빈 목록을 반환한다`() {
        val requestUserId = 1L
        val pageable = PageRequest.of(0, 5)

        given(
            savedRestaurantListRepository.findPopularLists(
                requestUserId,
                pageable
            )
        ).willReturn(emptyList())

        val result = restaurantListService.findPopularLists(
            userId = requestUserId,
            size = 5
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `findAll - mood가 null이면 전체 조회`() {
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl<RestaurantList>(emptyList(), pageable, 0)
        given(restaurantListRepository.findAll(pageable)).willReturn(page)

        val result = restaurantListService.findAll(null, pageable)

        assertThat(result).isEqualTo(page)
        then(restaurantListRepository).should().findAll(pageable)
    }

    @Test
    fun `findAll - mood가 있으면 moodTag로 필터링`() {
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl<RestaurantList>(emptyList(), pageable, 0)
        given(restaurantListRepository.findAllByMoodTag(MoodTag.DATE, pageable))
            .willReturn(page)

        val result = restaurantListService.findAll(MoodTag.DATE, pageable)

        assertThat(result).isEqualTo(page)
        then(restaurantListRepository).should().findAllByMoodTag(MoodTag.DATE, pageable)
    }

    @Test
    fun `findAllExceptUser - mood가 null이면 사용자 제외 전체 조회`() {
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl<RestaurantList>(emptyList(), pageable, 0)
        given(restaurantListRepository.findByUserIdNot(1L, pageable)).willReturn(page)

        val result = restaurantListService.findAllExceptUser(1L, null, pageable)

        assertThat(result).isEqualTo(page)
        then(restaurantListRepository).should().findByUserIdNot(1L, pageable)
    }

    @Test
    fun `findAllExceptUser - mood가 있으면 사용자 제외 + moodTag 필터링`() {
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl<RestaurantList>(emptyList(), pageable, 0)
        given(restaurantListRepository.findByUserIdNotAndMoodTag(1L, MoodTag.GROUP, pageable))
            .willReturn(page)

        val result = restaurantListService.findAllExceptUser(1L, MoodTag.GROUP, pageable)

        assertThat(result).isEqualTo(page)
        then(restaurantListRepository).should()
            .findByUserIdNotAndMoodTag(1L, MoodTag.GROUP, pageable)
    }
}
