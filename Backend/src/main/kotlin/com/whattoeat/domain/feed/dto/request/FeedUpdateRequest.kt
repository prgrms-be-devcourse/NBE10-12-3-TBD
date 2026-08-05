package com.whattoeat.domain.feed.dto.request

import com.whattoeat.domain.restaurant.entity.MoodTag
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
    // null이면 기존 moodTag 유지 (수정 화면에서 선택하지 않으면 지워지지 않도록).
    // 사용자가 수정 화면에서 태그를 명시적으로 해제한 경우에는 moodTag가 null인 것만으로는
    // "유지"와 "삭제"를 구분할 수 없으므로 clearMoodTag로 삭제 의도를 별도로 전달받는다.
    val moodTag: MoodTag?,
    val clearMoodTag: Boolean = false,
)
