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
    fun findSecondDegreeAuthorIds_authorIds_범위_밖의_2차_팔로우는_조회되지_않는다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val friend = createAndSaveUser("friend", "friend", "friend@test.com")
        val candidateAuthor = createAndSaveUser("candidateAuthor", "candidateAuthor", "candidate@test.com")
        val outOfRangeAuthor = createAndSaveUser("outOfRangeAuthor", "outOfRangeAuthor", "outofrange@test.com")

        createAndSaveFollow(me, friend)
        createAndSaveFollow(friend, candidateAuthor)
        createAndSaveFollow(friend, outOfRangeAuthor)

        val result =
            followRepository.findSecondDegreeAuthorIds(
                userId = requireNotNull(me.id),
                authorIds = listOf(requireNotNull(candidateAuthor.id)),
            )

        assertThat(result).containsExactly(candidateAuthor.id)
    }

    @Test
    fun findSecondDegreeAuthorIds_내가_팔로우하지_않는_사람을_거친_2차_팔로우는_조회되지_않는다() {
        val me = createAndSaveUser("me", "me", "me@test.com")
        val stranger = createAndSaveUser("stranger", "stranger", "stranger@test.com")
        val candidateAuthor = createAndSaveUser("candidateAuthor", "candidateAuthor", "candidate@test.com")

        // me는 stranger를 팔로우하지 않으므로, stranger가 팔로우하는 사람은 me의 2차 팔로우가 아니다.
        createAndSaveFollow(stranger, candidateAuthor)

        val result =
            followRepository.findSecondDegreeAuthorIds(
                userId = requireNotNull(me.id),
                authorIds = listOf(requireNotNull(candidateAuthor.id)),
            )

        assertThat(result).isEmpty()
    }
}
