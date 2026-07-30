package com.whattoeat.domain.auth.dto


@JvmRecord
data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val userProfile: AuthUserResponse
)
