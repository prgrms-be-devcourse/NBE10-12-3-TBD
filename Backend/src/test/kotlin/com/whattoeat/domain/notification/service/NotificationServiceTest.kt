package com.whattoeat.domain.notification.service

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.follow.entity.Follow
import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.NotificationNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var feedRepository: FeedRepository

    @Mock
    private lateinit var followRepository: FollowRepository

    @InjectMocks
    private lateinit var notificationService: NotificationService

    private fun createUser(id: Long, nickname: String): User {
        val user = User.builder().nickname(nickname).email("$nickname@test.com").provider(Provider.LOCAL).build()
        ReflectionTestUtils.setField(user, "id", id)
        return user
    }

    private fun createFeed(id: Long, author: User): Feed {
        val feed = Feed.builder().user(author).content("맛집이네요").build()
        ReflectionTestUtils.setField(feed, "id", id)
        return feed
    }

    private fun createNotification(id: Long, receiver: User, actor: User, feed: Feed?): Notification {
        val notification = Notification.of(receiver, actor, feed, NotificationType.NEW_FEED, "$id")
        ReflectionTestUtils.setField(notification, "id", id)
        return notification
    }

    @Test
    @DisplayName("팔로워 전원에게 새 글 알림이 생성된다")
    fun createFeedNotifications_success() {
        val author = createUser(1L, "작성자")
        val follower1 = createUser(2L, "팔로워1")
        val follower2 = createUser(3L, "팔로워2")
        val feed = createFeed(10L, author)

        given(feedRepository.findById(10L)).willReturn(Optional.of(feed))
        given(followRepository.findByFollowing_Id(1L, Pageable.unpaged()))
            .willReturn(PageImpl(listOf(Follow.of(follower1, author), Follow.of(follower2, author))))

        notificationService.createFeedNotifications(10L, 1L)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Notification>>
        verify(notificationRepository).saveAll(captor.capture())

        val saved = captor.value
        assertThat(saved).hasSize(2)
        assertThat(saved.map { it.receiver.id }).containsExactlyInAnyOrder(2L, 3L)
        assertThat(saved).allMatch { it.actor.id == 1L }
        assertThat(saved).allMatch { it.feed?.id == 10L }
        assertThat(saved).allMatch { it.message.contains("작성자") }
    }

    @Test
    @DisplayName("존재하지 않는 피드면 알림을 생성하지 않는다")
    fun createFeedNotifications_feedNotFound() {
        given(feedRepository.findById(999L)).willReturn(Optional.empty())

        notificationService.createFeedNotifications(999L, 1L)

        verify(notificationRepository, never()).saveAll(any<List<Notification>>())
    }

    @Test
    @DisplayName("팔로워가 없으면 알림을 생성하지 않는다")
    fun createFeedNotifications_noFollowers() {
        val author = createUser(1L, "작성자")
        val feed = createFeed(10L, author)

        given(feedRepository.findById(10L)).willReturn(Optional.of(feed))
        given(followRepository.findByFollowing_Id(1L, Pageable.unpaged()))
            .willReturn(PageImpl(emptyList<Follow>()))

        notificationService.createFeedNotifications(10L, 1L)

        verify(notificationRepository, never()).saveAll(any<List<Notification>>())
    }

    @Test
    @DisplayName("알림 읽음 처리 성공")
    fun markAsRead_success() {
        val receiver = createUser(2L, "받는사람")
        val actor = createUser(1L, "작성자")
        val feed = createFeed(10L, actor)
        val notification = createNotification(100L, receiver, actor, feed)

        given(notificationRepository.findByIdAndReceiverId(100L, 2L)).willReturn(Optional.of(notification))

        val result = notificationService.markAsRead(2L, 100L)

        assertThat(result.isRead).isTrue
    }

    @Test
    @DisplayName("본인 소유가 아니거나 존재하지 않는 알림이면 예외 발생")
    fun markAsRead_notFound() {
        given(notificationRepository.findByIdAndReceiverId(100L, 2L)).willReturn(Optional.empty())

        assertThatThrownBy { notificationService.markAsRead(2L, 100L) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    @DisplayName("커서 조회 - size+1 조회되면 hasNext=true, nextCursor는 마지막 항목 id")
    fun getNotificationsByCursor_hasNext_true() {
        val receiver = createUser(2L, "받는사람")
        val actor = createUser(1L, "작성자")
        val feed = createFeed(10L, actor)

        val n1 = createNotification(100L, receiver, actor, feed)
        val n2 = createNotification(99L, receiver, actor, feed)
        val n3 = createNotification(98L, receiver, actor, feed)

        // size=2 요청 → 내부적으로 PageRequest.of(0, 3) 로 조회
        given(notificationRepository.findByReceiverIdWithCursor(2L, null, PageRequest.of(0, 3)))
            .willReturn(listOf(n1, n2, n3))

        val result = notificationService.getNotificationsByCursor(2L, null, 2)

        assertThat(result.content).hasSize(2)
        assertThat(result.hasNext).isTrue
        assertThat(result.nextCursor).isEqualTo(99L)
    }

    @Test
    @DisplayName("커서 조회 - size 이하 조회되면 hasNext=false, nextCursor=null")
    fun getNotificationsByCursor_hasNext_false() {
        val receiver = createUser(2L, "받는사람")
        val actor = createUser(1L, "작성자")
        val feed = createFeed(10L, actor)
        val n1 = createNotification(100L, receiver, actor, feed)

        given(notificationRepository.findByReceiverIdWithCursor(2L, null, PageRequest.of(0, 21)))
            .willReturn(listOf(n1))

        val result = notificationService.getNotificationsByCursor(2L, null, 20)

        assertThat(result.content).hasSize(1)
        assertThat(result.hasNext).isFalse
        assertThat(result.nextCursor).isNull()
    }

    @Test
    @DisplayName("커서 조회 - size는 1~50으로 clamp된다 (0→1, 999→50)")
    fun getNotificationsByCursor_sizeClamp() {
        // 0 → 1 로 clamp → PageRequest.of(0, 2)
        given(notificationRepository.findByReceiverIdWithCursor(2L, null, PageRequest.of(0, 2)))
            .willReturn(emptyList())
        // 999 → 50 으로 clamp → PageRequest.of(0, 51)
        given(notificationRepository.findByReceiverIdWithCursor(2L, null, PageRequest.of(0, 51)))
            .willReturn(emptyList())

        notificationService.getNotificationsByCursor(2L, null, 0)
        notificationService.getNotificationsByCursor(2L, null, 999)

        verify(notificationRepository).findByReceiverIdWithCursor(2L, null, PageRequest.of(0, 2))
        verify(notificationRepository).findByReceiverIdWithCursor(2L, null, PageRequest.of(0, 51))
    }

    @Test
    @DisplayName("커서 조회 - cursor 파라미터가 그대로 repository로 전달된다")
    fun getNotificationsByCursor_cursorPassthrough() {
        given(notificationRepository.findByReceiverIdWithCursor(2L, 50L, PageRequest.of(0, 21)))
            .willReturn(emptyList())

        val result = notificationService.getNotificationsByCursor(2L, 50L, 20)

        assertThat(result.content).isEmpty()
        assertThat(result.hasNext).isFalse
        verify(notificationRepository).findByReceiverIdWithCursor(2L, 50L, PageRequest.of(0, 21))
    }

    @Test
    @DisplayName("안 읽은 알림 개수를 반환한다")
    fun getUnreadCount_returnsRepoCount() {
        given(notificationRepository.countByReceiverIdAndIsReadFalse(2L)).willReturn(7L)

        val count = notificationService.getUnreadCount(2L)

        assertThat(count).isEqualTo(7L)
    }
}
