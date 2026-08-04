package com.whattoeat.domain.feed.repository

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.follow.entity.Follow
import com.whattoeat.domain.user.entity.Provider
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
internal class FeedRepositoryTest {
    @Autowired
    private lateinit var feedRepository: FeedRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private fun createAndSaveUser(loginId: String, nickname: String, email: String): User {
        val user = User(
            loginId = loginId,
            nickname = nickname,
            email = email,
            provider = Provider.LOCAL,
        )
        return entityManager.persistAndFlush(user)
    }

    private fun createAndSaveFeed(user: User, content: String): Feed {
        val feed = Feed.builder()
            .user(user)
            .content(content).build()

        return entityManager.persistAndFlush(feed)
    }

    private fun createAndSaveFollow(follower: User, following: User): Follow =
        entityManager.persistAndFlush(Follow.of(follower, following))

    @Test
    fun findFollowingFeeds_팔로우하지_않아도_내_글은_포함된다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val myFeed = createAndSaveFeed(me, "my feed")

        val result = feedRepository.findFollowingFeeds(requireNotNull(me.id), PageRequest.of(0, 10))

        assertThat(result.content).extracting("id").containsExactly(myFeed.id)
    }

    @Test
    fun findFollowingFeeds_팔로우한_사람의_글은_포함되고_모르는_사람의_글은_제외된다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val followedAuthor = createAndSaveUser("followedAuthor", "followedAuthor", "followed@test.com")
        val stranger = createAndSaveUser("stranger", "stranger", "stranger@test.com")
        createAndSaveFollow(me, followedAuthor)

        val followedFeed = createAndSaveFeed(followedAuthor, "followed feed")
        createAndSaveFeed(stranger, "stranger feed")

        val result = feedRepository.findFollowingFeeds(requireNotNull(me.id), PageRequest.of(0, 10))

        assertThat(result.content).extracting("id").containsExactly(followedFeed.id)
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun findRecommendCandidates_본인과_팔로우한_사람의_글은_제외된다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val followedAuthor = createAndSaveUser("followedAuthor", "followedAuthor", "followed@test.com")
        val stranger = createAndSaveUser("stranger", "stranger", "stranger@test.com")
        createAndSaveFollow(me, followedAuthor)

        createAndSaveFeed(me, "my feed")
        createAndSaveFeed(followedAuthor, "followed feed")
        val strangerFeed = createAndSaveFeed(stranger, "stranger feed")

        val result =
            feedRepository.findRecommendCandidates(
                requireNotNull(me.id),
                null,
                PageRequest.of(0, 300),
            )

        assertThat(result).extracting("id").containsExactly(strangerFeed.id)
    }

    @Test
    fun findRecommendCandidates_beforeFeedId보다_오래된_글만_다음_후보로_가져온다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val stranger = createAndSaveUser("stranger", "stranger", "stranger@test.com")

        val olderFeed = createAndSaveFeed(stranger, "older feed")
        val newerFeed = createAndSaveFeed(stranger, "newer feed")

        val result =
            feedRepository.findRecommendCandidates(
                requireNotNull(me.id),
                requireNotNull(newerFeed.id),
                PageRequest.of(0, 300),
            )

        assertThat(result).extracting("id").containsExactly(olderFeed.id)
    }
}
