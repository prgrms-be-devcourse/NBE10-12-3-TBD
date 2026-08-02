package com.whattoeat.domain.feed.dto.response

@JvmRecord
data class FeedDeleteResponse(
    val message: String,
) {
    companion object {
        @JvmStatic
        fun of(): FeedDeleteResponse = FeedDeleteResponse("글을 성공적으로 삭제하였습니다.")
    }
}
