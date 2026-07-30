package com.whattoeat.domain.comment.dto

import com.whattoeat.domain.comment.entity.Comment
import java.time.LocalDateTime

@JvmRecord
data class CommentResponse(
    val id: Long?,
    val content: String?,
    val userId: Long?,
    val nickname: String?,
    val createdAt: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun from(comment: Comment): CommentResponse {
            return CommentResponse(
                comment.id,
                comment.content,
                comment.user.id,
                comment.user.nickname,
                comment.createdAt
            )
        }
    }
}
