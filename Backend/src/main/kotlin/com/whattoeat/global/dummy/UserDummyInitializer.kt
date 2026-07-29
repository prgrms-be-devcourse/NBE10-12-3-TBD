package com.whattoeat.global.dummy

import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(2)
class UserDummyInitializer(
    private val userRepository: UserRepository,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String) {
        val count = userRepository.count()
        log.info("[Dummy] UserDummyInitializer started. current count={}", count)
        if (count > 0) {
            log.info("[Dummy] Users already exist. Skipping dummy insertion.")
            return
        }

        val dummies = listOf(
            User.builder()
                .loginId("foodie1")
                .password("\$2a\$10\$dummy")
                .nickname("맛집러버")
                .email("foodie1@example.com")
                .profileImage(null)
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build(),
            User.builder()
                .loginId("foodie2")
                .password("\$2a\$10\$dummy")
                .nickname("점심메뉴탐험가")
                .email("foodie2@example.com")
                .profileImage(null)
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build(),
            User.builder()
                .loginId("foodie3")
                .password("\$2a\$10\$dummy")
                .nickname("디저트매니아")
                .email("foodie3@example.com")
                .profileImage(null)
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build(),
        )

        userRepository.saveAll(dummies)
        log.info("[Dummy] Inserted {} dummy users.", dummies.size)
    }
}
