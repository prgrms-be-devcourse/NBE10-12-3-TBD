package com.whattoeat.global.security

import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.core.GrantedAuthority

internal class KakaoOAuth2UserTest {
    private lateinit var kakaoUser: User
    private lateinit var oAuth2User: KakaoOAuth2User

    @BeforeEach
    fun setUp() {
        kakaoUser = User.builder()
            .kakaoId("123456789")
            .nickname("nickname")
            .profileImage("http://kakao.com/profile.jpg")
            .email("test@test.com")
            .provider(Provider.KAKAO)
            .role(Role.USER)
            .build()

        oAuth2User = KakaoOAuth2User(kakaoUser)
    }

    @Test
    @DisplayName("내부 유저 객체 반환")
    fun getUser_returns_inner_user() {
        assertThat(oAuth2User.user).isSameAs(kakaoUser)
        assertThat(oAuth2User.user.kakaoId).isEqualTo("123456789")
        assertThat(oAuth2User.user.nickname).isEqualTo("nickname")
    }

    @Test
    @DisplayName("getAttributes 빈객체 반환")
    fun getAttributes_returns_empty_map() {
        assertThat(oAuth2User.attributes).isEmpty()
    }

    @Test
    @DisplayName("일반유저 ROLE USER")
    fun getAuthorities_with_user_role() {
        val authorities: Collection<GrantedAuthority> = oAuth2User.authorities

        assertThat(authorities).hasSize(1)
        assertThat(authorities.toString()).contains("ROLE_USER")
        assertThat(authorities.size).isEqualTo(1)
    }

    @Test
    @DisplayName("관리자 ROLE ADMIN")
    fun getAuthorities_with_admin_role() {
        val adminUser = User.builder()
            .kakaoId("admin123")
            .nickname("admin")
            .email("admin@example.com")
            .provider(Provider.KAKAO)
            .role(Role.ADMIN)
            .build()
        val adminOAuth2User = KakaoOAuth2User(adminUser)

        val authorities: Collection<GrantedAuthority> = adminOAuth2User.getAuthorities()

        assertThat(authorities).hasSize(1)
        assertThat(authorities.toString()).contains("ROLE_ADMIN")
        assertThat(authorities.size).isEqualTo(1)
    }
}
