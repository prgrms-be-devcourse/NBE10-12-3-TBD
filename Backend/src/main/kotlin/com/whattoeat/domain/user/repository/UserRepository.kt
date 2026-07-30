package com.whattoeat.domain.user.repository

import com.whattoeat.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {

    fun findByLoginId(loginId: String): Optional<User>

    fun findByKakaoId(kakaoId: String): Optional<User>

    fun findByEmail(email: String): Optional<User>

    fun existsByLoginId(loginId: String): Boolean

    fun existsByNickname(nickname: String): Boolean

    fun existsByEmail(email: String): Boolean
}
