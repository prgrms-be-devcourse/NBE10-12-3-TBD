package com.whattoeat.domain.user.controller

import com.whattoeat.domain.user.dto.UpdateProfileRequest
import com.whattoeat.domain.user.dto.UserProfileResponse
import com.whattoeat.domain.user.service.UserService
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import com.whattoeat.global.upload.ImageUploadService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val imageUploadService: ImageUploadService
) {

    // 내 프로필 조회
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<RsData<UserProfileResponse>> =
        ResponseEntity.ok(
            RsData.success(
                userService.getUser(userDetails.userId, userDetails.userId),
                "내 프로필 조회 성공"
            )
        )

    // 프로필 조회
    @GetMapping("/{id}")
    fun getUser(
        @PathVariable id: Long,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<RsData<UserProfileResponse>> =
        ResponseEntity.ok(
            RsData.success(
                userService.getUser(id, userDetails.userId),
                "프로필 조회 성공"
            )
        )

    // 내 프로필 수정
    @PatchMapping("/me")
    fun updateProfile(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestBody @Valid request: UpdateProfileRequest
    ): ResponseEntity<RsData<UserProfileResponse>> {
        val userId = userDetails.userId
        val response = userService.updateProfile(userId, userId, request)
        return ResponseEntity.ok(RsData.success(response, "프로필이 수정되었습니다."))
    }

    // 프로필 이미지 변경
    @PatchMapping("/me/image")
    fun updateProfileImage(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestParam("image") image: MultipartFile
    ): ResponseEntity<RsData<UserProfileResponse>> {
        val imageUrl = imageUploadService.uploadProfileImage(image)
        val response = userService.updateProfileImage(userDetails.userId, imageUrl)
        return ResponseEntity.ok(RsData.success(response, "프로필 이미지가 변경되었습니다."))
    }
}
