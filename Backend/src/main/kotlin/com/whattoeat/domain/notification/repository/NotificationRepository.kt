package com.whattoeat.domain.notification.repository

import com.whattoeat.domain.notification.entity.Notification
import com.whattoeat.domain.notification.entity.NotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface NotificationRepository : JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = ["actor", "feed"])
    fun findByReceiverIdOrderByIdDesc(receiverId: Long, pageable: Pageable): Page<Notification>

    fun findByIdAndReceiverId(id: Long, receiverId: Long): Optional<Notification>

    fun countByReceiverIdAndIsReadFalse(receiverId: Long): Long

    @Modifying
    @Query("""
    update Notification n
    set n.feed = null
    where n.feed.id = :feedId
""")
    fun clearFeedReference(feedId: Long)

    @EntityGraph(attributePaths = ["actor", "feed", "restaurantList"])
    @Query(
        """
        SELECT n FROM Notification n
        WHERE n.receiver.id = :receiverId
          AND (:cursor IS NULL OR n.id < :cursor)
        ORDER BY n.id DESC
        """
    )
    fun findByReceiverIdWithCursor(
        @Param("receiverId") receiverId: Long,
        @Param("cursor") cursor: Long?,
        pageable: Pageable
    ): List<Notification>

    fun existsByReceiverIdAndActorIdAndFeedIdAndType(
        receiverId: Long,
        actorId: Long,
        feedId: Long,
        type: NotificationType
    ): Boolean

    fun existsByReceiverIdAndActorIdAndTypeAndFeedIsNullAndRestaurantListIsNull(
        receiverId: Long,
        actorId: Long,
        type: NotificationType
    ): Boolean

    fun existsByReceiverIdAndActorIdAndRestaurantListIdAndType(
        receiverId: Long,
        actorId: Long,
        restaurantListId: Long,
        type: NotificationType
    ): Boolean

    // 좋아요/팔로우/리스트 저장 취소 시 대응하는 알림을 지우는 용도. 호출부(FeedLikeService
    // 등) 주석 참고.
    fun deleteByReceiverIdAndActorIdAndFeedIdAndType(
        receiverId: Long,
        actorId: Long,
        feedId: Long,
        type: NotificationType
    )

    fun deleteByReceiverIdAndActorIdAndTypeAndFeedIsNullAndRestaurantListIsNull(
        receiverId: Long,
        actorId: Long,
        type: NotificationType
    )

    fun deleteByReceiverIdAndActorIdAndRestaurantListIdAndType(
        receiverId: Long,
        actorId: Long,
        restaurantListId: Long,
        type: NotificationType
    )
}
