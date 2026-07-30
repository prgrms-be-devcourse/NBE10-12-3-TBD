package com.whattoeat.domain.notification.service

import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import com.whattoeat.domain.notification.repository.NotificationRepository
import com.whattoeat.global.exception.NotificationNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val feedRepository: FeedRepository,
    private val followRepository: FollowRepository
) {

    // REQUIRES_NEW: AFTER_COMMIT 콜백에서 호출되면 스레드에 남아있는 종료된 트랜잭션에
    // 편승해 flush가 조용히 사라지므로, 항상 새 트랜잭션을 시작해야 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createFeedNotifications(feedId: Long, authorId: Long) {
        val feed = feedRepository.findById(feedId).orElse(null) ?: return

        val follows = followRepository.findByFollowing_Id(authorId, Pageable.unpaged()).content
        if (follows.isEmpty()) {
            return
        }

        val author = follows[0].following
        val message = "${author.nickname}님이 새 글을 작성했습니다."

        val notifications = follows.map { follow ->
            Notification.of(follow.follower, author, feed, NotificationType.NEW_FEED, message)
        }

        notificationRepository.saveAll(notifications)
    }

    @Transactional(readOnly = true)
    fun getNotifications(userId: Long, pageable: Pageable): Page<Notification> =
        notificationRepository.findByReceiverIdOrderByIdDesc(userId, pageable)

    @Transactional
    fun markAsRead(userId: Long, notificationId: Long): Notification {
        val notification = notificationRepository.findByIdAndReceiverId(notificationId, userId)
            .orElseThrow { NotificationNotFoundException(notificationId) }

        notification.markAsRead()

        return notification
    }
}
