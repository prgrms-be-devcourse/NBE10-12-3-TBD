package com.whattoeat.domain.feedlike.dto

@JvmRecord
data class FeedLikeResponse(
    val feedId: Long?,
    val likeCount: Int?,
    val isLikedByMe: Boolean,
) {
    companion object {
        @JvmStatic
        fun of(feedId: Long?, likeCount: Int?, isLikedByMe: Boolean): FeedLikeResponse =
            FeedLikeResponse(feedId, likeCount, isLikedByMe)
    }
}
