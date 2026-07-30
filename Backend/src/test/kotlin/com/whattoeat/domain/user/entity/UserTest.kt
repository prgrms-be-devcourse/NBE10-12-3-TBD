package com.whattoeat.domain.user.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserTest {

    private fun createUser(nickname: String, profileImage: String?): User =
        User.builder()
            .loginId("testuser")
            .password("password")
            .nickname(nickname)
            .email("test@example.com")
            .provider(Provider.LOCAL)
            .profileImage(profileImage)
            .build()

    @Test
    fun updateProfile_닉네임과_프로필이미지를_변경한다() {
        val user = createUser("oldNickname", "old.jpg")

        user.updateProfile("newNickname", "new.jpg")

        assertThat(user.nickname).isEqualTo("newNickname")
        assertThat(user.profileImage).isEqualTo("new.jpg")
    }

    @Test
    fun updateProfile_닉네임만_변경하고_이미지는_유지한다() {
        val user = createUser("oldNickname", "existing.jpg")

        user.updateProfile("newNickname", null)

        assertThat(user.nickname).isEqualTo("newNickname")
        assertThat(user.profileImage).isEqualTo("existing.jpg")
    }

    @Test
    fun updateProfile_기존_이미지가_없을때_null을_유지한다() {
        val user = createUser("nickname", null)

        user.updateProfile("nickname", null)

        assertThat(user.profileImage).isNull()
    }
}
