package com.whattoeat.domain.comment.service

import com.whattoeat.domain.comment.dto.CommentRequest
import com.whattoeat.domain.comment.entity.Comment
import com.whattoeat.domain.comment.repository.CommentRepository
import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.CommentNotFoundException
import com.whattoeat.global.exception.FeedNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import org.springframework.security.access.AccessDeniedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
internal class CommentServiceTest {
    @Mock
    private lateinit var commentRepository: CommentRepository

    @Mock
    private lateinit var feedRepository: FeedRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks
    private lateinit var commentService: CommentService

    private fun createUser(nickname: String): User = User(
        loginId = "testuser", nickname = nickname, email = "test@example.com", provider = Provider.LOCAL
    )


    private fun createFeed(user: User): Feed = Feed.builder().user(user).content("피드 내용").build()


    @Test
    fun getComments_성공() {
        val user = createUser("testUser")
        val feed = createFeed(user)
        val comment1 = Comment(feed = feed, user = user, content = "댓글1")
        val comment2 = Comment(feed = feed, user = user, content = "댓글2")
        given(commentRepository.findByFeedId(1L))
            .willReturn(listOf(comment1, comment2))

        val result = commentService.getComments(1L)

        assertThat(result).hasSize(2)
        assertThat(result.get(0).content).isEqualTo("댓글1")
        assertThat(result.get(1).content).isEqualTo("댓글2")
    }

    @Test
    fun getComments_댓글이_없으면_빈_리스트_반환() {
        given(commentRepository.findByFeedId(1L))
            .willReturn(listOf())

        val result = commentService.getComments(1L)

        assertThat(result).isEmpty()
    }

    @Test
    fun createComment_성공() {
        val user = createUser("testUser")
        val feed = createFeed(user)
        val request = CommentRequest("새 댓글")
        val saved = Comment(feed = feed, user = user, content = "새 댓글")
        ReflectionTestUtils.setField(saved, "id", 42L)
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed))
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(commentRepository.save(ArgumentMatchers.any(Comment::class.java)))
            .willReturn(saved)

        val result = commentService.createComment(1L, 1L, request)

        assertThat(result.content).isEqualTo("새 댓글")
        assertThat(result.nickname).isEqualTo("testUser")
    }

    @Test
    fun createComment_피드가_없으면_예외발생() {
        given(feedRepository.findById(999L)).willReturn(Optional.empty())
        val request = CommentRequest("댓글")

        assertThatThrownBy { commentService.createComment(999L, 1L, request) }
            .isInstanceOf(FeedNotFoundException::class.java)
            .hasMessageContaining("999")
    }

    @Test
    fun createComment_유저가_없으면_예외발생() {
        val user = createUser("testUser")
        val feed = createFeed(user)
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed))
        given(userRepository.findById(999L)).willReturn(Optional.empty())
        val request = CommentRequest("댓글")

        assertThatThrownBy { commentService.createComment(1L, 999L, request) }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessageContaining("999")
    }

    @Test
    fun deleteComment_성공() {
        val user = createUser("testUser")
        val feed = createFeed(user)
        val comment = Comment(feed = feed, user = user, content = "댓글")

        ReflectionTestUtils.setField(feed, "id", 1L)
        ReflectionTestUtils.setField(user, "id", 1L)

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment))

        commentService.deleteComment(1L, 1L, 1L)

        then(commentRepository).should().delete(comment)
    }

    @Test
    fun deleteComment_댓글이_없으면_예외발생() {
        given(commentRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { commentService.deleteComment(1L, 999L, 1L) }
            .isInstanceOf(CommentNotFoundException::class.java)
            .hasMessageContaining("999")
    }

    @Test
    fun deleteComment_다른_피드의_댓글이면_예외발생() {
        val user = createUser("testUser")
        val feed = createFeed(user)
        ReflectionTestUtils.setField(feed, "id", 1L)
        val comment = Comment(feed = feed, user = user, content = "댓글")
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment))

        assertThatThrownBy { commentService.deleteComment(999L, 1L, 1L) }
            .isInstanceOf(CommentNotFoundException::class.java)
            .hasMessageContaining("1")
        then(commentRepository).should(never()).delete(comment)
    }

    @Test
    fun deleteComment_타인의_댓글이면_예외발생() {
        val owner = createUser("testUser")
        val feed = createFeed(owner)
        val comment = Comment(feed, owner, "댓글")

        ReflectionTestUtils.setField(feed, "id", 1L)
        ReflectionTestUtils.setField(owner, "id", 1L)

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment))
        assertThatThrownBy { commentService.deleteComment(1L, 1L, 2L) }
            .isInstanceOf(AccessDeniedException::class.java)
        then(commentRepository).should(never()).delete(comment)
    }
}
