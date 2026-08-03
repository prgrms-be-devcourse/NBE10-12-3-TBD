package com.whattoeat.domain.feed.repository

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
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

@DataJpaTest
@Import(JpaConfig::class)
class FeedRepositoryTest {
    @Autowired
    lateinit var feedRepository: FeedRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    private fun createUser(): User =
        entityManager.persistAndFlush(
            User.builder()
                .loginId("feed-test-user")
                .nickname("피드테스터")
                .email("feed@test.com")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build()
        )

    private fun createRestaurant(
        kakaoPlaceId: String,
        name: String,
    ): Restaurant =
        entityManager.persistAndFlush(
            Restaurant(
                kakaoPlaceId = kakaoPlaceId,
                name = name,
                category = Category.KOREAN,
                address = "서울시 강남구",
                roadAddress = null,
                region1 = "서울특별시",
                region2 = "강남구",
                region3 = null,
                region4 = null,
                phone = null,
                lat = 37.5,
                lng = 127.0
            )
        )

    private fun createFeed(
        user: User,
        restaurant: Restaurant,
        mood: MoodTag
    ) {
        entityManager.persistAndFlush(
            Feed(
                user = user,
                restaurant = restaurant,
                content = "테스트 피드",
                imageUrl = null,
                likeCount = 0,
                moodTag = mood
            )
        )
    }

    @Test
    fun `countMoodVotes는 지정한 분위기와 식당의 피드 수를 집계한다`(){
        val user = createUser()
        val targetRestaurant = createRestaurant("kakao-1","대상 식당")
        val excludeRestaurant = createRestaurant("kakao-2","제외 식당")

        createFeed(user, targetRestaurant, MoodTag.DATE)
        createFeed(user, targetRestaurant, MoodTag.DATE)
        createFeed(user, targetRestaurant, MoodTag.SOLO)
        createFeed(user, excludeRestaurant, MoodTag.DATE)

        val result = feedRepository.countMoodVotes(
            MoodTag.DATE,
            listOf(targetRestaurant.id!!)
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].restaurantId).isEqualTo(targetRestaurant.id)
        assertThat(result[0].voteCount).isEqualTo(2L)

    }


}