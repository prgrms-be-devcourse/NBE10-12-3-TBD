package com.whattoeat.global.security

import com.whattoeat.domain.user.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(private val userRepository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(userId: String): UserDetails {
        val parsedUserId = userId.toLongOrNull()
            ?: throw UsernameNotFoundException("유저를 찾을 수 없습니다.")

        val user = userRepository.findById(parsedUserId)
            .orElseThrow {
                UsernameNotFoundException("유저를 찾을 수 없습니다.")
            }

        return CustomUserDetails(user)
    }
}
