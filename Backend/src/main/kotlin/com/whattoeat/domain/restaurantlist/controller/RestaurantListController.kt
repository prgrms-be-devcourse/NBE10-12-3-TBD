package com.whattoeat.domain.restaurantlist.controller

import com.whattoeat.domain.restaurantlist.dto.RestaurantListRequest
import com.whattoeat.domain.restaurantlist.dto.RestaurantListResponse
import com.whattoeat.domain.restaurantlist.service.RestaurantListService
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.security.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/lists")
class RestaurantListController (
    private val restaurantListService: RestaurantListService
) {

    // ============================== 내 정보 조회 =================================
    // 맛집 리스트 등록
    @PostMapping
    @Operation(summary = "맛집 리스트 등록")
    fun createRestaurantList(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
           @Valid @RequestBody req: RestaurantListRequest.RestaurantList
    ) : RsData<RestaurantListResponse.RestaurantLists>{
        val userId = userDetails.userId

        val restaurantList = restaurantListService.create(
                userId,
                req.title,
                req.description,
                req.moodTag
        )

        return RsData.success(
            RestaurantListResponse.RestaurantLists(restaurantList),
                "맛집 리스트가 생성되었습니다."
        )
    }

    // 맛집 리스트 다건 조회
    @GetMapping
    @Operation(summary = "맛집 리스트 다건 조회")
    fun getRestaurantLists(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @RequestParam(defaultValue = "0") page: Int,
            @RequestParam(defaultValue = "10") size: Int
    ) : RsData<RestaurantListResponse.RestaurantListsResponse> {
        val userId = userDetails.userId;

        val pageable = PageRequest.of(page, size)

        val result = restaurantListService.findAllByUserId(userId, pageable)

        val lists = result.content.map { RestaurantListResponse.RestaurantLists(it) }

        return RsData.success(
            RestaurantListResponse.RestaurantListsResponse(
                    lists,
                    result.getTotalPages(),
                    result.getTotalElements()
            ),
            "맛집 리스트 목록 조회가 완료되었습니다."
        )
    }

    // 맛집 리스트 단건 조회
    @GetMapping("/{id}")
    @Operation(summary = "맛집 리스트 단건 조회")
    fun getRestaurantList(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long
    ) : RsData<RestaurantListResponse.RestaurantListDetail> {
        val userId = userDetails.userId;

        val restaurantList = restaurantListService.findByIdAndUserId(id, userId)

        return RsData.success(
            RestaurantListResponse.RestaurantListDetail(restaurantList),
        "맛집 리스트 조회가 완료되었습니다."
        )
    }


    // ----------- RestaurantListItem -------------

    // 맛집 리스트 아이템 등록
    @PostMapping("/{id}/items")
    @Operation(summary = "맛집 리스트 아이템 등록")
    fun createRestaurantListItem(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable("id") listId: Long,
            @Valid @RequestBody  req: RestaurantListRequest.RestaurantListItem
    ) : RsData<RestaurantListResponse.RestaurantListItemDetail> {
        val userId = userDetails.userId;

        val item = restaurantListService.addItem(
                userId,
                listId,
                req.restaurantId,
                req.memo,
                req.orderIndex
        );

        return RsData.success(
            RestaurantListResponse.RestaurantListItemDetail(item),
                "맛집 리스트에 식당이 추가되었습니다."
        )
    }

    @PutMapping("/{id}")
    @Operation(summary = "식당 리스트 기본 정보 수정")
    fun updateRestaurantList(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long,
            @Valid @RequestBody req: RestaurantListRequest.RestaurantList
    ) : RsData<RestaurantListResponse.RestaurantListDetail>{
        val userId = userDetails.userId;

        val restaurantList = restaurantListService.update(
                id,
                userId,
                req.title,
                req.description,
                req.moodTag
        )

        return RsData.success(
            RestaurantListResponse.RestaurantListDetail(restaurantList),
                "리스트 정보가 변경되었습니다."
        )
    }


    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "식당 리스트 아이템 수정(순서/메모)")
    fun updateRestaurantListItem(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long,
            @PathVariable itemId: Long,
            @Valid @RequestBody req: RestaurantListRequest.RestaurantListItem
    ) : RsData<RestaurantListResponse.RestaurantListItemDetail> {
        val userId = userDetails.userId;

        val item = restaurantListService.updateItem(
                id, // listId
                itemId,
                userId,
                req.orderIndex,
                req.memo
        );

        return RsData.success(
            RestaurantListResponse.RestaurantListItemDetail(item),
                "리스트 아이템 정보가 변경되었습니다."
        )
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "맛집 리스트 아이템 삭제")
    fun deleteRestaurantListItem(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long, // listId
            @PathVariable itemId: Long
    ) : RsData<Void> {
        val userId = userDetails.userId;

        // 나중에 인증 추가 사용자일 경우에만 삭제가능하도록 수정
        restaurantListService.deleteItem(id, itemId, userId)

        return RsData.success(
                null,
                "리스트 아이템이 삭제되었습니다."
        )
    }

    // ============================== 전체 조회 =================================

    // 전체 맛집 리스트 다건 조회
    @GetMapping("/all")
    @Operation(summary = "전체 맛집 리스트 다건 조회")
    fun getAllRestaurantLists(
            @RequestParam(defaultValue = "0") page: Int,
            @RequestParam(defaultValue = "10") size: Int
    ) : RsData<RestaurantListResponse.RestaurantListsResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        val result = restaurantListService.findAll(pageable)

        val lists = result.content.map { RestaurantListResponse.RestaurantLists(it) }

        return RsData.success(
            RestaurantListResponse.RestaurantListsResponse(
                        lists,
                        result.totalPages,
                        result.totalElements
                ),
                "전체 맛집 리스트 목록 조회가 완료되었습니다."
        )
    }

    @GetMapping("/others")
    @Operation(summary = "다른 사용자의 맛집 리스트 다건 조회")
    fun getOtherRestaurantLists(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @RequestParam(defaultValue = "0") page: Int,
            @RequestParam(defaultValue = "10") size: Int
    ) : RsData<RestaurantListResponse.RestaurantListsResponse> {
        val userId = userDetails.userId;

        val pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        )

        val result = restaurantListService.findAllExceptUser(userId, pageable)

        val lists =
                result.content.map { RestaurantListResponse.RestaurantLists(it) }

        return RsData.success(
            RestaurantListResponse.RestaurantListsResponse(
                        lists,
                        result.totalPages,
                        result.totalElements
                ),
                "다른 사용자의 맛집 리스트 목록 조회가 완료되었습니다."
        )
    }

    // 전체 맛집 리스트 단건 조회
    @GetMapping("/all/{id}")
    @Operation(summary = "전체 맛집 리스트 단건 조회")
    fun  getAllRestaurantListsDetail(
            @PathVariable id : Long
    ) : RsData<RestaurantListResponse.RestaurantListDetail> {
        val restaurantList = restaurantListService.findById(id)

        return RsData.success(
            RestaurantListResponse.RestaurantListDetail(restaurantList),
                "전체 맛집 리스트 조회가 완료되었습니다."
        )
    }

    // ================= 리스트 복사 ====================
    @PostMapping("/{id}/copy")
    @Operation(summary = "맛집 리스트 복사")
    fun copyRestaurantList(
            @AuthenticationPrincipal userDetails: CustomUserDetails,
            @PathVariable id: Long
    ) : RsData<RestaurantListResponse.RestaurantListDetail> {
        val userId = userDetails.userId
        val copyList = restaurantListService.copyList(userId, id)

        return RsData.success(
            RestaurantListResponse.RestaurantListDetail(copyList),
                "맛집 리스트가 복사되었습니다."
        )
    }

}
