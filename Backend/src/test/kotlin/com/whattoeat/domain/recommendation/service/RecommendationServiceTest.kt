package com.whattoeat.domain.recommendation.service

import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.feed.repository.MoodVoteCount
import com.whattoeat.domain.recommendation.dto.RecommendRequest
import com.whattoeat.domain.recommendation.dto.RecommendSort
import com.whattoeat.domain.restaurant.dto.RestaurantRequest
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListItemRepository
import com.whattoeat.global.exception.InvalidRecommendParameterException
import com.whattoeat.global.exception.RestaurantNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class RecommendationServiceTest {

    @Mock
    lateinit var restaurantRepository: RestaurantRepository

    @Mock
    lateinit var feedRepository: FeedRepository

    @Mock
    lateinit var restaurantListItemRepository: RestaurantListItemRepository

    @InjectMocks
    lateinit var recommendService: RecommendService

    private fun candidate(
        kakaoId: String,
        categoryName: String,
        lat: Double = 37.5,
        lng: Double = 127.0
    ) = RestaurantRequest.FromKakao(
        kakaoPlaceId = kakaoId,
        name = "식당$kakaoId",
        categoryName = categoryName,
        address = "수원시 팔달구 행궁동 1-1",
        roadAddress = null,
        region1 = "경기",
        region2 = "수원시",
        region3 = "행궁동",
        region4 = null,
        phone = null,
        lat = lat,
        lng = lng
    )

    private fun storedRestaurant(
        id: Long,
        kakaoPlaceId: String
    ): Restaurant {
        val restaurant = Restaurant(
            kakaoPlaceId = kakaoPlaceId,
            name = "저장된 식당$kakaoPlaceId",
            category = Category.KOREAN,
            address = "수원시 팔달구",
            roadAddress = null,
            region1 = "경기",
            region2 = "수원시",
            region3 = null,
            region4 = null,
            phone = null,
            lat = 37.5,
            lng = 127.0
        )

        ReflectionTestUtils.setField(restaurant, "id", id)

        return restaurant
    }

    private fun voteCount(
        id: Long,
        count: Long
    ): MoodVoteCount = object : MoodVoteCount {
        override val restaurantId: Long = id
        override val voteCount: Long = count
    }

    private fun request(
        candidates: List<RestaurantRequest.FromKakao>,
        category: Category? = null,
        sort: RecommendSort = RecommendSort.RANDOM,
        lat: Double? = null,
        lng: Double? = null,
        exclude: List<String> = emptyList(),
        mood: MoodTag? = null,
    ) = RecommendRequest(
        candidates = candidates,
        category = category,
        sort = sort,
        lat = lat,
        lng = lng,
        exclude = exclude,
        mood = mood
    )

    @Test
    fun `RAMDOM - 필터 없이 전체 반환`() {
        val result = recommendService.recommend(
            request(
                listOf(
                    candidate("1", "음식점 > 한식"),
                    candidate("2", "음식점 > 치킨"),
                    candidate("3", "카페 > 커피전문점")
                )
            )
        )

        assertThat(result.map { it.kakaoPlaceId }).containsExactlyInAnyOrder("1", "2", "3")
        assertThat(result.first { it.kakaoPlaceId == "2" }.category).isEqualTo(Category.CHICKEN)
        assertThat(result.first { it.kakaoPlaceId == "2" }.categoryLabel).isEqualTo("치킨")
    }

    @Test
    fun `카테고리 지정 시 매핑 결과 일치하는 후보 반환`() {
        val result = recommendService.recommend(
            request(
                listOf(
                    candidate("1", "음식점 > 한식"),
                    candidate("2", "카페 > 커피전문점"),
                    candidate("3", "음식점 > 카페 > 테마카페"),
                    candidate("4", "여가시설 > 보드카페")
                ),
                category = Category.CAFE
            )
        )

        assertThat(result.map { it.kakaoPlaceId }).containsExactlyInAnyOrder("2", "3")
    }

    @Test
    fun `중복 id 제거와 exclude 적용`() {
        val result = recommendService.recommend(
            request(
                listOf(
                    candidate("1", "음식점 > 한식"),
                    candidate("1", "음식점 > 한식"),
                    candidate("2", "음식점 > 치킨"),
                    candidate("3", "음식점 > 일식")
                ),
                exclude = listOf("2")
            )
        )

        assertThat(result.map { it.kakaoPlaceId }).containsExactlyInAnyOrder("1", "3")
    }

    @Test
    fun `전부 걸러지면 RestaurantNotFoundException`() {
        assertThatThrownBy {
            recommendService.recommend(
                request(listOf(candidate("1", "여가시설 > 만화방")))
            )
        }
            .isInstanceOf(RestaurantNotFoundException::class.java)
            .hasMessage("조건에 맞는 식당이 없습니다.")
    }

    @Test
    fun `DISTANCE - 거리 오름차순과 distanceMeter 계산`() {
        val result = recommendService.recommend(
            request(
                listOf(
                    candidate("far", "음식점 > 한식", lat = 37.51, lng = 127.01),
                    candidate("near", "음식점 > 한식", lat = 37.5001, lng = 127.0001)
                ),
                sort = RecommendSort.DISTANCE,
                lat = 37.5,
                lng = 127.0
            )
        )

        assertThat(result.map { it.kakaoPlaceId }).containsExactly("near", "far")
        assertThat(result[0].distanceMeter!!).isLessThan(result[1].distanceMeter!!)
    }

    @Test
    fun `DISTANCE + mood - mood 필터 적용 후 거리 정렬`() {
        val result = recommendService.recommend(
            request(
                listOf(
                    candidate("far-cafe", "카페 > 커피전문점", lat = 37.51, lng = 127.01),
                    candidate("near-chicken", "음식점 > 치킨", lat = 37.5001, lng = 127.0001)
                ),
                sort = RecommendSort.DISTANCE,
                lat = 37.5,
                lng = 127.0,
                mood = MoodTag.DATE
            )
        )

        // 가깝지만 mood 불일치인 치킨은 제외되고, 멀어도 mood 일치인 카페만 남음
        assertThat(result.map { it.kakaoPlaceId }).containsExactly("far-cafe")
    }

    @Test
    fun `DISTANCE인데 좌표 없으면 InvalidRecommendParameterException`() {
        assertThatThrownBy {
            recommendService.recommend(
                request(listOf(candidate("1", "음식점 > 한식")), sort = RecommendSort.DISTANCE)
            )
        }.isInstanceOf(InvalidRecommendParameterException::class.java)
    }

    @Test
    fun `좌표 범위 초과 또는 한쪽만 전달되면 InvalidRecommendParameterException`() {
        assertThatThrownBy {
            recommendService.recommend(
                request(listOf(candidate("1", "음식점 > 한식")), lat = 91.0, lng = 127.0)
            )
        }.isInstanceOf(InvalidRecommendParameterException::class.java)

        assertThatThrownBy {
            recommendService.recommend(
                request(listOf(candidate("1", "음식점 > 한식")), lat = 37.5)
            )
        }.isInstanceOf(InvalidRecommendParameterException::class.java)
    }

    @Test
    fun `mood가 있으면 분위기 룰에 맞는 후보만 반환`() {
        val candidates = listOf(
            candidate("1", "음식점 > 한식"),
            candidate("2", "카페 > 커피전문점")
        )

        given(
            restaurantRepository
                .findByKakaoPlaceIdIn(listOf("1", "2"))
        ).willReturn(emptyList())
        val result = recommendService.recommend(
            request(candidates = candidates, mood = MoodTag.DATE)
        )

        assertThat(result.map { it.kakaoPlaceId }).containsExactly("2")
    }

    @Test
    fun `mood 룰, 투표에 맞는 후보 없으면 전체 후보 반환`() {
        val candidates = listOf(
            candidate("1", "음식점 > 한식"),
            candidate("2", "음식점 > 아시아음식")
        )

        given(
            restaurantRepository
                .findByKakaoPlaceIdIn(listOf("1", "2"))
        ).willReturn(emptyList())
        val result = recommendService.recommend(
            request(candidates = candidates, mood = MoodTag.DATE)
        )

        assertThat(result.map { it.kakaoPlaceId }).containsExactlyInAnyOrder("1", "2")
    }

    @Test
    fun `피드와 리스트의 mood 투표 후보를 모두 추천에 반영한다`() {
        val candidates = listOf(
            candidate("1", "음식점 > 한식"),
            candidate("2", "음식점 > 중식"),
            candidate("3", "음식점 > 한식")
        )

        val restaurantIds = listOf(1L, 2L, 3L)
        given(restaurantRepository.findByKakaoPlaceIdIn(listOf("1", "2", "3")))
            .willReturn(
                listOf(
                    storedRestaurant(1L, "1"),
                    storedRestaurant(2L, "2"),
                    storedRestaurant(3L, "3")
                )
            )

        given(feedRepository.countMoodVotes(MoodTag.DATE, restaurantIds))
            .willReturn(listOf(voteCount(1L, 2L)))
        given(restaurantListItemRepository.countMoodVotes(MoodTag.DATE, restaurantIds))
            .willReturn(listOf(voteCount(2L, 3L)))
        val result = recommendService.recommend(
            request(candidates = candidates, mood = MoodTag.DATE)
        )
        assertThat(result.map { it.kakaoPlaceId }).containsExactlyInAnyOrder("1", "2")
        then(feedRepository).should().countMoodVotes(MoodTag.DATE, restaurantIds)
        then(restaurantListItemRepository).should().countMoodVotes(MoodTag.DATE, restaurantIds)

    }
}
