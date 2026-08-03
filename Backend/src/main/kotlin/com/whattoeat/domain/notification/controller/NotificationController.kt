package com.whattoeat.domain.notification.controller

import com.whattoeat.domain.notification.dto.NotificationCursorResponse
import com.whattoeat.domain.notification.dto.NotificationResponse
import com.whattoeat.domain.notification.dto.UnreadCountResponse
import com.whattoeat.domain.notification.service.NotificationService
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    @GetMapping
    fun getNotifications(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): RsData<NotificationCursorResponse> {
        val data = notificationService.getNotificationsByCursor(userDetails.userId, cursor, size)
        return RsData.success(data, "알림 목록 조회가 완료되었습니다.")
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): RsData<UnreadCountResponse> {
        val count = notificationService.getUnreadCount(userDetails.userId)
        return RsData.success(UnreadCountResponse(count), "안 읽은 알림 개수 조회가 완료되었습니다.")
    }

    @PutMapping("/{id}/read")
    fun readNotification(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable id: Long
    ): RsData<NotificationResponse> {
        val notification = notificationService.markAsRead(userDetails.userId, id)
        return RsData.success(NotificationResponse(notification), "알림을 읽음 처리했습니다.")
    }
}
