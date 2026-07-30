package com.whattoeat.domain.user.entity

import com.whattoeat.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    loginId: String? = null,
    password: String? = null,
    kakaoId: String? = null,
    nickname: String,
    profileImage: String? = null,
    email: String,
    role: Role = Role.USER,
    provider: Provider
) : BaseEntity() {

    @Column(name = "login_id", length = 50, unique = true)
    var loginId: String? = loginId
        protected set

    @Column(length = 255)
    var password: String? = password
        protected set

    @Column(name = "kakao_id", length = 255, unique = true)
    var kakaoId: String? = kakaoId
        protected set

    @Column(length = 100, nullable = false)
    var nickname: String = nickname
        protected set

    @Column(name = "profile_image", length = 500)
    var profileImage: String? = profileImage
        protected set

    @Column(length = 100, nullable = false)
    var email: String = email
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    var role: Role = role
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    var provider: Provider = provider
        protected set

    fun updateProfile(nickname: String?, profileImage: String?) {
        if (nickname != null) this.nickname = nickname
        if (profileImage != null) this.profileImage = profileImage
    }

    fun updateEmail(email: String) {
        this.email = email
    }

    fun updatePassword(password: String) {
        this.password = password
    }

    fun updateLoginId(loginId: String) {
        this.loginId = loginId
    }

    // Lombok의 @Builder와 동일한 API를 유지해 다른 도메인(Java)의 기존 호출부를 그대로 지원한다.
    companion object {
        @JvmStatic
        fun builder(): UserBuilder = UserBuilder()
    }

    class UserBuilder internal constructor() {
        private var loginId: String? = null
        private var password: String? = null
        private var kakaoId: String? = null
        private var nickname: String? = null
        private var profileImage: String? = null
        private var email: String? = null
        private var role: Role? = null
        private var provider: Provider? = null

        fun loginId(loginId: String?) = apply { this.loginId = loginId }
        fun password(password: String?) = apply { this.password = password }
        fun kakaoId(kakaoId: String?) = apply { this.kakaoId = kakaoId }
        fun nickname(nickname: String?) = apply { this.nickname = nickname }
        fun profileImage(profileImage: String?) = apply { this.profileImage = profileImage }
        fun email(email: String?) = apply { this.email = email }
        fun role(role: Role?) = apply { this.role = role }
        fun provider(provider: Provider?) = apply { this.provider = provider }

        fun build(): User = User(
            loginId = loginId,
            password = password,
            kakaoId = kakaoId,
            nickname = requireNotNull(nickname) { "nickname must be set" },
            profileImage = profileImage,
            email = requireNotNull(email) { "email must be set" },
            role = role ?: Role.USER,
            provider = requireNotNull(provider) { "provider must be set" }
        )
    }
}
