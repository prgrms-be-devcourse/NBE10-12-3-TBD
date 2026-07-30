package com.whattoeat.domain.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@JvmRecord
data class LoginRequest(
    @field:NotBlank @field:Email val loginId: String,
    @field:NotBlank val password: String
)
