package com.whattoeat.domain.restaurantlist.service

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurantlist.dto.SavedRestaurantListResponse
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.restaurantlist.entity.SavedRestaurantList
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListRepository
import com.whattoeat.domain.restaurantlist.repository.SavedRestaurantListRepository
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.AlreadySavedRestaurantListException
import com.whattoeat.global.exception.ListNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.BDDMockito.then
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SavedRestaurantListServiceTest {

    @Mock
    lateinit var savedRestaurantListRepository: SavedRestaurantListRepository

    @Mock
    lateinit var restaurantListRepository: RestaurantListRepository

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var notificationRepository: NotificationRepository

    @InjectMocks
    lateinit var savedRestaurantListService: SavedRestaurantListService

    @Test
    fun `save 성공`() {
        val userId = 1L
        val restaurantListId = 10L

        val user = mock(User::class.java)
        val owner = mock(User::class.java)
        val restaurantList = mock(RestaurantList::class.java)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        given(restaurantListRepository.findById(restaurantListId))
            .willReturn(Optional.of(restaurantList))

        given(restaurantList.user)
            .willReturn(owner)

        given(owner.id)
            .willReturn(2L)

        given(
            savedRestaurantListRepository.existsByUserIdAndRestaurantListId(
                userId,
                restaurantListId
            )
        ).willReturn(false)

        savedRestaurantListService.save(userId, restaurantListId)

        then(savedRestaurantListRepository)
            .should()
            .save(any(SavedRestaurantList::class.java))
    }

    @Test
    fun `save 이미 저장한 리스트면 예외`() {
        val userId = 1L
        val restaurantListId = 10L

        val user = mock(User::class.java)
        val owner = mock(User::class.java)
        val restaurantList = mock(RestaurantList::class.java)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        given(restaurantListRepository.findById(restaurantListId))
            .willReturn(Optional.of(restaurantList))

        given(restaurantList.user)
            .willReturn(owner)

        given(owner.id)
            .willReturn(2L)

        given(
            savedRestaurantListRepository.existsByUserIdAndRestaurantListId(
                userId,
                restaurantListId
            )
        ).willReturn(true)

        assertThatThrownBy {
            savedRestaurantListService.save(userId, restaurantListId)
        }
            .isInstanceOf(AlreadySavedRestaurantListException::class.java)
            .hasMessage("이미 저장한 리스트입니다.")

        then(savedRestaurantListRepository)
            .should(never())
            .save(any(SavedRestaurantList::class.java))
    }

    @Test
    fun `save 사용자가 없으면 예외`() {
        val userId = 1L
        val restaurantListId = 10L

        given(userRepository.findById(userId))
            .willReturn(Optional.empty())

        assertThatThrownBy {
            savedRestaurantListService.save(userId, restaurantListId)
        }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessage("User not found: 1")

        then(restaurantListRepository)
            .shouldHaveNoInteractions()

        then(savedRestaurantListRepository)
            .should(never())
            .save(any(SavedRestaurantList::class.java))
    }

    @Test
    fun `save 레스토랑 리스트가 없으면 예외`() {
        val userId = 1L
        val restaurantListId = 10L

        val user = mock(User::class.java)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        given(restaurantListRepository.findById(restaurantListId))
            .willReturn(Optional.empty())

        assertThatThrownBy {
            savedRestaurantListService.save(userId, restaurantListId)
        }
            .isInstanceOf(ListNotFoundException::class.java)
            .hasMessage("리스트를 찾을 수 없습니다: 10")

        then(savedRestaurantListRepository)
            .should(never())
            .save(any(SavedRestaurantList::class.java))
    }

    @Test
    fun `unsave 성공`() {
        val userId = 1L
        val restaurantListId = 10L
        val ownerId = 2L

        val savedRestaurantList = mock(SavedRestaurantList::class.java)
        val restaurantList = mock(RestaurantList::class.java)
        val owner = mock(User::class.java)

        given(
            savedRestaurantListRepository.findByUserIdAndRestaurantListId(
                userId,
                restaurantListId
            )
        ).willReturn(Optional.of(savedRestaurantList))

        given(savedRestaurantList.restaurantList).willReturn(restaurantList)
        given(restaurantList.user).willReturn(owner)
        given(owner.id).willReturn(ownerId)

        savedRestaurantListService.unsave(userId, restaurantListId)

        then(savedRestaurantListRepository)
            .should()
            .delete(savedRestaurantList)

        then(notificationRepository)
            .should()
            .deleteByReceiverIdAndActorIdAndRestaurantListIdAndType(
                ownerId, userId, restaurantListId, NotificationType.LIST_SHARE
            )
    }

    @Test
    fun `unsave 저장한 리스트가 아니면 예외`() {
        val userId = 1L
        val restaurantListId = 10L

        given(
            savedRestaurantListRepository.findByUserIdAndRestaurantListId(
                userId,
                restaurantListId
            )
        ).willReturn(Optional.empty())

        assertThatThrownBy {
            savedRestaurantListService.unsave(userId, restaurantListId)
        }
            .isInstanceOf(ListNotFoundException::class.java)
            .hasMessage("리스트를 찾을 수 없습니다: 10")

        then(savedRestaurantListRepository)
            .should(never())
            .delete(any(SavedRestaurantList::class.java))
    }

    @Test
    fun `isSaved 저장되어 있으면 true`() {
        given(
            savedRestaurantListRepository.existsByUserIdAndRestaurantListId(
                1L,
                10L
            )
        ).willReturn(true)

        assertThat(
            savedRestaurantListService.isSaved(1L, 10L)
        ).isTrue()
    }

    @Test
    fun `isSaved 저장되어 있지 않으면 false`() {
        given(
            savedRestaurantListRepository.existsByUserIdAndRestaurantListId(
                1L,
                10L
            )
        ).willReturn(false)

        assertThat(
            savedRestaurantListService.isSaved(1L, 10L)
        ).isFalse()
    }

    @Test
    fun `findMySavedLists 성공`() {
        val userId = 1L
        val pageable = PageRequest.of(0, 10)

        val owner = mock(User::class.java)
        val restaurantList = mock(RestaurantList::class.java)
        val savedRestaurantList = mock(SavedRestaurantList::class.java)

        val page = PageImpl(
            listOf(savedRestaurantList),
            pageable,
            1
        )

        given(
            savedRestaurantListRepository.findByUserId(
                userId,
                pageable
            )
        ).willReturn(page)

        given(savedRestaurantList.restaurantList)
            .willReturn(restaurantList)

        given(savedRestaurantList.createdAt)
            .willReturn(null)

        given(restaurantList.id)
            .willReturn(10L)

        given(restaurantList.user)
            .willReturn(owner)

        given(restaurantList.title)
            .willReturn("혼밥 맛집")

        given(restaurantList.description)
            .willReturn("혼자 먹기 좋은 곳")

        given(restaurantList.moodTag)
            .willReturn(MoodTag.SOLO)

        given(restaurantList.items)
            .willReturn(mutableListOf<RestaurantListItem>())

        given(owner.id)
            .willReturn(2L)

        given(owner.nickname)
            .willReturn("작성자")

        val result =
            savedRestaurantListService.findMySavedLists(
                userId,
                pageable
            )

        assertThat(result.content).hasSize(1)

        val response: SavedRestaurantListResponse =
            result.content[0]

        assertThat(response.listId).isEqualTo(10L)
        assertThat(response.userId).isEqualTo(2L)
        assertThat(response.nickname).isEqualTo("작성자")
        assertThat(response.title).isEqualTo("혼밥 맛집")
        assertThat(response.description).isEqualTo("혼자 먹기 좋은 곳")
        assertThat(response.moodTag).isEqualTo(MoodTag.SOLO)
        assertThat(response.items).isEmpty()
        assertThat(response.savedAt).isNull()
    }
}