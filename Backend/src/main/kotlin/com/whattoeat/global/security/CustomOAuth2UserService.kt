package com.whattoeat.global.security

import com.whattoeat.domain.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class CustomOAuth2UserService(private val userService: UserService) : DefaultOAuth2UserService() {
    private val log = LoggerFactory.getLogger(CustomOAuth2UserService::class.java)


    @Transactional
    @Throws(OAuth2AuthenticationException::class)
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        log.info(
            "[OAuth2] loadUser called. provider={}",
            userRequest.clientRegistration.registrationId
        )

        val providerType = userRequest.clientRegistration.registrationId.uppercase(Locale.getDefault())
        var oauthUserId = ""
        var nickname: String? = ""
        var profileImg: String? = ""
        var email: String? = ""

        val user = when (providerType) {
            "KAKAO" -> {
                val attributes: Map<String, Any> = oAuth2User.getAttributes()
                log.info("[OAuth2] kakao attributes={}", attributes)
                val attributesProperties = attributes.get("properties") as? Map<*, *>
                val kakaoAccount = attributes.get("kakao_account") as? Map<*, *>

                oauthUserId = oAuth2User.name
                nickname = attributesProperties?.get("nickname") as? String
                profileImg =attributesProperties?.get("profile_image") as? String
                email = kakaoAccount?.get("email") as? String

                log.info(
                    "[OAuth2] extracted. oauthUserId={}, nickname={}, email={}",
                    oauthUserId,
                    nickname,
                    email
                )
                userService.kakaoLoginOrSignUp(oauthUserId, nickname, profileImg, email)
            }

            else -> throw OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다: $providerType")
        }
        return KakaoOAuth2User(user)
    }
}
