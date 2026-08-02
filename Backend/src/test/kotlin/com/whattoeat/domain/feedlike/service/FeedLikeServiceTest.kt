package com.whattoeat.domain.feedlike.service

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.feedlike.dto.FeedLikeResponse
import com.whattoeat.domain.feedlike.repository.FeedLikeRepository
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.AlreadyLikedFeedException
import com.whattoeat.global.exception.FeedLikeNotFoundException
import com.whattoeat.global.exception.FeedNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class FeedLikeServiceTest {
    @MockitoBean
    private lateinit var clientRegistrationRepository: ClientRegistrationRepository

    @Autowired
    private lateinit var feedLikeService: FeedLikeService

    @Autowired
    private lateinit var feedLikeRepository: FeedLikeRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var feedRepository: FeedRepository

    @Test
    @DisplayName("like succeeds")
    fun like() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")

        val response: FeedLikeResponse = feedLikeService.like(user.id!!, feed.id!!)

        assertThat(response.feedId).isEqualTo(feed.id)
        assertThat(response.likeCount).isEqualTo(1)
        assertThat(response.isLikedByMe).isTrue()
        assertThat(feedLikeRepository.existsByFeed_IdAndUser_Id(feed.id!!, user.id!!))
            .isTrue()
    }

    @Test
    @DisplayName("already liked feed fails")
    fun likeAlreadyLikedFeed() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")
        feedLikeService.like(user.id!!, feed.id!!)

        assertThatThrownBy { feedLikeService.like(user.id!!, feed.id!!) }
            .isInstanceOf(AlreadyLikedFeedException::class.java)
            .hasMessage("이미 좋아요한 피드입니다.")
    }

    @Test
    @DisplayName("unlike succeeds")
    fun unlike() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")
        feedLikeService.like(user.id!!, feed.id!!)

        val response: FeedLikeResponse = feedLikeService.unlike(user.id!!, feed.id!!)

        assertThat(response.feedId).isEqualTo(feed.id)
        assertThat(response.likeCount).isEqualTo(0)
        assertThat(response.isLikedByMe).isFalse()
        assertThat(feedLikeRepository.existsByFeed_IdAndUser_Id(feed.id!!, user.id!!))
            .isFalse()
    }

    @Test
    @DisplayName("unlike fails when relation does not exist")
    fun unlikeNotFound() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")

        assertThatThrownBy { feedLikeService.unlike(user.id!!, feed.id!!) }
            .isInstanceOf(FeedLikeNotFoundException::class.java)
            .hasMessage("좋아요 관계가 존재하지 않습니다.")
    }

    @Test
    @DisplayName("like status returns true")
    fun getLikeStatusTrue() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")
        feedLikeService.like(user.id!!, feed.id!!)

        val response: FeedLikeResponse = feedLikeService.getLikeStatus(user.id!!, feed.id!!)

        assertThat(response.feedId).isEqualTo(feed.id)
        assertThat(response.likeCount).isEqualTo(1)
        assertThat(response.isLikedByMe).isTrue()
    }

    @Test
    @DisplayName("like status returns false")
    fun getLikeStatusFalse() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")

        val response: FeedLikeResponse = feedLikeService.getLikeStatus(user.id!!, feed.id!!)

        assertThat(response.feedId).isEqualTo(feed.id)
        assertThat(response.likeCount).isEqualTo(0)
        assertThat(response.isLikedByMe).isFalse()
    }

    @Test
    @DisplayName("like fails when user does not exist")
    fun likeUserNotFound() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")

        assertThatThrownBy { feedLikeService.like(999L, feed.id!!) }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessage("User not found: 999")
    }

    @Test
    @DisplayName("like fails when feed does not exist")
    fun likeFeedNotFound() {
        val user = saveUser("user")

        assertThatThrownBy { feedLikeService.like(user.id!!, 999L) }
            .isInstanceOf(FeedNotFoundException::class.java)
            .hasMessage("Feed not found: 999")
    }

    @Test
    @DisplayName("unlike fails when user does not exist")
    fun unlikeUserNotFound() {
        val user = saveUser("user")
        val feed = saveFeed(user, "content")

        assertThatThrownBy { feedLikeService.unlike(999L, feed.id!!) }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessage("User not found: 999")
    }

    @Test
    @DisplayName("unlike fails when feed does not exist")
    fun unlikeFeedNotFound() {
        val user = saveUser("user")

        assertThatThrownBy { feedLikeService.unlike(user.id!!, 999L) }
            .isInstanceOf(FeedNotFoundException::class.java)
            .hasMessage("Feed not found: 999")
    }

    private fun saveUser(name: String): User =
        userRepository.save(
            User.builder()
                .loginId(name)
                .password("password")
                .kakaoId("$name-kakao")
                .nickname(name)
                .email("$name@test.com")
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .build(),
        )

    private fun saveFeed(user: User, content: String): Feed {
        val feed =
            Feed.builder()
                .user(user)
                .content(content)
                .build()
        ReflectionTestUtils.setField(feed, "updatedAt", LocalDateTime.now())
        return feedRepository.save(feed)
    }
}
