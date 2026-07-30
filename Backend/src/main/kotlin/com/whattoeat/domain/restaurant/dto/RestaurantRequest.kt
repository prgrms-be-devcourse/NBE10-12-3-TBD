package com.whattoeat.domain.restaurant.dto

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.jdbc.Work

class RestaurantRequest {

    // 프론트에서 카카오 검색할 때 보내는 요청
    data class KakaoSearch(
        @field:NotBlank(message = "검색어는 필수입니다.")
        val keyword: String,
        val lng: Double?,
        val lat: Double?,
        val radius: Int?,
        val page: Int?
    )

    // 카카오 응답을 Restaurant 저장용을 변환
    data class FromKakao(
        @field:NotBlank(message = "카카오 장소 ID는 필수입니다.")
        val kakaoPlaceId: String,
        @field:NotBlank(message = "식당명은 필수입니다.")
        val name: String,
        val categoryName: String?,
        @field:NotBlank(message = "주소는 필수입니다.")
        val address: String,
        val roadAddress: String?,
        val region1: String?,
        val region2: String?,
        val region3: String?,
        val region4: String?,
        val phone: String?,
        @field:NotNull(message = "위도는 필수입니다.")
        val lat: Double,
        @field:NotNull(message = "경도는 필수입니다.")
        val lng: Double,
    )
}
