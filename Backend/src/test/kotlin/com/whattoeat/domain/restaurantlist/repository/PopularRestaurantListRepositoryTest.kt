package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.restaurantlist.entity.SavedRestaurantList
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime

@DataJpaTest
class PopularRestaurantListRepositoryTest {
    @Autowired
    lateinit var savedRestaurantListRepository: SavedRestaurantListRepository

    @Autowired
    lateinit var restaurantListRepository: RestaurantListRepository

    @Autowired
    lateinit var restaurantListItemRepository: RestaurantListItemRepository

    @Autowired
    lateinit var restaurantRepository: RestaurantRepository

    @Autowired
    lateinit var userRepository: UserRepository

    private var sequence = 0

    @Test
    fun `저장 수 생성 시각 ID 순으로 인기 리스트를 정렬한다`() {
        val viewer = createUser("viewer")
        val owner = createUser("owner")
        val baseTime = LocalDateTime.of(2026, 7, 31, 12, 0)

        val mostSaved = createListWithItem(
            owner,
            "저장 3개",
            baseTime.minusDays(2)
        )

        val olderTie = createListWithItem(
            owner,
            "저장 2개 오래된 리스트",
            baseTime.minusDays(1)
        )

        val newerTieLowId = createListWithItem(
            owner,
            "저장 2개 최신 리스트 1",
            baseTime
        )

        val newerTieHighId = createListWithItem(
            owner,
            "저장 2개 최신 리스트 2",
            baseTime
        )

        saveByUsers(mostSaved, 3)
        saveByUsers(olderTie, 2)
        saveByUsers(newerTieLowId, 2)
        saveByUsers(newerTieHighId, 2)

        val result = savedRestaurantListRepository.findPopularLists(
            viewer.id!!,
            PageRequest.of(0, 5)
        )

        assertThat(result.map { it.id }).containsExactly(
            mostSaved.id,
            newerTieHighId.id,
            newerTieLowId.id,
            olderTie.id
        )

        assertThat(result.map { it.saveCount }).containsExactly(
            3L,
            2L,
            2L,
            2L
        )
    }

    @Test
    fun `본인 리스트와 식당 없는 리스트와 저장 없는 리스트를 제외한다`() {
        val viewer = createUser("viewer")
        val owner = createUser("owner")
        val saver = createUser("saver")

        val viewerList = createListWithItem(
            viewer,
            "내 리스트"
        )

        savedRestaurantListRepository.saveAndFlush(
            SavedRestaurantList(
                saver,
                viewerList
            )
        )

        val emptyList = createList(
            owner,
            "식당 없는 리스트"
        )

        savedRestaurantListRepository.saveAndFlush(
            SavedRestaurantList(
                viewer,
                emptyList
            )
        )

        createListWithItem(
            owner,
            "저장 없는 리스트"
        )

        val validList = createListWithItem(
            owner,
            "정상 인기 리스트"
        )

        // 이미 저장한 리스트도 인기 목록에 계속 노출한다.
        savedRestaurantListRepository.saveAndFlush(
            SavedRestaurantList(
                viewer,
                validList
            )
        )

        val result = savedRestaurantListRepository.findPopularLists(
            viewer.id!!,
            PageRequest.of(0, 5)
        )

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(validList.id)
        assertThat(result.first().saveCount).isEqualTo(1L)
        assertThat(result.first().itemCount).isEqualTo(1L)
    }

    @Test
    fun `저장 취소 후 인기 순위가 변경된다`() {
        val viewer = createUser("viewer")
        val owner = createUser("owner")
        val baseTime = LocalDateTime.of(2026, 7, 31, 12, 0)

        val olderList = createListWithItem(
            owner,
            "기존 1위",
            baseTime.minusDays(1)
        )

        val newerList = createListWithItem(
            owner,
            "신규 리스트",
            baseTime
        )

        val olderSaves = saveByUsers(olderList, 2)
        saveByUsers(newerList, 1)

        val beforeUnsave = savedRestaurantListRepository.findPopularLists(
            viewer.id!!,
            PageRequest.of(0, 5)
        )

        assertThat(beforeUnsave.map { it.id }).containsExactly(
            olderList.id,
            newerList.id
        )

        savedRestaurantListRepository.delete(olderSaves.first())
        savedRestaurantListRepository.flush()

        val afterUnsave = savedRestaurantListRepository.findPopularLists(
            viewer.id!!,
            PageRequest.of(0, 5)
        )

        assertThat(afterUnsave.map { it.id }).containsExactly(
            newerList.id,
            olderList.id
        )
    }

    @Test
    fun `전체 후보 중 상위 5개만 반환하고 동일 작성자 리스트도 허용한다`() {
        val viewer = createUser("viewer")
        val owner = createUser("owner")
        val baseTime = LocalDateTime.of(2026, 7, 31, 12, 0)

        val lists = (1..6).map { saveCount ->
            createListWithItem(
                owner,
                "리스트 $saveCount",
                baseTime.plusMinutes(saveCount.toLong())
            ).also { restaurantList ->
                saveByUsers(
                    restaurantList,
                    saveCount
                )
            }
        }

        val result = savedRestaurantListRepository.findPopularLists(
            viewer.id!!,
            PageRequest.of(0, 5)
        )

        assertThat(result).hasSize(5)

        assertThat(result.map { it.id }).containsExactly(
            lists[5].id,
            lists[4].id,
            lists[3].id,
            lists[2].id,
            lists[1].id
        )

        assertThat(result.map { it.saveCount }).containsExactly(
            6L,
            5L,
            4L,
            3L,
            2L
        )
    }

    @Test
    fun `원본 리스트가 삭제되면 인기 목록에서 제외된다`() {
        val viewer = createUser("viewer")
        val owner = createUser("owner")

        val restaurantList = createListWithItem(
            owner,
            "삭제할 리스트"
        )

        saveByUsers(restaurantList, 2)

        savedRestaurantListRepository
            .deleteAllByRestaurantListId(restaurantList.id!!)

        restaurantListRepository.delete(restaurantList)
        restaurantListRepository.flush()

        val result = savedRestaurantListRepository.findPopularLists(
            viewer.id!!,
            PageRequest.of(0, 5)
        )

        assertThat(result).isEmpty()
    }

    private fun createUser(prefix: String): User {
        sequence += 1

        return userRepository.saveAndFlush(
            User(
                nickname = "$prefix-$sequence",
                email = "$prefix-$sequence@test.com",
                provider = Provider.LOCAL
            )
        )
    }

    private fun createList(
        owner: User,
        title: String,
        createdAt: LocalDateTime = LocalDateTime.of(
            2026,
            7,
            31,
            12,
            0
        )
    ): RestaurantList {
        val restaurantList = RestaurantList(
            owner,
            title,
            "$title 설명",
            MoodTag.SOLO
        )

        ReflectionTestUtils.setField(
            restaurantList,
            "createdAt",
            createdAt
        )

        return restaurantListRepository.saveAndFlush(
            restaurantList
        )
    }

    private fun createListWithItem(
        owner: User,
        title: String,
        createdAt: LocalDateTime = LocalDateTime.of(
            2026,
            7,
            31,
            12,
            0
        )
    ): RestaurantList {
        val restaurantList = createList(
            owner,
            title,
            createdAt
        )

        sequence += 1

        val restaurant = restaurantRepository.saveAndFlush(
            Restaurant(
                kakaoPlaceId = "place-$sequence",
                name = "식당 $sequence",
                category = Category.KOREAN,
                address = "서울시 강남구",
                roadAddress = "서울시 강남구 테헤란로",
                region1 = "서울",
                region2 = "강남구",
                region3 = null,
                region4 = null,
                phone = null,
                lat = 37.0,
                lng = 127.0
            )
        )

        val restaurantListItem = RestaurantListItem(
            restaurantList,
            restaurant,
            null,
            1
        )
        restaurantList.items.add(restaurantListItem)
        restaurantListItemRepository.saveAndFlush(
            restaurantListItem
        )
        return restaurantList
    }

    private fun saveByUsers(
        restaurantList: RestaurantList,
        count: Int
    ): List<SavedRestaurantList> {
        val saves = (1..count).map {
            SavedRestaurantList(
                createUser("saver"),
                restaurantList
            )
        }

        return savedRestaurantListRepository.saveAllAndFlush(
            saves
        )
    }
}
