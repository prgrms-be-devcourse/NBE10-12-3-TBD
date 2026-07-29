package com.whattoeat.global.dummy

import com.whattoeat.domain.restaurant.entity.Category
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.restaurant.repository.RestaurantRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(1)
class RestaurantDummyInitializer(
    private val restaurantRepository: RestaurantRepository,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String) {
        val count = restaurantRepository.count()
        log.info("[Dummy] RestaurantDummyInitializer started. current count={}", count)
        if (count > 0) {
            log.info("[Dummy] Restaurants already exist. Skipping dummy insertion.")
            return
        }

        val dummies = listOf(
            Restaurant(
                "KPLACE_001", "을지삼겹", Category.KOREAN,
                "서울 중구 을지로 00", "서울 중구 을지로 00길 0", "서울", "중구", "을지로동", null,
                "02-0000-0001", 37.5665, 126.9780,
            ),
            Restaurant(
                "KPLACE_002", "맛있는 초밥집", Category.JAPANESE,
                "서울 강남구 테헤란로 00", "서울 강남구 테헤란로 00길 0", "서울", "강남구", "역삼1동", null,
                "02-0000-0002", 37.5012, 127.0396,
            ),
            Restaurant(
                "KPLACE_003", "이탈리안 파스타", Category.WESTERN,
                "서울 마포구 홍익로 00", "서울 마포구 홍익로 00길 0", "서울", "마포구", "서교동", null,
                "02-0000-0003", 37.5575, 126.9245,
            ),
            Restaurant(
                "KPLACE_004", "중화루", Category.CHINESE,
                "서울 종로구 종로 00", "서울 종로구 종로 00길 0", "서울", "종로구", "종로1.2.3.4가동", null,
                "02-0000-0004", 37.5700, 126.9820,
            ),
            Restaurant(
                "KPLACE_005", "카페 브레드", Category.CAFE,
                "서울 성동구 서울숲 00", "서울 성동구 서울숲 00길 0", "서울", "성동구", "성수1가1동", null,
                "02-0000-0005", 37.5445, 127.0437,
            ),
        )

        restaurantRepository.saveAll(dummies)
    }
}
