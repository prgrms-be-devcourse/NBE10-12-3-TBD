package com.whattoeat.domain.auth.dto

import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import java.time.LocalDateTime

@JvmRecord
data class AuthUserResponse(
    val userId: Long?,
    val nickname: String?,
    val profileImage: String?,
    val email: String,
    val provider: Provider,
    val role: Role,
    val createAt: LocalDateTime?
) {
    companion object {
        fun from(user: User): AuthUserResponse = AuthUserResponse(
            user.id,
            user.getNickname(),
            user.getProfileImage(),
            user.getEmail(),
            user.getProvider(),
            user.getRole(),
            user.createdAt
        )
    }
}
