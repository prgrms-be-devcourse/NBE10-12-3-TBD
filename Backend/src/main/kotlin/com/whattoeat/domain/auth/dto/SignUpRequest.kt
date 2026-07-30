package com.whattoeat.domain.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@JvmRecord
data class SignUpRequest(
    @field:NotBlank @field:Email @field:Size(max = 100) val loginId: String,
    @field:NotBlank @field:Size(min = 8, max = 20) val password: String,
    @field:NotBlank @field:Size(max = 20) val nickname: String
)
