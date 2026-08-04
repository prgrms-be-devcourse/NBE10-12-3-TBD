package com.whattoeat.domain.recommendation.service

import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.recommendation.dto.RecommendItem
import com.whattoeat.domain.recommendation.dto.RecommendRequest
import com.whattoeat.domain.recommendation.dto.RecommendSort
import com.whattoeat.domain.restaurant.dto.RestaurantRequest
import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.mapper.categoryLabel
import com.whattoeat.domain.restaurant.mapper.toCategory
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.restaurantlist.repository.RestaurantListItemRepository
import com.whattoeat.global.exception.InvalidRecommendParameterException
import com.whattoeat.global.exception.RestaurantNotFoundException
import org.springframework.stereotype.Service
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class RecommendService(
    private val restaurantRepository: RestaurantRepository,
    private val feedRepository: FeedRepository,
    private val restaurantListItemRepository: RestaurantListItemRepository
) {

    fun recommend(request: RecommendRequest): List<RecommendItem> {
        validateCoordinates(request.lat, request.lng)
        if (request.sort == RecommendSort.DISTANCE && (request.lat == null || request.lng == null)) {
            throw InvalidRecommendParameterException("거리순 정렬에는 lat, lng가 필요합니다.")
        }

        val seen = mutableSetOf<String>()
        val items = request.candidates
            .filter { it.kakaoPlaceId.isNotBlank() }                    // ID 없는 후보 제외
            .filter { seen.add(it.kakaoPlaceId) }                       // 카카오 ID 중복 제거
            .filter { isFoodOrCafe(it.categoryName) }                   // 만화방/놀이시설 등 제외
            .filter { matchesCategory(it.categoryName, request.category) } // 선택 카테고리 일치만
            .filter { it.kakaoPlaceId !in request.exclude }             // 이미 본 식당 제외
            .map { toItem(it, request.lat, request.lng) }

        if (items.isEmpty()) {
            throw RestaurantNotFoundException("조건에 맞는 식당이 없습니다.")
        }

        return when (request.sort) {
            RecommendSort.RANDOM -> filterByMoodScore(items, request.mood)
            // 거리순도 mood 필터를 먼저 적용한 뒤 거리 정렬 (mood+거리순 조합 일관성)
            RecommendSort.DISTANCE -> filterByMoodScore(items, request.mood)
                .sortedBy { it.distanceMeter ?: Int.MAX_VALUE }
        }
    }

    private fun filterByMoodScore(items: List<RecommendItem>, mood: MoodTag?): List<RecommendItem> {
        if (mood == null) return items.shuffled()

        val kakaoToDbId = restaurantRepository
            .findByKakaoPlaceIdIn(items.map { it.kakaoPlaceId })
            .mapNotNull { restaurant -> restaurant.id?.let { id -> restaurant.kakaoPlaceId to id } }.toMap()
        val dbIds = kakaoToDbId.values.toList()

        val votes = mutableMapOf<Long, Long>()

        if (dbIds.isNotEmpty()) {
            feedRepository.countMoodVotes(mood, dbIds)
                .forEach { votes.merge(it.restaurantId, it.voteCount, Long::plus) }
            restaurantListItemRepository.countMoodVotes(mood, dbIds)
                .forEach { votes.merge(it.restaurantId, it.voteCount, Long::plus) }
        }

        val matched = items.filter { item ->
            val ruleScore = moodRuleScore(mood, item.category, item.name, item.categoryName)
            val voteScore = votes[kakaoToDbId[item.kakaoPlaceId]] ?: 0L
            ruleScore + voteScore > 0
        }
        return (matched.ifEmpty { items }).shuffled()
    }

    // 1단계 분류가 음식점/카페인 것만 (여가시설 > 보드카페 같은 비카페 CE7 노이즈 제거)
    private fun isFoodOrCafe(categoryName: String?): Boolean {
        val top = categoryName.orEmpty().split(">").firstOrNull()?.trim()
        return top == "음식점" || top == "카페"
    }

    private fun matchesCategory(categoryName: String?, category: Category?): Boolean =
        category == null || category == Category.ETC || toCategory(categoryName) == category

    private fun toItem(candidate: RestaurantRequest.FromKakao, lat: Double?, lng: Double?): RecommendItem =
        RecommendItem(
            kakaoPlaceId = candidate.kakaoPlaceId,
            name = candidate.name,
            categoryName = candidate.categoryName,
            category = toCategory(candidate.categoryName),
            categoryLabel = categoryLabel(candidate.categoryName),
            distanceMeter = if (lat != null && lng != null) {
                haversineMeter(lat, lng, candidate.lat, candidate.lng)
            } else null
        )

    private fun haversineMeter(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val earthRadiusMeter = 6371000.0 // 지구 평균 반지름(6371km)을 미터로 나타낸 값
        val dLat = Math.toRadians(lat2 - lat1) // 두 지점 위도 차를 라디안으로 변환
        val dLng = Math.toRadians(lng2 - lng1) // 두 지점 경도 차를 라디안으로 변환

        // 구면 위 두 점의 각거리 중간값. cos(lat)을 곱하는 이유: 경도 1°의 실제 길이는 위도에 따라 달라지기 때문
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)

        // asin(√a) = 두 점 사이 중심각(라디안), 2 × R × 중심각 = 지표면 거리(m). roundToInt로 반올림
        return (2 * earthRadiusMeter * asin(sqrt(a))).roundToInt()
    }

    private fun validateCoordinates(lat: Double?, lng: Double?) {
        if (lat != null && lat !in -90.0..90.0) {
            throw InvalidRecommendParameterException("lat는 -90~90 사이여야 합니다.")
        }
        if (lng != null && lng !in -180.0..180.0) {
            throw InvalidRecommendParameterException("lng는 -180~180 사이여야 합니다.")
        }
        if ((lat == null) != (lng == null)) {
            throw InvalidRecommendParameterException("lat와 lng는 함께 전달해야 합니다.")
        }
    }
}