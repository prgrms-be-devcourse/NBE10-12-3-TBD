package com.whattoeat.domain.restaurantlist.repository

import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurantlist.entity.RestaurantList
import com.whattoeat.domain.restaurantlist.entity.SavedRestaurantList
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest

@DataJpaTest
class SavedRestaurantListRepositoryTest {

    @Autowired
    lateinit var savedRestaurantListRepository: SavedRestaurantListRepository

    @Autowired
    lateinit var restaurantListRepository: RestaurantListRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    @DisplayName("사용자 ID와 레스토랑 리스트 ID로 저장 여부를 확인할 수 있다")
    fun `existsByUserIdAndRestaurantListId 저장되어있으면 true`() {
        val user = userRepository.save(
            createUser(
                "user1@test.com",
                "사용자1"
            )
        )

        val owner = userRepository.save(
            createUser(
                "owner@test.com",
                "작성자"
            )
        )

        val restaurantList = restaurantListRepository.save(
            createRestaurantList(
                owner,
                "혼밥 맛집",
                "혼자 먹기 좋은 곳"
            )
        )

        savedRestaurantListRepository.save(
            SavedRestaurantList(
                user,
                restaurantList
            )
        )

        val result =
            savedRestaurantListRepository
                .existsByUserIdAndRestaurantListId(
                    user.id!!,
                    restaurantList.id!!
                )

        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("저장하지 않은 리스트는 저장 여부가 false다")
    fun `existsByUserIdAndRestaurantListId 저장되어있지않으면 false`() {
        val user = userRepository.save(
            createUser(
                "user1@test.com",
                "사용자1"
            )
        )

        val owner = userRepository.save(
            createUser(
                "owner@test.com",
                "작성자"
            )
        )

        val restaurantList = restaurantListRepository.save(
            createRestaurantList(
                owner,
                "데이트 맛집",
                "데이트하기 좋은 곳"
            )
        )

        val result =
            savedRestaurantListRepository
                .existsByUserIdAndRestaurantListId(
                    user.id!!,
                    restaurantList.id!!
                )

        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("사용자 ID와 레스토랑 리스트 ID로 저장 기록을 조회할 수 있다")
    fun `findByUserIdAndRestaurantListId 성공`() {
        val user = userRepository.save(
            createUser(
                "user1@test.com",
                "사용자1"
            )
        )

        val owner = userRepository.save(
            createUser(
                "owner@test.com",
                "작성자"
            )
        )

        val restaurantList = restaurantListRepository.save(
            createRestaurantList(
                owner,
                "친구 맛집",
                "친구랑 가기 좋은 곳"
            )
        )

        val savedRestaurantList =
            savedRestaurantListRepository.save(
                SavedRestaurantList(
                    user,
                    restaurantList
                )
            )

        val result =
            savedRestaurantListRepository
                .findByUserIdAndRestaurantListId(
                    user.id!!,
                    restaurantList.id!!
                )

        assertThat(result).isPresent

        val foundSavedList = result.get()

        assertThat(foundSavedList.id)
            .isEqualTo(savedRestaurantList.id)

        assertThat(foundSavedList.user.id)
            .isEqualTo(user.id)

        assertThat(foundSavedList.restaurantList.id)
            .isEqualTo(restaurantList.id)
    }

    @Test
    @DisplayName("내가 저장한 레스토랑 리스트 목록을 조회할 수 있다")
    fun `findByUserId 성공`() {
        val user = userRepository.save(
            createUser(
                "user1@test.com",
                "사용자1"
            )
        )

        val owner = userRepository.save(
            createUser(
                "owner@test.com",
                "작성자"
            )
        )

        val restaurantList1 = restaurantListRepository.save(
            createRestaurantList(
                owner,
                "혼밥 맛집",
                "혼자 먹기 좋은 곳"
            )
        )

        val restaurantList2 = restaurantListRepository.save(
            createRestaurantList(
                owner,
                "데이트 맛집",
                "데이트하기 좋은 곳"
            )
        )

        savedRestaurantListRepository.save(
            SavedRestaurantList(
                user,
                restaurantList1
            )
        )

        savedRestaurantListRepository.save(
            SavedRestaurantList(
                user,
                restaurantList2
            )
        )

        val result =
            savedRestaurantListRepository.findByUserId(
                user.id!!,
                PageRequest.of(0, 10)
            )

        assertThat(result.content).hasSize(2)
    }

    @Test
    @DisplayName("같은 사용자가 같은 레스토랑 리스트를 중복 저장할 수 없다")
    fun `같은 사용자가 같은 리스트를 중복저장하면 예외`() {
        val user = userRepository.save(
            createUser(
                "user1@test.com",
                "사용자1"
            )
        )

        val owner = userRepository.save(
            createUser(
                "owner@test.com",
                "작성자"
            )
        )

        val restaurantList = restaurantListRepository.save(
            createRestaurantList(
                owner,
                "혼밥 맛집",
                "혼자 먹기 좋은 곳"
            )
        )

        savedRestaurantListRepository.saveAndFlush(
            SavedRestaurantList(
                user,
                restaurantList
            )
        )

        assertThatThrownBy {
            savedRestaurantListRepository.saveAndFlush(
                SavedRestaurantList(
                    user,
                    restaurantList
                )
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun createUser(
        email: String,
        nickname: String
    ): User {
        return User(
            null,
            null,
            null,
            nickname,
            null,
            email,
            Role.USER,
            Provider.LOCAL
        )
    }

    private fun createRestaurantList(
        user: User,
        title: String,
        description: String
    ): RestaurantList {
        return RestaurantList(
            user,
            title,
            description,
            MoodTag.SOLO
        )
    }
}