package com.whattoeat.global.security

import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.mock

@DisplayName("CustomUserDetails 테스트")
internal class CustomUserDetailsTest {
    private lateinit var userDetails: CustomUserDetails

    @BeforeEach
    fun setUp() {
        val mockUser = mock(User::class.java)
        given(mockUser.id).willReturn(1L)
        given(mockUser.loginId).willReturn("testUser")
        given(mockUser.password).willReturn("password")
        given(mockUser.role).willReturn(Role.USER)
        userDetails = CustomUserDetails(mockUser)
    }

    @Test
    @DisplayName("userId는 유저의 id를 반환")
    fun getUserId() {
        assertThat(userDetails.userId).isEqualTo(1L)
    }

    @Test
    @DisplayName("username은 loginId를 반환")
    fun getUsername() {
        assertThat(userDetails.username).isEqualTo("testUser")
    }

    @Test
    @DisplayName("authorities는 ROLE_USER를 반환")
    fun getAuthorities() {
        assertThat(userDetails.authorities)
            .extracting("authority")
            .containsExactly("ROLE_USER")
    }
}
