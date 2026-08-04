package com.whattoeat.domain.feed.dto.response

import com.whattoeat.domain.feed.entity.Feed
import com.whattoeat.domain.restaurant.entity.MoodTag
import java.time.LocalDateTime

@JvmRecord
data class FeedListResponse(
    val feedId: Long?,
    val content: String,
    val imageUrl: String?,
    // 프론트에서 피드 카드 클릭 시 /profile/{userId} 링크 만들기, 본인 글 여부 판별 가능
    val userId: Long?,
    val nickname: String,
    val profileImage: String?,
    val likeCount: Int,
    val isLikedByMe: Boolean,
    val commentCount: Long,
    // id만 있으면 추가 API 호출로 피드 목록 로딩이 느려지고, 반대로 name만 있으면 링크 불가
    val restaurantId: Long?,
    val restaurantName: String?,
    val moodTag: MoodTag?,
    val createdAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun from(feed: Feed, commentCount: Long, isLikedByMe: Boolean): FeedListResponse =
            FeedListResponse(
                feed.id,
                feed.content,
                feed.imageUrl,
                feed.user.id,
                feed.user.nickname,
                feed.user.profileImage,
                feed.likeCount,
                isLikedByMe,
                commentCount,
                feed.restaurant?.id,
                feed.restaurant?.name,
                feed.moodTag,
                feed.createdAt,
            )
    }
}
