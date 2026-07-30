package com.whattoeat.domain.notification.controller

import com.whattoeat.domain.notification.dto.NotificationResponse
import com.whattoeat.domain.notification.service.NotificationService
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    // 알림 목록 조회
    @GetMapping
    fun getNotifications(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PageableDefault(size = 20) pageable: Pageable
    ): RsData<List<NotificationResponse>> {
        val userId = userDetails.userId

        val notifications = notificationService.getNotifications(userId, pageable)

        val result = notifications.map { NotificationResponse(it) }.content

        return RsData.success(result, "알림 목록 조회가 완료되었습니다.")
    }

    // 알림 읽음 처리
    @PutMapping("/{id}/read")
    fun readNotification(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable id: Long
    ): RsData<NotificationResponse> {
        val userId = userDetails.userId

        val notification = notificationService.markAsRead(userId, id)

        return RsData.success(NotificationResponse(notification), "알림을 읽음 처리했습니다.")
    }
}
