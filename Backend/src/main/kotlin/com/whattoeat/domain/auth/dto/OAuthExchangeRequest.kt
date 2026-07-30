package com.whattoeat.domain.auth.dto

import jakarta.validation.constraints.NotBlank

@JvmRecord
data class OAuthExchangeRequest(
    @field:NotBlank val code: String
)
