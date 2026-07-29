package com.whattoeat.global.security

import com.whattoeat.domain.user.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class KakaoOAuth2User(val user: User) : OAuth2User {

    override fun getAttributes(): Map<String, Any> {
        return emptyMap()
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
    }

    override fun getName(): String {
        return user.id.toString()
    }
}
