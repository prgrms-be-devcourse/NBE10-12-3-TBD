package com.whattoeat.global.dummy

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import com.whattoeat.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(3)
class FeedDummyInitializer(
    private val feedRepository: FeedRepository,
    private val userRepository: UserRepository,
    private val restaurantRepository: RestaurantRepository,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String) {
        val count = feedRepository.count()
        log.info("[Dummy] FeedDummyInitializer started. current count={}", count)
        if (count > 0) {
            log.info("[Dummy] Feeds already exist. Skipping dummy insertion.")
            return
        }

        val foodie1 = userRepository.findByLoginId("foodie1").orElse(null)
        val foodie2 = userRepository.findByLoginId("foodie2").orElse(null)
        val foodie3 = userRepository.findByLoginId("foodie3").orElse(null)
        val r1 = restaurantRepository.findByKakaoPlaceId("KPLACE_001").orElse(null)
        val r2 = restaurantRepository.findByKakaoPlaceId("KPLACE_002").orElse(null)
        val r3 = restaurantRepository.findByKakaoPlaceId("KPLACE_003").orElse(null)

        if (foodie1 == null || foodie2 == null || foodie3 == null || r1 == null || r2 == null || r3 == null) {
            log.warn("[Dummy] Required dummy users or restaurants not found. Skipping feed dummy insertion.")
            return
        }

        val dummies = listOf(
            Feed.builder()
                .user(foodie1)
                .restaurant(r1)
                .content("을지로 삼겹살 집 분위기도 좋고 고기도 두툼해서 최고였어요! 다음에 또 올게요.")
                .build(),
            Feed.builder()
                .user(foodie2)
                .restaurant(r2)
                .content("강남 초밥 맛집 인정입니다. 스시 초밥이 정말 신선했어요.")
                .build(),
            Feed.builder()
                .user(foodie3)
                .restaurant(r3)
                .content("홍대 파스타 집 소스가 진하고 면이 알단테라 맛있었어요.")
                .build(),
            Feed.builder()
                .user(foodie1)
                .restaurant(r2)
                .content("점심 특선으로 방문했는데 가성비 최고였습니다.")
                .build(),
            Feed.builder()
                .user(foodie3)
                .restaurant(r1)
                .content("을지로 데이트 코스로 추천해요. 분위기 짱!")
                .build(),
        )

        feedRepository.saveAll(dummies)
        log.info("[Dummy] Inserted {} dummy feeds.", dummies.size)
    }
}
