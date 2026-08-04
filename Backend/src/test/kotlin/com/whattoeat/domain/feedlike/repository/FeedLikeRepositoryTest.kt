package com.whattoeat.domain.feedlike.repository

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feedlike.entity.FeedLike
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

@DataJpaTest
@Import(JpaConfig::class)
internal class FeedLikeRepositoryTest {
    @Autowired
    private lateinit var feedLikeRepository: FeedLikeRepository

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

    private fun createAndSaveLike(feed: Feed, user: User): FeedLike =
        entityManager.persistAndFlush(FeedLike.of(feed, user))

    private fun createAndSaveFollow(follower: User, following: User): Follow =
        entityManager.persistAndFlush(Follow.of(follower, following))

    @Test
    fun findFeedIdsLikedByFollowingOf_feedIds_범위_밖의_좋아요는_조회되지_않는다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val liker = createAndSaveUser("liker", "liker", "liker@test.com")
        val author = createAndSaveUser("author", "author", "author@test.com")
        createAndSaveFollow(me, liker)

        val candidateFeed = createAndSaveFeed(author, "후보 피드")
        val outOfRangeFeed = createAndSaveFeed(author, "후보 범위 밖 피드")

        createAndSaveLike(candidateFeed, liker)
        createAndSaveLike(outOfRangeFeed, liker)

        val result =
            feedLikeRepository.findFeedIdsLikedByFollowingOf(
                userId = requireNotNull(me.id),
                feedIds = listOf(requireNotNull(candidateFeed.id)),
            )

        assertThat(result).containsExactly(candidateFeed.id)
    }

    @Test
    fun findFeedIdsLikedByFollowingOf_내가_팔로우하지_않는_사람의_좋아요는_조회되지_않는다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val stranger = createAndSaveUser("stranger", "stranger", "stranger@test.com")
        val author = createAndSaveUser("author", "author", "author@test.com")

        val feed = createAndSaveFeed(author, "피드")
        createAndSaveLike(feed, stranger)

        val result =
            feedLikeRepository.findFeedIdsLikedByFollowingOf(
                userId = requireNotNull(me.id),
                feedIds = listOf(requireNotNull(feed.id)),
            )

        assertThat(result).isEmpty()
    }
}
