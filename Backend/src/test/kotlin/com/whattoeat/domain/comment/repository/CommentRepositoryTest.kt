package com.whattoeat.domain.comment.repository

import com.whattoeat.domain.comment.entity.Comment
import com.whattoeat.domain.feed.entity.Feed
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
internal class CommentRepositoryTest {
    @Autowired
    private lateinit var commentRepository: CommentRepository

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

    private fun createAndSaveComment(feed: Feed, user: User, content: String): Comment {
        val comment = Comment(
            feed = feed,
            user = user,
            content = content
        )
        return entityManager.persistAndFlush(comment)
    }

    @Test
    fun findByFeedId_성공() {
        val user = createAndSaveUser("user1", "nick1", "user1@test.com")
        val feed = createAndSaveFeed(user, "피드 내용")
        createAndSaveComment(feed, user, "댓글1")
        createAndSaveComment(feed, user, "댓글2")

        val result: List<Comment> = commentRepository.findByFeedId(requireNotNull(feed.id))

        assertThat(result).hasSize(2)
        assertThat(result).extracting("content").containsExactlyInAnyOrder("댓글1", "댓글2")
    }

    @Test
    fun findByFeedId_다른_피드의_댓글은_조회되지_않는다() {
        val user = createAndSaveUser("user1", "nick1", "user1@test.com")
        val feed1 = createAndSaveFeed(user, "피드1")
        val feed2 = createAndSaveFeed(user, "피드2")
        createAndSaveComment(feed1, user, "피드1 댓글")
        createAndSaveComment(feed2, user, "피드2 댓글")

        val result: List<Comment> = commentRepository.findByFeedId(requireNotNull(feed1.id))

        assertThat(result).hasSize(1)
        assertThat(result.get(0).content).isEqualTo("피드1 댓글")
    }

    @Test
    fun findByFeedId_댓글이_없으면_빈_리스트_반환() {
        val user = createAndSaveUser("user1", "nick1", "user1@test.com")
        val feed = createAndSaveFeed(user, "피드 내용")

        val result: List<Comment> = commentRepository.findByFeedId(requireNotNull(feed.id))

        assertThat(result).isEmpty()
    }

    @Test
    fun save_시_createdAt이_자동_설정된다() {
        val user = createAndSaveUser("user1", "nick1", "user1@test.com")
        val feed = createAndSaveFeed(user, "피드 내용")
        val comment = createAndSaveComment(feed, user, "댓글")

        assertThat(comment.createdAt).isNotNull()
    }
}
