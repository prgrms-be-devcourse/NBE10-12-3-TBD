package com.whattoeat.domain.restaurant.service

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.HotPlaceQueryRepository
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.global.entity.BaseEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class TodayHotPlaceServiceTest {

    @Mock
    lateinit var hotPlaceQueryRepository: HotPlaceQueryRepository

    @Mock
    lateinit var restaurantRepository: RestaurantRepository

    @Mock
    lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    lateinit var valueOps: ValueOperations<String, String>

    private lateinit var service: TodayHotPlaceService

    @BeforeEach
    fun setUp() {
        service = TodayHotPlaceService(
            hotPlaceQueryRepository, restaurantRepository, redisTemplate
        )
        given(redisTemplate.opsForValue()).willReturn(valueOps)
    }

    /**
     * Kotlin에서 Mockito.any()가 null을 반환해 non-null 파라미터에 NPE를 던지는 문제를
     * 우회하기 위한 헬퍼. 반환 타입이 Kotlin non-null 이라 컴파일러가 콜사이드
     * null-check을 삽입하지 않는다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKt(clazz: Class<T>): T {
        Mockito.any(clazz)
        return null as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> eqKt(value: T): T {
        Mockito.eq(value)
        return null as T
    }

    private fun restaurant(id: Long, name: String): Restaurant {
        val r = Restaurant(
            "k$id", name, Category.KOREAN, "주소", "도로",
            "서울", "강남구", "역삼동", null, "02-0", 37.5, 127.0
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(r, id)
        return r
    }

    @Test
    fun `캐시 미스면 24h 쿼리로 TOP3 반환하고 캐시에 저장한다`() {
        given(valueOps.get("today-hot:v1")).willReturn(null)
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsSince(
                anyKt(LocalDateTime::class.java), eq(1), eq(3), eq(5), eq(3)
            )
        ).willReturn(listOf(10L, 20L, 30L))
        given(restaurantRepository.findAllById(listOf(10L, 20L, 30L)))
            .willReturn(listOf(restaurant(20L, "b"), restaurant(10L, "a"), restaurant(30L, "c")))

        val result = service.getTodayHotPlaces()

        assertThat(result).extracting<Long> { it.id }.containsExactly(10L, 20L, 30L)
        then(valueOps).should().set(eq("today-hot:v1"), anyString(), eq(10L), eq(TimeUnit.MINUTES))
    }

    @Test
    fun `신호 1개만 있으면 나머지 2개를 누적 인기로 채운다`() {
        given(valueOps.get("today-hot:v1")).willReturn(null)
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsSince(
                anyKt(LocalDateTime::class.java), anyInt(), anyInt(), anyInt(), eq(3)
            )
        ).willReturn(listOf(10L))
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsCumulative(
                eqKt(listOf(10L)), anyInt(), anyInt(), anyInt(), eq(2)
            )
        ).willReturn(listOf(20L, 30L))
        given(restaurantRepository.findAllById(listOf(10L, 20L, 30L)))
            .willReturn(listOf(restaurant(10L, "a"), restaurant(20L, "b"), restaurant(30L, "c")))

        val result = service.getTodayHotPlaces()

        assertThat(result).extracting<Long> { it.id }.containsExactly(10L, 20L, 30L)
    }

    @Test
    fun `신호 0개면 누적 인기 TOP3만 반환한다`() {
        given(valueOps.get("today-hot:v1")).willReturn(null)
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsSince(
                anyKt(LocalDateTime::class.java), anyInt(), anyInt(), anyInt(), eq(3)
            )
        ).willReturn(emptyList())
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsCumulative(
                eqKt(listOf(-1L)), anyInt(), anyInt(), anyInt(), eq(3)
            )
        ).willReturn(listOf(50L, 60L, 70L))
        given(restaurantRepository.findAllById(listOf(50L, 60L, 70L)))
            .willReturn(listOf(restaurant(50L, "x"), restaurant(60L, "y"), restaurant(70L, "z")))

        val result = service.getTodayHotPlaces()

        assertThat(result).extracting<Long> { it.id }.containsExactly(50L, 60L, 70L)
    }

    @Test
    fun `DB에 식당이 부족하면 있는 만큼만 반환하고 예외를 던지지 않는다`() {
        given(valueOps.get("today-hot:v1")).willReturn(null)
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsSince(
                anyKt(LocalDateTime::class.java), anyInt(), anyInt(), anyInt(), eq(3)
            )
        ).willReturn(emptyList())
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsCumulative(
                eqKt(listOf(-1L)), anyInt(), anyInt(), anyInt(), eq(3)
            )
        ).willReturn(listOf(50L))
        given(restaurantRepository.findAllById(listOf(50L)))
            .willReturn(listOf(restaurant(50L, "x")))

        val result = service.getTodayHotPlaces()

        assertThat(result).hasSize(1).extracting<Long> { it.id }.containsExactly(50L)
    }

    @Test
    fun `캐시 HIT면 쿼리를 돌지 않고 findAllById만 호출한다`() {
        given(valueOps.get("today-hot:v1")).willReturn("[10,20,30]")
        given(restaurantRepository.findAllById(listOf(10L, 20L, 30L)))
            .willReturn(listOf(restaurant(10L, "a"), restaurant(20L, "b"), restaurant(30L, "c")))

        val result = service.getTodayHotPlaces()

        assertThat(result).extracting<Long> { it.id }.containsExactly(10L, 20L, 30L)
        then(hotPlaceQueryRepository).should(never())
            .findTopRestaurantIdsSince(anyKt(LocalDateTime::class.java), anyInt(), anyInt(), anyInt(), anyInt())
    }

    @Test
    fun `Redis 실패시 DB 경로로 진행하고 예외를 던지지 않는다`() {
        given(valueOps.get("today-hot:v1")).willThrow(RuntimeException("redis down"))
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsSince(
                anyKt(LocalDateTime::class.java), anyInt(), anyInt(), anyInt(), eq(3)
            )
        ).willReturn(listOf(10L, 20L, 30L))
        given(restaurantRepository.findAllById(listOf(10L, 20L, 30L)))
            .willReturn(listOf(restaurant(10L, "a"), restaurant(20L, "b"), restaurant(30L, "c")))

        val result = service.getTodayHotPlaces()

        assertThat(result).hasSize(3)
    }

    @Test
    fun `DB 실패시 예외를 그대로 전파해 상위에서 5xx로 응답되게 한다`() {
        given(valueOps.get("today-hot:v1")).willReturn(null)
        given(
            hotPlaceQueryRepository.findTopRestaurantIdsSince(
                anyKt(LocalDateTime::class.java), anyInt(), anyInt(), anyInt(), anyInt()
            )
        ).willThrow(RuntimeException("db down"))

        assertThatThrownBy { service.getTodayHotPlaces() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("db down")
    }
}
