package com.whattoeat.domain.feed.dto.response

import com.whattoeat.domain.feed.entity.Feed
import java.time.LocalDateTime

@JvmRecord
data class FeedDetailResponse(
    val feedId: Long?,
    val content: String,
    val imageUrl: String?,
    val nickname: String,
    val profileImage: String?,
    val likeCount: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val restaurantId: Long?,
    val restaurantName: String?,
) {
    companion object {
        @JvmStatic
        fun from(feed: Feed): FeedDetailResponse =
            FeedDetailResponse(
                feed.id,
                feed.content,
                feed.imageUrl,
                feed.user.nickname,
                feed.user.profileImage,
                feed.likeCount,
                feed.createdAt,
                feed.updatedAt,
                feed.restaurant?.id,
                feed.restaurant?.name,
            )
    }
}
