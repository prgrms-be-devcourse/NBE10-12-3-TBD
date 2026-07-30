package com.whattoeat.domain.feed.messaging

import com.whattoeat.domain.feed.event.FeedCreatedEvent
import com.whattoeat.domain.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class FeedEventListener(
    private val notificationService: NotificationService,
) {
    // 커밋 이후에도 실패는 삼킨다 - 알림 생성 실패가 이미 커밋된 피드 생성 요청의 응답을 실패시키면 안 됨
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onFeedCreated(event: FeedCreatedEvent) {
        val feedId = event.feedId ?: return
        val authorId = event.authorId ?: return

        try {
            notificationService.createFeedNotifications(event.feedId, event.authorId)
        } catch (e: Exception) {
            log.warn("피드 생성 알림 생성 실패 (feedId={}): {}", event.feedId, e.message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FeedEventListener::class.java)
    }
}
