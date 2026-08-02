package com.whattoeat.domain.restaurant.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.HotPlaceQueryRepository
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class TodayHotPlaceService(
    private val hotPlaceQueryRepository: HotPlaceQueryRepository,
    private val restaurantRepository: RestaurantRepository,
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    fun getTodayHotPlaces(): List<Restaurant> {
        val cachedIds = readCache()
        if (cachedIds != null) {
            return fetchInOrder(cachedIds)
        }

        val ids = computeTopIds()
        writeCache(ids)
        return fetchInOrder(ids)
    }

    private fun readCache(): List<Long>? {
        return try {
            val json = redisTemplate.opsForValue().get(CACHE_KEY) ?: return null
            objectMapper.readValue(json, object : TypeReference<List<Long>>() {})
        } catch (e: Exception) {
            log.warn("today-hot 캐시 읽기 실패, DB 경로로 진행: {}", e.message)
            null
        }
    }

    private fun writeCache(ids: List<Long>) {
        try {
            val json = objectMapper.writeValueAsString(ids)
            redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES)
        } catch (e: Exception) {
            log.warn("today-hot 캐시 저장 실패: {}", e.message)
        }
    }

    private fun computeTopIds(): List<Long> {
        val since = LocalDateTime.now().minusHours(24)
        val top24h = hotPlaceQueryRepository.findTopRestaurantIdsSince(
            since, WEIGHT_LIKE, WEIGHT_SAVE, WEIGHT_FEED, TOP_N
        )

        if (top24h.size >= TOP_N) {
            return top24h
        }

        val remaining = TOP_N - top24h.size
        val excludeIds = top24h.ifEmpty { listOf(EXCLUDE_SENTINEL) }
        val cumulative = hotPlaceQueryRepository.findTopRestaurantIdsCumulative(
            excludeIds, WEIGHT_LIKE, WEIGHT_SAVE, WEIGHT_FEED, remaining
        )

        log.info("today-hot fallback used: 24h={} cumulative={}", top24h.size, cumulative.size)
        return top24h + cumulative
    }

    private fun fetchInOrder(ids: List<Long>): List<Restaurant> {
        if (ids.isEmpty()) return emptyList()
        val byId = restaurantRepository.findAllById(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    companion object {
        private const val CACHE_KEY = "today-hot:v1"
        private const val CACHE_TTL_MINUTES = 10L
        private const val TOP_N = 3
        private const val WEIGHT_LIKE = 1
        private const val WEIGHT_SAVE = 3
        private const val WEIGHT_FEED = 5
        private const val EXCLUDE_SENTINEL = -1L
    }
}
