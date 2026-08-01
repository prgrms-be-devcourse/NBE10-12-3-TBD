package com.whattoeat.domain.notification.repository

import com.whattoeat.domain.notification.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface NotificationRepository : JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = ["actor", "feed"])
    fun findByReceiverIdOrderByIdDesc(receiverId: Long, pageable: Pageable): Page<Notification>

    fun findByIdAndReceiverId(id: Long, receiverId: Long): Optional<Notification>

    // Kotlin에서는 boolean 필드명이 "isRead"로 유지되므로(자바의 read 필드 + isRead() 게터와 달리)
    // 파생 쿼리 메서드명도 프로퍼티명(isRead)에 맞춰야 한다.
    fun countByReceiverIdAndIsReadFalse(receiverId: Long): Long

    @Modifying
    @Query("""
    update Notification n
    set n.feed = null
    where n.feed.id = :feedId
""")
    fun clearFeedReference(feedId: Long)
}
