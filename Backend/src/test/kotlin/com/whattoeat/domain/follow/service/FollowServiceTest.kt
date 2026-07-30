package com.whattoeat.domain.follow.service

import com.whattoeat.domain.follow.entity.Follow
import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.Role
import com.whattoeat.domain.user.entity.User
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.AlreadyFollowingException
import com.whattoeat.global.exception.FollowNotFoundException
import com.whattoeat.global.exception.SelfFollowNotAllowedException
import java.util.function.Function
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class FollowServiceTest {
    @MockitoBean
    private lateinit var clientRegistrationRepository: ClientRegistrationRepository

    @Autowired
    private lateinit var followService: FollowService

    @Autowired
    private lateinit var followRepository: FollowRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    @DisplayName("팔로우 성공")
    fun follow() {
        val follower = saveUser("follower")
        val following = saveUser("following")

        val follow = followService.follow(follower.id!!, following.id!!)

        assertThat(follow.id).isNotNull()
        assertThat(follow.follower.id).isEqualTo(follower.id)
        assertThat(follow.following.id).isEqualTo(following.id)
        assertThat(followRepository.existsByFollower_IdAndFollowing_Id(follower.id!!, following.id!!))
            .isTrue()
    }

    @Test
    @DisplayName("자기 자신은 팔로우할 수 없다")
    fun followSelf() {
        val user = saveUser("user")

        assertThatThrownBy { followService.follow(user.id!!, user.id!!) }
            .isInstanceOf(SelfFollowNotAllowedException::class.java)
            .hasMessage("자기 자신을 팔로우할 수 없습니다.")
    }

    @Test
    @DisplayName("이미 팔로우 중이면 실패한다")
    fun followAlreadyFollowing() {
        val follower = saveUser("follower")
        val following = saveUser("following")
        followService.follow(follower.id!!, following.id!!)

        assertThatThrownBy { followService.follow(follower.id!!, following.id!!) }
            .isInstanceOf(AlreadyFollowingException::class.java)
            .hasMessage("이미 팔로우 중인 사용자입니다.")
    }

    @Test
    @DisplayName("언팔로우 성공")
    fun unfollow() {
        val follower = saveUser("follower")
        val following = saveUser("following")
        followService.follow(follower.id!!, following.id!!)

        followService.unfollow(follower.id!!, following.id!!)

        assertThat(followRepository.existsByFollower_IdAndFollowing_Id(follower.id!!, following.id!!))
            .isFalse()
    }

    @Test
    @DisplayName("팔로우 관계가 없으면 언팔로우에 실패한다")
    fun unfollowNotFound() {
        val follower = saveUser("follower")
        val following = saveUser("following")

        assertThatThrownBy { followService.unfollow(follower.id!!, following.id!!) }
            .isInstanceOf(FollowNotFoundException::class.java)
            .hasMessage("팔로우 관계가 존재하지 않습니다.")
    }

    @Test
    @DisplayName("팔로잉 목록을 조회한다")
    fun getFollowings() {
        val follower = saveUser("follower")
        val following1 = saveUser("following1")
        val following2 = saveUser("following2")
        followService.follow(follower.id!!, following1.id!!)
        followService.follow(follower.id!!, following2.id!!)

        val followings: Page<Follow> =
            followService.getFollowings(follower.id!!, PageRequest.of(0, 10))

        assertThat(followings.totalElements).isEqualTo(2)
        assertThat(followings.content)
            .extracting(Function<Follow, Long?> { follow -> follow.following.id })
            .containsExactlyInAnyOrder(following1.id, following2.id)
    }

    @Test
    @DisplayName("팔로워 목록을 조회한다")
    fun getFollowers() {
        val follower1 = saveUser("follower1")
        val follower2 = saveUser("follower2")
        val following = saveUser("following")
        followService.follow(follower1.id!!, following.id!!)
        followService.follow(follower2.id!!, following.id!!)

        val followers: Page<Follow> =
            followService.getFollowers(following.id!!, PageRequest.of(0, 10))

        assertThat(followers.totalElements).isEqualTo(2)
        assertThat(followers.content)
            .extracting(Function<Follow, Long?> { follow -> follow.follower.id })
            .containsExactlyInAnyOrder(follower1.id, follower2.id)
    }

    private fun saveUser(name: String): User =
        userRepository.save(
            User.builder()
                .loginId(name)
                .password("password")
                .kakaoId("$name-kakao")
                .nickname(name)
                .email("$name@test.com")
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .build(),
        )
}
