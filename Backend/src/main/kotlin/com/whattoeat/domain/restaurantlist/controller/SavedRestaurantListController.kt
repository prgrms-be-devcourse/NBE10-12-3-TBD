package com.whattoeat.domain.restaurantlist.controller

import com.whattoeat.domain.restaurantlist.dto.SavedRestaurantListResponse
import com.whattoeat.domain.restaurantlist.dto.SavedRestaurantListStatusResponse
import com.whattoeat.domain.restaurantlist.service.SavedRestaurantListService
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/restaurant_lists")
class SavedRestaurantListController(
    private val savedRestaurantListService: SavedRestaurantListService
) {
    @PostMapping("/{id}/save")
    @Operation(summary = "맛집 리스트 저장")
    fun save(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long
    ) : ResponseEntity<RsData<Void>> {
        val userId = userDetails.userId

        savedRestaurantListService.save(userId, id)

        return ResponseEntity.ok(
                RsData.success(null, "레스토랑 리스트를 저장했습니다.")
        )
    }

    @DeleteMapping("/{id}/save")
    @Operation(summary = "맛집 리스트 저장 취소")
    fun unsave(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long
    ) : ResponseEntity<RsData<Void>> {
        val userId = userDetails.userId;

        savedRestaurantListService.unsave(userId, id)

        return ResponseEntity.ok(
                RsData.success(null, "레스토랑 리스트 저장을 취소했습니다.")
        )
    }

    @GetMapping("/saved")
    @Operation(summary = "내가 저장한 리스트 조회")
    fun findMySavedLists(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @RequestParam(defaultValue = "0") page: Int,
            @RequestParam(defaultValue = "10") size: Int
    ) : ResponseEntity<RsData<Page<SavedRestaurantListResponse>>> {
        val userId = userDetails.userId

        val pageable = PageRequest.of(page, size)

        val response = savedRestaurantListService.findMySavedLists(userId, pageable)

        return ResponseEntity.ok(
                RsData.success(response, "저장한 레스토랑 리스트 조회가 완료되었습니다.")
        )
    }

    @GetMapping("/{id}/saved")
    fun isSaved(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long
    ) : ResponseEntity<RsData<SavedRestaurantListStatusResponse>> {
        val userId = userDetails.userId

        val saved = savedRestaurantListService.isSaved(userId, id)

        val response = SavedRestaurantListStatusResponse.of(id, saved)

        val message = if(saved) {
            "저장한 레스토랑 리스트입니다."
        } else {
            "저장하지 않은 레스토랑 리스트입니다."
        }

        return ResponseEntity.ok(
                RsData.success(response, message)
        )
    }
}
