package com.whattoeat.domain.feed.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@JvmRecord
data class FeedUpdateRequest(
    @field:NotBlank(message = "내용은 필수입니다.")
    @field:Size(max = 1000, message = "내용은 1000자를 넘을 수 없습니다.")
    val content: String,
    @field:Positive(message = "장소 Id는 음수일 수 없습니다.")
    val restaurantId: Long?,
    val deleteImage: Boolean,
)
