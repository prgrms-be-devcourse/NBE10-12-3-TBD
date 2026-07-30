package com.whattoeat.domain.feed.service

import com.whattoeat.domain.comment.repository.CommentRepository
import com.whattoeat.domain.feed.dto.request.FeedCreateRequest
import com.whattoeat.domain.feed.dto.request.FeedUpdateRequest
import com.whattoeat.domain.feed.dto.response.FeedDetailResponse
import com.whattoeat.domain.feed.dto.response.FeedListResponse
import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feed.event.FeedCreatedEvent
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.feedlike.repository.FeedLikeRepository
import com.whattoeat.domain.follow.entity.Follow
import com.whattoeat.domain.follow.repository.FollowRepository
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.user.entity.Provider
import com.whattoeat.domain.user.entity.User
import com.whattoeat.global.exception.FeedNotFoundException
import java.time.LocalDateTime
import java.util.Optional
import java.util.function.Function
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.multipart.MultipartFile

@SpringBootTest
@RecordApplicationEvents
class FeedServiceTest {
    @MockitoBean
    private lateinit var feedRepository: FeedRepository

    @MockitoBean
    private lateinit var restaurantRepository: RestaurantRepository

    @MockitoBean
    private lateinit var clientRegistrationRepository: ClientRegistrationRepository

    @MockitoBean
    private lateinit var followRepository: FollowRepository

    @MockitoBean
    private lateinit var commentRepository: CommentRepository

    @MockitoBean
    private lateinit var feedLikeRepository: FeedLikeRepository

    @Autowired
    private lateinit var feedService: FeedService

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    private fun createTestUser(): User =
        User.builder()
            .nickname("test")
            .email("test@test.com")
            .provider(Provider.LOCAL)
            .build()

    private fun createTestUser(id: Long, nickname: String): User {
        val user =
            User.builder()
                .nickname(nickname)
                .email("user$id@test.com")
                .provider(Provider.LOCAL)
                .build()

        ReflectionTestUtils.setField(user, "id", id)

        return user
    }

    private fun createTestFeed(id: Long, user: User, content: String): Feed {
        val feed =
            Feed.builder()
                .user(user)
                .content(content)
                .likeCount(0)
                .build()

        ReflectionTestUtils.setField(feed, "id", id)

        return feed
    }

    @Test
    @DisplayName("피드 생성 성공")
    fun createFeed_success() {
        val user = User.builder().nickname("test")
            .email("user1@test.com")
            .provider(Provider.LOCAL)
            .build()
        val feedCreateRequest = FeedCreateRequest("맛집이네요", null)
        val image: MultipartFile? = null

        val savedFeed =
            Feed.builder()
                .user(user)
                .content(feedCreateRequest.content)
                .build()

        given(feedRepository.save(any())).willReturn(savedFeed)

        val result: FeedDetailResponse = feedService.createFeed(user, feedCreateRequest, image)

        assertThat(result.content).isEqualTo("맛집이네요")
    }

    @Test
    @DisplayName("피드 생성 시 FeedCreatedEvent가 발행된다")
    fun createFeed_publishesFeedCreatedEvent() {
        val user = User.builder().nickname("test")
            .email("user1@test.com")
            .provider(Provider.LOCAL)
            .build()
        ReflectionTestUtils.setField(user, "id", 1L)
        val feedCreateRequest = FeedCreateRequest("맛집이네요", null)
        val image: MultipartFile? = null

        val savedFeed =
            Feed.builder()
                .user(user)
                .content(feedCreateRequest.content)
                .build()
        ReflectionTestUtils.setField(savedFeed, "id", 10L)

        given(feedRepository.save(any())).willReturn(savedFeed)

        feedService.createFeed(user, feedCreateRequest, image)

        assertThat(applicationEvents.stream(FeedCreatedEvent::class.java))
            .anyMatch { event -> event.feedId == 10L && event.authorId == 1L }
    }

    @Test
    @DisplayName("피드 목록 조회 성공")
    fun getFeed_success() {
        val user = createTestUser()
        val feed1 = createTestFeed(1L, user, "맛집1")
        val feed2 = createTestFeed(2L, user, "맛집2")
        val pageable = PageRequest.of(0, 10)
        given(feedRepository.findAllByOrderByIdDesc(pageable))
            .willReturn(PageImpl(listOf(feed1, feed2), pageable, 2))
        given(commentRepository.countByFeedIds(any())).willReturn(emptyList())

        given(feedLikeRepository.findLikedFeedIdsByUserIdAndFeedIds(any(), any()))
            .willReturn(listOf(feed1.id))

        val result: Page<FeedListResponse> = feedService.getFeeds(1L, null, null, pageable)

        assertThat(result.content[0].isLikedByMe).isTrue()
        assertThat(result.content[1].isLikedByMe).isFalse()
        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].content).isEqualTo("맛집1")
    }

    @Test
    @DisplayName("피드 목록 조회 - 빈 결과")
    fun getFeed_empty() {
        val pageable = PageRequest.of(0, 10)
        given(feedRepository.findAllByOrderByIdDesc(pageable))
            .willReturn(PageImpl(emptyList(), pageable, 0))

        val result: Page<FeedListResponse> = feedService.getFeeds(null, null, null, pageable)
        assertThat(result.content.isEmpty())
        assertThat(result.totalElements).isZero()
    }

    @Test
    @DisplayName("피드 수정 실패 - 작성자가 아닌 사람")
    fun updateFeed_notOwner() {
        val owner = createTestUser(1L, "owner")
        val other = createTestUser(2L, "other")
        val feed = Feed.builder().user(owner).content("원본 내용").build()
        val request = FeedUpdateRequest("수정된 내용", null, false)
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed))
        val image: MultipartFile? = null

        assertThatThrownBy { feedService.updateFeed(1L, 2L, request, image) }
            .isInstanceOf(AccessDeniedException::class.java)
            .hasMessageContaining("본인 피드만 수정할 수 있습니다.")
    }

    @Test
    @DisplayName("피드 수정 실패 - 존재하지 않는 필드")
    fun updateFeed_notFound() {
        val req = FeedUpdateRequest("수정된 내용", null, false)
        val image: MultipartFile? = null

        given(feedRepository.findById(999L)).willReturn(Optional.empty())
        assertThatThrownBy { feedService.updateFeed(999L, 1L, req, image) }
            .isInstanceOf(FeedNotFoundException::class.java)
            .hasMessageContaining("Feed not found")
    }

    @Test
    @DisplayName("피드 수정 성공")
    fun updateFeed_success() {
        val user = createTestUser(1L, "testUser")
        val feed = Feed.builder().user(user).content("원본 내용").build()
        val image: MultipartFile? = null

        val request = FeedUpdateRequest("수정된 내용", null, false)
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed))
        given(feedRepository.save(any())).willReturn(feed)

        val result: FeedDetailResponse = feedService.updateFeed(1L, 1L, request, image)
        assertThat(result.content).isEqualTo("수정된 내용")
    }

    @Test
    @DisplayName("피드 수정 시 이미지 삭제 반영")
    fun updateFeed_deleteImage() {
        val user = createTestUser(1L, "testUser")
        val feed = createTestFeed(1L, user, "내용")
        ReflectionTestUtils.setField(feed, "imageUrl", "/uploads/old.jpg")

        given(feedRepository.findById(1L)).willReturn(Optional.of(feed))
        given(feedRepository.save(any(Feed::class.java))).willReturn(feed)

        val request = FeedUpdateRequest("수정할 내용", null, true)
        val result: FeedDetailResponse = feedService.updateFeed(1L, 1L, request, null)

        assertThat(result.imageUrl).isNull()
    }

    @Test
    @DisplayName("피드 삭제 실패 - 작성자가 아닌 사람")
    fun deleteFeed_notOwner() {
        val owner = createTestUser(1L, "owner")
        val other = createTestUser(2L, "other")
        val feed = Feed.builder().user(owner).content("맛집이네요").build()
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed))

        assertThatThrownBy { feedService.deleteFeed(1L, 2L) }
            .isInstanceOf(AccessDeniedException::class.java)
            .hasMessageContaining("본인 피드만 삭제할 수 있습니다.")
    }

    @Test
    @DisplayName("피드 삭제 실패 - 존재하지 않는 필드")
    fun deleteFeed_notFound() {
        given(feedRepository.findById(999L)).willReturn(Optional.empty())
        assertThatThrownBy { feedService.deleteFeed(999L, 1L) }
            .isInstanceOf(FeedNotFoundException::class.java)
            .hasMessageContaining("Feed not found")
    }

    @Test
    @DisplayName("피드 삭제 성공")
    fun deleteFeed_success() {
        val user = createTestUser(1L, "testUser")
        val feed = Feed.builder().user(user).content("맛집이네요").build()
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed))
        feedService.deleteFeed(1L, 1L)
        verify(feedRepository).delete(feed)
    }

    @Test
    @DisplayName("팔로잉한 사용자의 피드만 조회한다")
    fun getFollowingFeeds() {
        val user1 = createTestUser(1L, "user1")
        val user2 = createTestUser(2L, "user2")
        val user3 = createTestUser(3L, "user3")

        val follow = Follow.of(user1, user2)

        val user2Feed = createTestFeed(10L, user2, "user2 feed")
        val user3Feed = createTestFeed(20L, user3, "user3 feed")

        val pageable = PageRequest.of(0, 10)

        given(followRepository.findByFollower_Id(1L, Pageable.unpaged()))
            .willReturn(PageImpl(listOf(follow)))

        given(feedRepository.findByUser_IdInOrderByIdDesc(listOf(2L), pageable))
            .willReturn(PageImpl(listOf(user2Feed), pageable, 1))

        given(commentRepository.countByFeedIds(any())).willReturn(emptyList())
        given(feedLikeRepository.findLikedFeedIdsByUserIdAndFeedIds(any(), any()))
            .willReturn(emptyList())

        val result: Page<FeedListResponse> = feedService.getFollowingFeeds(user1.id!!, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content)
            .extracting(Function<FeedListResponse, Long?> { response -> response.feedId })
            .contains(user2Feed.id)
            .doesNotContain(user3Feed.id)

        assertThat(result.content)
            .extracting(Function<FeedListResponse, String> { response -> response.content })
            .contains("user2 feed")
            .doesNotContain("user3 feed")
    }

    @Test
    @DisplayName("팔로잉한 사용자의 피드만 제외하고 랜덤 추천 피드를 조회한다")
    fun getRandomRecommendedFeeds() {
        val user1 = createTestUser(1L, "user1")
        val user2 = createTestUser(2L, "user2")
        val user3 = createTestUser(3L, "user3")

        val follow = Follow.of(user1, user2)

        val user1Feed = createTestFeed(10L, user1, "user1 feed")
        val user2Feed = createTestFeed(20L, user2, "user2 feed")
        val user3Feed = createTestFeed(30L, user3, "user3 feed")

        val pageable = PageRequest.of(0, 10)

        given(followRepository.findByFollower_Id(1L, Pageable.unpaged()))
            .willReturn(PageImpl(listOf(follow)))

        given(feedRepository.findByUser_IdNotInOrderByIdDesc(listOf(2L), pageable))
            .willReturn(PageImpl(listOf(user1Feed, user3Feed), pageable, 2))

        given(commentRepository.countByFeedIds(any())).willReturn(emptyList())
        given(feedLikeRepository.findLikedFeedIdsByUserIdAndFeedIds(any(), any()))
            .willReturn(emptyList())

        val result: Page<FeedListResponse> =
            feedService.getRandomRecommendedFeeds(user1.id!!, pageable)

        assertThat(result.content).hasSize(2)
        assertThat(result.content)
            .extracting(Function<FeedListResponse, Long?> { response -> response.feedId })
            .contains(user1Feed.id, user3Feed.id)
            .doesNotContain(user2Feed.id)

        assertThat(result.content)
            .extracting(Function<FeedListResponse, String> { response -> response.content })
            .contains("user1 feed", "user3 feed")
            .doesNotContain("user2 feed")
    }
}
