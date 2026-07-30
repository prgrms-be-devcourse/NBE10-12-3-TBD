package com.whattoeat.domain.user.service

import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.Optional

@ExtendWith(SpringExtension::class)
class UserServiceKakaoLoginTest {

    @Mock
    private lateinit var userRepository: UserRepository

    // Kotlin의 UserService 생성자는 non-null 파라미터에 런타임 null 체크를 추가하므로,
    // @InjectMocks가 생성자를 호출할 때 모든 의존성이 mock으로 채워져 있어야 한다.
    @Mock
    private lateinit var followRepository: FollowRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks
    private lateinit var userService: UserService

    @Captor
    private lateinit var captor: ArgumentCaptor<User>

    private val kakaoId = "123456789"
    private val nickname = "nickname"
    private val profileImg = "img.jpg"
    private val email = "test@test.com"

    private lateinit var existUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        existUser = User.builder()
            .kakaoId(kakaoId)
            .nickname("old")
            .profileImage("old.jpg")
            .email(email)
            .provider(Provider.KAKAO)
            .role(Role.USER)
            .build()
    }

    @Test
    @DisplayName("신규 회원가입")
    fun kakaoLoginOrSignUp_newUser() {
        given(userRepository.findByKakaoId(kakaoId)).willReturn(Optional.empty())
        // Kotlin의 kakaoLoginOrSignUp()은 non-null User를 반환하도록 선언되어 있어
        // save()가 mock 기본값인 null을 반환하면 안 된다.
        given(userRepository.save(any<User>())).willAnswer { it.arguments[0] }

        userService.kakaoLoginOrSignUp(kakaoId, nickname, profileImg, email)

        then(userRepository).should().save(captor.capture())
        val savedUser = captor.value
        assertThat(savedUser.kakaoId).isEqualTo(kakaoId)
        assertThat(savedUser.provider).isEqualTo(Provider.KAKAO)
        assertThat(savedUser.nickname).isEqualTo(nickname)
    }

    @Test
    @DisplayName("기존 회원은 카카오 프로필로 덮어쓰지 않는다")
    fun kakaoLoginOrSignUp_existingUser_updateProfile() {
        given(userRepository.findByKakaoId(kakaoId)).willReturn(Optional.of(existUser))

        userService.kakaoLoginOrSignUp(kakaoId, "new_nickname", "new.jpg", email)
        assertThat(existUser.nickname).isEqualTo("old")
        assertThat(existUser.profileImage).isEqualTo("old.jpg")
    }

    @Test
    @DisplayName("프로필 이미지 null일 경우 기존 이미지 사용")
    fun kakaoLoginOrSignUp_nullUser_keepProfileImg() {
        given(userRepository.findByKakaoId(kakaoId)).willReturn(Optional.of(existUser))

        userService.kakaoLoginOrSignUp(kakaoId, "new_nickname", null, email)

        assertThat(existUser.nickname).isEqualTo("old")
        assertThat(existUser.profileImage).isEqualTo("old.jpg")
    }
}
