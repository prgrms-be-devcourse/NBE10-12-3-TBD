package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.config.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest

@DataJpaTest
@Import(JpaConfig::class)
class RestaurantListRepositoryTest {

    @Autowired
    lateinit var restaurantListRepository: RestaurantListRepository

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

    @Test
    fun `findByUserIdOrderByIdDesc 성공`() {
        val user = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )

        createAndSaveRestaurantList(user, "리스트1")
        createAndSaveRestaurantList(user, "리스트2")

        val result =
            restaurantListRepository.findByUserIdOrderByIdDesc(
                user.id!!,
                PageRequest.of(0, 10)
            )

        assertThat(result.content).hasSize(2)

        assertThat(result.content)
            .extracting("title")
            .containsExactly(
                "리스트2",
                "리스트1"
            )

        assertThat(result.totalElements).isEqualTo(2L)
        assertThat(result.totalPages).isEqualTo(1)
        assertThat(result.number).isEqualTo(0)
        assertThat(result.size).isEqualTo(10)
    }

    @Test
    fun `findByUserIdOrderByIdDesc 다른 유저의 리스트는 조회되지 않는다`() {
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

        createAndSaveRestaurantList(
            user1,
            "user1 리스트"
        )
        createAndSaveRestaurantList(
            user2,
            "user2 리스트"
        )

        val result =
            restaurantListRepository.findByUserIdOrderByIdDesc(
                user1.id!!,
                PageRequest.of(0, 10)
            )

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].title)
            .isEqualTo("user1 리스트")

        assertThat(result.totalElements).isEqualTo(1L)
        assertThat(result.totalPages).isEqualTo(1)
    }

    @Test
    fun `findByIdAndUserId 성공`() {
        val user = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )

        val restaurantList =
            createAndSaveRestaurantList(
                user,
                "리스트1"
            )

        val result =
            restaurantListRepository.findByIdAndUserId(
                restaurantList.id!!,
                user.id!!
            )

        assertThat(result).isPresent
        assertThat(result.get().title)
            .isEqualTo("리스트1")
    }

    @Test
    fun `findByIdAndUserId 다른 유저면 조회되지 않는다`() {
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

        val restaurantList =
            createAndSaveRestaurantList(
                user1,
                "리스트1"
            )

        val result =
            restaurantListRepository.findByIdAndUserId(
                restaurantList.id!!,
                user2.id!!
            )

        assertThat(result).isEmpty
    }

    @Test
    fun `save 시 createdAt이 자동 설정된다`() {
        val user = createAndSaveUser(
            "user1",
            "nick1",
            "user1@test.com"
        )

        val restaurantList =
            createAndSaveRestaurantList(
                user,
                "리스트1"
            )

        assertThat(restaurantList.createdAt)
            .isNotNull()
    }
}