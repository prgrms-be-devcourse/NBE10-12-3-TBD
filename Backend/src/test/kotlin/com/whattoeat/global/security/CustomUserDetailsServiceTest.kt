package com.whattoeat.global.security

import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("CustomUserDetailsService 테스트")
internal class CustomUserDetailsServiceTest {
    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var customUserDetailsService: CustomUserDetailsService

    @Test
    @DisplayName("존재하는 userId로 조회하면 CustomUserDetails 반환")
    fun v1() {
        given(userRepository.findById(1L)).willReturn(
            Optional.of(mock(User::class.java))
        )

        val result = customUserDetailsService.loadUserByUsername("1")

        assertThat(result).isInstanceOf(CustomUserDetails::class.java)
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 UsernameNotFoundException 발생")
    fun v2() {
        given(userRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy{
            customUserDetailsService.loadUserByUsername("999")
        }.isInstanceOf(UsernameNotFoundException::class.java)
    }
}
