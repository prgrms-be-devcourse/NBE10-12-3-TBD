package com.whattoeat.domain.feed.dto.request

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.restaurant.entity.MoodTag
import com.whattoeat.domain.restaurant.entity.Restaurant
import com.whattoeat.domain.user.entity.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@JvmRecord
data class FeedCreateRequest(
    @field:NotBlank(message = "내용은 필수입니다.")
    @field:Size(max = 1000, message = "내용은 1000자를 넘을 수 없습니다.")
    val content: String,
    @field:Positive(message = "음수 Id는 올 수 없습니다.")
    val restaurantId: Long?,
    val moodTag: MoodTag?,
) {
    fun toEntity(user: User, restaurant: Restaurant?, imageUrl: String?): Feed =
        Feed.builder()
            .user(user)
            .restaurant(restaurant)
            .content(content)
            .imageUrl(imageUrl)
            .moodTag(moodTag)
            .build()
}
