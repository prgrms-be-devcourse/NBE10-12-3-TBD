package com.whattoeat.domain.feed.service;

import com.whattoeat.domain.comment.repository.CommentRepository;
import com.whattoeat.domain.feed.dto.request.FeedCreateRequest;
import com.whattoeat.domain.feed.dto.request.FeedUpdateRequest;
import com.whattoeat.domain.feed.dto.response.FeedDetailResponse;
import com.whattoeat.domain.feed.dto.response.FeedListResponse;
import com.whattoeat.domain.feed.entity.Feed;
import com.whattoeat.domain.feed.event.FeedCreatedEvent;
import com.whattoeat.domain.feed.repository.FeedRepository;
import com.whattoeat.domain.feedlike.repository.FeedLikeRepository;
import com.whattoeat.domain.follow.entity.Follow;
import com.whattoeat.domain.follow.repository.FollowRepository;
import com.whattoeat.domain.restaurant.repository.RestaurantRepository;
import com.whattoeat.domain.user.entity.Provider;
import com.whattoeat.domain.user.entity.User;
import com.whattoeat.global.exception.FeedNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@RecordApplicationEvents
public class FeedServiceTest {
    @MockitoBean
    FeedRepository feedRepository;

    @MockitoBean
    RestaurantRepository restaurantRepository;

    @MockitoBean
    ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    FollowRepository followRepository;

    @MockitoBean
    CommentRepository commentRepository;

    @MockitoBean
    FeedLikeRepository feedLikeRepository;

    @Autowired
    FeedService feedService;

    @Autowired
    ApplicationEvents applicationEvents;

    private User createTestUser() {
        return User.builder()
                .nickname("test")
                .email("test@test.com")
                .provider(Provider.LOCAL)
                .build();

    }

    private User createTestUser(Long id, String nickname) {
        User user = User.builder()
                .nickname(nickname)
                .email("user" + id + "@test.com")
                .provider(Provider.LOCAL)
                .build();

        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

    private Feed createTestFeed(Long id, User user, String content) {
        Feed feed = Feed.builder()
                .user(user)
                .content(content)
                .likeCount(0)
                .build();

        ReflectionTestUtils.setField(feed, "id", id);

        return feed;
    }

    @Test
    @DisplayName("피드 생성 성공")
    public void createFeed_success() throws IOException {
        User user = User.builder().nickname("test").email("test@test.com").provider(Provider.LOCAL).build();
        FeedCreateRequest feedCreateRequest = new FeedCreateRequest("맛집이네요", null);
        MultipartFile image = null;

        Feed savedFeed = Feed.builder()
                .user(user)
                .content(feedCreateRequest.content())
                .build();

        given(feedRepository.save(any())).willReturn(savedFeed);

        FeedDetailResponse result = feedService.createFeed(user, feedCreateRequest, image);

        assertThat(result.content()).isEqualTo("맛집이네요");
    }

    @Test
    @DisplayName("피드 생성 시 FeedCreatedEvent가 발행된다")
    public void createFeed_publishesFeedCreatedEvent() throws IOException {
        User user = User.builder().nickname("test").email("test@test.com").provider(Provider.LOCAL).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        FeedCreateRequest feedCreateRequest = new FeedCreateRequest("맛집이네요", null);
        MultipartFile image = null;

        Feed savedFeed = Feed.builder()
                .user(user)
                .content(feedCreateRequest.content())
                .build();
        ReflectionTestUtils.setField(savedFeed, "id", 10L);

        given(feedRepository.save(any())).willReturn(savedFeed);

        feedService.createFeed(user, feedCreateRequest, image);

        assertThat(applicationEvents.stream(FeedCreatedEvent.class))
                .anyMatch(event -> event.feedId().equals(10L) && event.authorId().equals(1L));
    }

    @Test
    @DisplayName("피드 목록 조회 성공")
    public void getFeed_success() {
        User user = createTestUser();
        Feed feed1 = createTestFeed(1L, user, "맛집1");
        Feed feed2 = createTestFeed(2L, user, "맛집2");
        PageRequest pageable = PageRequest.of(0, 10);
        given(feedRepository.findAllByOrderByIdDesc(pageable))
                .willReturn(new PageImpl<>(List.of(feed1, feed2), pageable, 2));
        given(commentRepository.countByFeedIds(any())).willReturn(List.of());

        given(feedLikeRepository.findLikedFeedIdsByUserIdAndFeedIds(any(), any()))
                .willReturn(List.of(feed1.getId()));

        Page<FeedListResponse> result = feedService.getFeeds(1L, null, null, pageable);

        assertThat(result.getContent().get(0).isLikedByMe()).isTrue();
        assertThat(result.getContent().get(1).isLikedByMe()).isFalse();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).content()).isEqualTo("맛집1");
    }

    @Test
    @DisplayName("피드 목록 조회 - 빈 결과")
    public void getFeed_empty() {
        PageRequest pageable = PageRequest.of(0, 10);
        given(feedRepository.findAllByOrderByIdDesc(pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<FeedListResponse> result = feedService.getFeeds(null, null, null, pageable);
        assertThat(result.getContent().isEmpty());
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("피드 수정 실패 - 작성자가 아닌 사람")
    public void updateFeed_notOwner() throws IOException {
        User owner = createTestUser(1L, "owner");
        User other = createTestUser(2L, "other");
        Feed feed = Feed.builder().user(owner).content("원본 내용").build();
        FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용", null, false);
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed));
        MultipartFile image = null;

        assertThatThrownBy(() -> feedService.updateFeed(1L, 2L, request, image))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("본인 피드만 수정할 수 있습니다.");
    }

    @Test
    @DisplayName("피드 수정 실패 - 존재하지 않는 필드")
    public void updateFeed_notFound() throws IOException {
        FeedUpdateRequest req = new FeedUpdateRequest("수정된 내용", null, false);
        MultipartFile image = null;

        given(feedRepository.findById(999L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> feedService.updateFeed(999L, 1L, req, image))
                .isInstanceOf(FeedNotFoundException.class)
                .hasMessageContaining("Feed not found");
    }

    @Test
    @DisplayName("피드 수정 성공")
    public void updateFeed_success() throws IOException {
        User user = createTestUser(1L, "testUser");
        Feed feed = Feed.builder().user(user).content("원본 내용").build();
        MultipartFile image = null;

        FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용", null, false);
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed));
        given(feedRepository.save(any())).willReturn(feed);

        FeedDetailResponse result = feedService.updateFeed(1L, 1L, request, image);
        assertThat(result.content()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("피드 수정 시 이미지 삭제 반영")
    public void updateFeed_deleteImage() throws IOException {
        User user = createTestUser(1L, "testUser");
        Feed feed = createTestFeed(1L, user, "내용");
        ReflectionTestUtils.setField(feed, "imageUrl", "/uploads/old.jpg");

        given(feedRepository.findById(1L)).willReturn(Optional.of(feed));
        given(feedRepository.save(any(Feed.class))).willReturn(feed);

        FeedUpdateRequest request = new FeedUpdateRequest("수정할 내용", null, true);
        FeedDetailResponse result = feedService.updateFeed(1L, 1L, request, null);

        assertThat(result.imageUrl()).isNull();
    }

    @Test
    @DisplayName("피드 삭제 실패 - 작성자가 아닌 사람")
    public void deleteFeed_notOwner() {
        User owner = createTestUser(1L, "owner");
        User other = createTestUser(2L, "other");
        Feed feed = Feed.builder().user(owner).content("맛집이네요").build();
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed));

        assertThatThrownBy(() -> feedService.deleteFeed(1L, 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("본인 피드만 삭제할 수 있습니다.");
    }

    @Test
    @DisplayName("피드 삭제 실패 - 존재하지 않는 필드")
    public void deleteFeed_notFound() {
        given(feedRepository.findById(999L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> feedService.deleteFeed(999L, 1L))
                .isInstanceOf(FeedNotFoundException.class)
                .hasMessageContaining("Feed not found");
    }

    @Test
    @DisplayName("피드 삭제 성공")
    public void deleteFeed_success() {
        User user = createTestUser(1L, "testUser");
        Feed feed = Feed.builder().user(user).content("맛집이네요").build();
        given(feedRepository.findById(1L)).willReturn(Optional.of(feed));
        feedService.deleteFeed(1L, 1L);
        verify(feedRepository).delete(feed);
    }

    @Test
    @DisplayName("팔로잉한 사용자의 피드만 조회한다")
    void getFollowingFeeds() {
        User user1 = createTestUser(1L, "user1");
        User user2 = createTestUser(2L, "user2");
        User user3 = createTestUser(3L, "user3");

        Follow follow = Follow.of(user1, user2);

        Feed user2Feed = createTestFeed(10L, user2, "user2 feed");
        Feed user3Feed = createTestFeed(20L, user3, "user3 feed");

        PageRequest pageable = PageRequest.of(0, 10);

        given(followRepository.findByFollower_Id(1L, Pageable.unpaged()))
                .willReturn(new PageImpl<>(List.of(follow)));

        given(feedRepository.findByUser_IdInOrderByIdDesc(List.of(2L), pageable))
                .willReturn(new PageImpl<>(List.of(user2Feed), pageable, 1));

        given(commentRepository.countByFeedIds(any())).willReturn(List.of());
        given(feedLikeRepository.findLikedFeedIdsByUserIdAndFeedIds(any(), any()))
                .willReturn(List.of());

        Page<FeedListResponse> result = feedService.getFollowingFeeds(user1.getId(), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent())
                .extracting(FeedListResponse::feedId)
                .contains(user2Feed.getId())
                .doesNotContain(user3Feed.getId());

        assertThat(result.getContent())
                .extracting(FeedListResponse::content)
                .contains("user2 feed")
                .doesNotContain("user3 feed");
    }

    @Test
    @DisplayName("팔로잉한 사용자의 피드만 제외하고 랜덤 추천 피드를 조회한다")
    void getRandomRecommendedFeeds() {
        User user1 = createTestUser(1L, "user1");
        User user2 = createTestUser(2L, "user2");
        User user3 = createTestUser(3L, "user3");

        Follow follow = Follow.of(user1, user2);

        Feed user1Feed = createTestFeed(10L, user1, "user1 feed");
        Feed user2Feed = createTestFeed(20L, user2, "user2 feed");
        Feed user3Feed = createTestFeed(30L, user3, "user3 feed");

        PageRequest pageable = PageRequest.of(0, 10);

        given(followRepository.findByFollower_Id(1L, Pageable.unpaged()))
                .willReturn(new PageImpl<>(List.of(follow)));

        given(feedRepository.findByUser_IdNotInOrderByIdDesc(List.of(2L), pageable))
                .willReturn(new PageImpl<>(List.of(user1Feed, user3Feed), pageable, 2));

        given(commentRepository.countByFeedIds(any())).willReturn(List.of());
        given(feedLikeRepository.findLikedFeedIdsByUserIdAndFeedIds(any(), any()))
                .willReturn(List.of());

        Page<FeedListResponse> result = feedService.getRandomRecommendedFeeds(user1.getId(), pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(FeedListResponse::feedId)
                .contains(user1Feed.getId(), user3Feed.getId())
                .doesNotContain(user2Feed.getId());

        assertThat(result.getContent())
                .extracting(FeedListResponse::content)
                .contains("user1 feed", "user3 feed")
                .doesNotContain("user2 feed");
    }

}
