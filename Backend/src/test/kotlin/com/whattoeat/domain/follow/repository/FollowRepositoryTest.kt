package com.whattoeat.domain.follow.repository

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
internal class FollowRepositoryTest {
    @Autowired
    private lateinit var followRepository: FollowRepository

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

    private fun createAndSaveFollow(follower: User, following: User): Follow =
        entityManager.persistAndFlush(Follow.of(follower, following))

    @Test
    fun findFollowingIdsByFollowerIds_authorIds_범위_밖의_팔로우는_조회되지_않는다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val friend = createAndSaveUser("friend", "friend", "friend@test.com")
        val candidateAuthor = createAndSaveUser("candidateAuthor", "candidateAuthor", "candidate@test.com")
        val outOfRangeAuthor = createAndSaveUser("outOfRangeAuthor", "outOfRangeAuthor", "outofrange@test.com")

        createAndSaveFollow(friend, candidateAuthor)
        createAndSaveFollow(friend, outOfRangeAuthor)

        val result =
            followRepository.findFollowingIdsByFollowerIds(
                followerIds = listOf(requireNotNull(friend.id)),
                authorIds = listOf(requireNotNull(candidateAuthor.id)),
            )

        assertThat(result).containsExactly(candidateAuthor.id)
    }

    @Test
    fun findFollowingIdsByFollowerIds_followerIds에_없는_사람의_팔로우는_조회되지_않는다() {
        val friend = createAndSaveUser("friend", "friend", "friend@test.com")
        val stranger = createAndSaveUser("stranger", "stranger", "stranger@test.com")
        val candidateAuthor = createAndSaveUser("candidateAuthor", "candidateAuthor", "candidate@test.com")

        createAndSaveFollow(stranger, candidateAuthor)

        val result =
            followRepository.findFollowingIdsByFollowerIds(
                followerIds = listOf(requireNotNull(friend.id)),
                authorIds = listOf(requireNotNull(candidateAuthor.id)),
            )

        assertThat(result).isEmpty()
    }
}
