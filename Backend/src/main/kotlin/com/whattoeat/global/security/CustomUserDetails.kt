package com.whattoeat.global.security

import com.whattoeat.domain.user.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class CustomUserDetails(val user: User) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))

    override fun getPassword(): String? = user.password

    override fun getUsername(): String =
        user.loginId ?: checkNotNull(user.id) { "인증 사용자의 id가 없습니다." }.toString()

    val userId: Long get() = requireNotNull(user.id)
}
