package com.whattoeat.domain.user.repository

import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.config.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(JpaConfig::class)
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private fun createAndSave(loginId: String?, kakaoId: String?, email: String, nickname: String): User {
        val user = User.builder()
            .loginId(loginId)
            .kakaoId(kakaoId)
            .nickname(nickname)
            .email(email)
            .provider(if (loginId != null) Provider.LOCAL else Provider.KAKAO)
            .build()
        return entityManager.persistAndFlush(user)
    }

    @Test
    fun findByLoginId_성공() {
        createAndSave("user1", null, "user1@test.com", "nick1")

        val result = userRepository.findByLoginId("user1")

        assertThat(result).isPresent
        assertThat(result.get().loginId).isEqualTo("user1")
        assertThat(result.get().email).isEqualTo("user1@test.com")
    }

    @Test
    fun findByLoginId_없으면_empty() {
        val result = userRepository.findByLoginId("nonexistent")

        assertThat(result).isEmpty
    }

    @Test
    fun findByKakaoId_성공() {
        createAndSave(null, "kakao123", "kakao@test.com", "kakaoNick")

        val result = userRepository.findByKakaoId("kakao123")

        assertThat(result).isPresent
        assertThat(result.get().kakaoId).isEqualTo("kakao123")
    }

    @Test
    fun findByKakaoId_없으면_empty() {
        val result = userRepository.findByKakaoId("notFound")

        assertThat(result).isEmpty
    }

    @Test
    fun findByEmail_성공() {
        createAndSave("user2", null, "find@test.com", "nick2")

        val result = userRepository.findByEmail("find@test.com")

        assertThat(result).isPresent
        assertThat(result.get().email).isEqualTo("find@test.com")
    }

    @Test
    fun findByEmail_없으면_empty() {
        val result = userRepository.findByEmail("nobody@test.com")

        assertThat(result).isEmpty
    }

    @Test
    fun existsByLoginId_존재하면_true() {
        createAndSave("existUser", null, "exist@test.com", "nick3")

        assertThat(userRepository.existsByLoginId("existUser")).isTrue
    }

    @Test
    fun existsByLoginId_없으면_false() {
        assertThat(userRepository.existsByLoginId("nobody")).isFalse
    }

    @Test
    fun existsByNickname_존재하면_true() {
        createAndSave("user3", null, "user3@test.com", "uniqueNick")

        assertThat(userRepository.existsByNickname("uniqueNick")).isTrue
    }

    @Test
    fun existsByNickname_없으면_false() {
        assertThat(userRepository.existsByNickname("unknownNick")).isFalse
    }

    @Test
    fun save_시_createdAt이_자동_설정된다() {
        val user = createAndSave("user4", null, "user4@test.com", "nick4")

        assertThat(user.createdAt).isNotNull
    }
}
