package com.whattoeat.global.security

import com.whattoeat.domain.user.entity.User
import lombok.Getter
import lombok.RequiredArgsConstructor
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.List

@Getter
@RequiredArgsConstructor
class CustomUserDetails(private val user: User) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))

    override fun getPassword(): String? =user.password

    override fun getUsername(): String = requireNotNull(user.loginId)

    val userId: Long get() = requireNotNull(user.id)
}
