package com.whattoeat.domain.restaurant.controller;

import com.whattoeat.domain.restaurant.dto.RestaurantRequest;
import com.whattoeat.domain.restaurant.dto.RestaurantResponse;
import com.whattoeat.domain.restaurant.entity.Category;
import com.whattoeat.domain.restaurant.service.RestaurantService;
import com.whattoeat.domain.restaurant.service.TodayHotPlaceService;
import com.whattoeat.global.rsData.RsData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/restaurants")
class RestaurantController(
    private val restaurantService: RestaurantService,
    private val todayHotPlaceService: TodayHotPlaceService
) {
    // 오늘의 핫플 TOP 3 (인증 불필요)
    @GetMapping("/today-hot")
    fun getTodayHotPlaces(): RsData<List<RestaurantResponse.Recommend>> {
        val data = todayHotPlaceService.getTodayHotPlaces()
            .map { RestaurantResponse.Recommend(it) }
        return RsData.success(data, "오늘의 핫플 조회가 완료되었습니다.")
    }

    //식당 추천조회
    @GetMapping("/recommend")
    fun recommend(
            @RequestParam(required = false) category: Category?,
            @RequestParam(required = false) region1: String?,
            @RequestParam(required = false) region2: String?,
            @RequestParam(required = false) region3: String?,
            @RequestParam(required = false) region4: String?
    ): RsData<RestaurantResponse.Recommend> {
        val restaurant = RestaurantResponse.Recommend(
                restaurantService.recommend(
                    category,
                    region1,
                    region2,
                    region3,
                    region4
                )
        );
        return RsData.success(
            restaurant,
            "식당 추천이 완료되었습니다."
        )
    }

    // 식당 조회 (kakaoPlaceId로 단건 조회 or 전체 목록)
    @GetMapping
    fun getRestaurants(
        @RequestParam(required = false) kakaoPlaceId: String?
    ): RsData<*> {
        if (kakaoPlaceId != null && !kakaoPlaceId.isBlank()) {
            val restaurant = restaurantService.findByKakaoPlaceId(kakaoPlaceId)

            return RsData.success(
                RestaurantResponse.Recommend(restaurant),
                "식당 조회가 완료되었습니다."
            )
        }

        val result = restaurantService.findAll()
                .map{
                    restaurant -> RestaurantResponse.Recommend(restaurant)
                }

        return RsData.success(
            result,
            "저장된 식당 목록입니다."
        )
    }

    // 프론트에서 선택한 식당만 저장
    @PostMapping
    fun saveSelectedRestaurant(
            @Valid @RequestBody request: RestaurantRequest.FromKakao
    ) : RsData<RestaurantResponse.Recommend> {
        val restaurant = restaurantService.saveGetKakao(request)

        return RsData.success(
            RestaurantResponse.Recommend(restaurant),
                "식당이 저장되었습니다."
        )
    }

    //식당 상세 조회
    @GetMapping("/{id}")
    fun getRestaurantById(
        @PathVariable id: Long
    ): RsData<RestaurantResponse.Recommend> {
        val restaurant = restaurantService.findById(id)

        return RsData.success(
            RestaurantResponse.Recommend(restaurant),
                "식당 조회가 완료 되었습니다."
        )
    }

}
