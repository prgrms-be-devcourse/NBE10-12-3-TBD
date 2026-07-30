package com.whattoeat.domain.auth.dto

@JvmRecord
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)
