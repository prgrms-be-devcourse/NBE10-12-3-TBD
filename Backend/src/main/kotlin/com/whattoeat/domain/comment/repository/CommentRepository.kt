package com.whattoeat.domain.comment.repository

import com.whattoeat.domain.comment.entity.Comment
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByFeedId(feedId: Long): List<Comment>

    // FeedListResponse에 댓글 수 넣기 위한 카운트 쿼리 추가
    fun countByFeedId(feedId: Long): Long

    fun deleteAllByFeed_Id(feedId: Long)

    @Query("SELECT c.feed.id, COUNT(c) FROM Comment c WHERE c.feed.id IN :feedIds GROUP BY c.feed.id")
    fun countByFeedIds(@Param("feedIds") feedIds: List<Long>): List<Array<Any>>
}
