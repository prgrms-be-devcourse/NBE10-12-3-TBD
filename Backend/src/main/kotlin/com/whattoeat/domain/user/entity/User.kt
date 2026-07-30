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
    @Column(name = "login_id", length = 50, unique = true)
    var loginId: String? = null,

    @Column(length = 255)
    var password: String? = null,

    @Column(name = "kakao_id", length = 255, unique = true)
    var kakaoId: String? = null,

    @Column(length = 100, nullable = false)
    var nickname: String? = null,

    @Column(name = "profile_image", length = 500)
    var profileImage: String? = null,

    @Column(length = 100, nullable = false)
    var email: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    var role: Role = Role.USER,

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    var provider: Provider? = null
) : BaseEntity() {

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
            nickname = nickname,
            profileImage = profileImage,
            email = email,
            role = role ?: Role.USER,
            provider = provider
        )
    }
}
