package com.whattoeat.domain.feed.dto.response

import org.springframework.data.domain.Page

@JvmRecord
data class FeedListPageResponse(
    val feeds: List<FeedListResponse>,
    val totalPages: Int,
    val totalElements: Long,
    val page: Int,
    val size: Int,
) {
    companion object {
        @JvmStatic
        fun from(page: Page<FeedListResponse>): FeedListPageResponse =
            FeedListPageResponse(
                page.content,
                page.totalPages,
                page.totalElements,
                page.number,
                page.size,
            )
    }
}
