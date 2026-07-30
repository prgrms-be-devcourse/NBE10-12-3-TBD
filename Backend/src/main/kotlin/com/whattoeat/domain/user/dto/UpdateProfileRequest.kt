package com.whattoeat.domain.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(min = 1, max = 20) val nickname: String?,
    @field:Email @field:Size(max = 100) val email: String?,
    val currentPassword: String?,
    @field:Size(min = 8, max = 20) val newPassword: String?
)
