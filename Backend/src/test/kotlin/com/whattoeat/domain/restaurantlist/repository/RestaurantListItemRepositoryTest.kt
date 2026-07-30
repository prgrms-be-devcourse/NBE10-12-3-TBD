package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.RestaurantListItem
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.config.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.util.ReflectionTestUtils

@DataJpaTest
@Import(JpaConfig::class)
class RestaurantListItemRepositoryTest {

    @Autowired
    lateinit var restaurantListItemRepository: RestaurantListItemRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    private fun createAndSaveUser(
        loginId: String,
        nickname: String,
        email: String
    ): User {
        val user = User.builder()
            .loginId(loginId)
            .nickname(nickname)
            .email(email)
            .provider(Provider.LOCAL)
            .role(Role.USER)
            .build()

        return entityManager.persistAndFlush(user)
    }

    private fun createAndSaveRestaurant(
        name: String
    ): Restaurant {
        val constructor = Restaurant::class.java.getDeclaredConstructor()
        constructor.isAccessible = true

        val restaurant = constructor.newInstance()

        ReflectionTestUtils.setField(
            restaurant,
            "name",
            name
        )
        ReflectionTestUtils.setField(
            restaurant,
            "category",
            Category.JAPANESE
        )
        ReflectionTestUtils.setField(
            restaurant,
            "address",
            "서울시 강남구 역삼동"
        )
        ReflectionTestUtils.setField(
            restaurant,
            "roadAddress",
            "서울시 강남구 테헤란로 123"
        )
        ReflectionTestUtils.setField(
            restaurant,
            "phone",
            "02-0000-0000"
        )
        ReflectionTestUtils.setField(
            restaurant,
            "region1",
            "서울특별시"
        )
        ReflectionTestUtils.setField(
            restaurant,
            "region2",
            "강남구"
        )
        ReflectionTestUtils.setField(
            restaurant,
            "region3",
            "역삼동"
        )
        ReflectionTestUtils.setField(
            restaurant,
            "lat",
            37.5665
        )
        ReflectionTestUtils.setField(
            restaurant,
            "lng",
            126.9780
        )
        ReflectionTestUtils.setField(
            restaurant,
            "kakaoPlaceId",
            "kakao-place-1"
        )

        return entityManager.persistAndFlush(restaurant)
    }

    private fun createAndSaveRestaurantList(
        user: User,
        title: String
    ): RestaurantList {
        val restaurantList = RestaurantList(
            user,
            title,
            "설명",
            MoodTag.DATE
        )

        return entityManager.persistAndFlush(restaurantList)
    }

    private fun createAndSaveItem(
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

        return entityManager.persistAndFlush(item)
    }

    @Test
    fun `findListItem 성공`() {
        val user = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )

        val restaurantList = createAndSaveRestaurantList(
            user,
            "맛집 리스트"
        )
        val restaurant = createAndSaveRestaurant("초밥집")

        val item = createAndSaveItem(
            restaurantList,
            restaurant,
            "한줄평",
            1
        )

        val result = restaurantListItemRepository.findListItem(
            item.id!!,
            restaurantList.id!!,
            user.id!!
        )

        assertThat(result).isPresent

        val foundItem = result.get()

        assertThat(foundItem.id).isEqualTo(item.id)
        assertThat(foundItem.restaurantList.id)
            .isEqualTo(restaurantList.id)
        assertThat(foundItem.restaurant.id)
            .isEqualTo(restaurant.id)
        assertThat(foundItem.memo).isEqualTo("한줄평")
        assertThat(foundItem.orderIndex).isEqualTo(1)
    }

    @Test
    fun `findListItem 다른 유저의 아이템이면 조회되지 않는다`() {
        val user1 = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )
        val user2 = createAndSaveUser(
            "user2",
            "nick2",
            "user2@test.com"
        )

        val restaurantList = createAndSaveRestaurantList(
            user1,
            "user1 리스트"
        )
        val restaurant = createAndSaveRestaurant("초밥집")

        val item = createAndSaveItem(
            restaurantList,
            restaurant,
            "한줄평",
            1
        )

        val result = restaurantListItemRepository.findListItem(
            item.id!!,
            restaurantList.id!!,
            user2.id!!
        )

        assertThat(result).isEmpty
    }

    @Test
    fun `findListItem 다른 리스트의 아이템이면 조회되지 않는다`() {
        val user = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )

        val list1 = createAndSaveRestaurantList(
            user,
            "리스트1"
        )
        val list2 = createAndSaveRestaurantList(
            user,
            "리스트2"
        )

        val restaurant = createAndSaveRestaurant("초밥집")

        val item = createAndSaveItem(
            list1,
            restaurant,
            "한줄평",
            1
        )

        val result = restaurantListItemRepository.findListItem(
            item.id!!,
            list2.id!!,
            user.id!!
        )

        assertThat(result).isEmpty
    }

    @Test
    fun `같은 리스트에 같은 식당은 중복 저장할 수 없다`() {
        val user = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )

        val restaurantList = createAndSaveRestaurantList(
            user,
            "맛집 리스트"
        )
        val restaurant = createAndSaveRestaurant("초밥집")

        createAndSaveItem(
            restaurantList,
            restaurant,
            "첫 번째 한줄평",
            1
        )

        val duplicate = RestaurantListItem(
            restaurantList,
            restaurant,
            "중복 한줄평",
            2
        )

        assertThatThrownBy {
            entityManager.persistAndFlush(duplicate)
        }.isInstanceOf(ConstraintViolationException::class.java)
    }

    @Test
    fun `save 시 createdAt이 자동 설정된다`() {
        val user = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )

        val restaurantList = createAndSaveRestaurantList(
            user,
            "맛집 리스트"
        )
        val restaurant = createAndSaveRestaurant("초밥집")

        val item = createAndSaveItem(
            restaurantList,
            restaurant,
            "한줄평",
            1
        )

        assertThat(item.createdAt).isNotNull()
    }
}