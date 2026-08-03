package com.whattoeat.domain.comment.service

import com.whattoeat.domain.comment.dto.CommentRequest
import com.whattoeat.domain.comment.dto.CommentResponse
import com.whattoeat.domain.comment.dto.CommentResponse.Companion.from
import com.whattoeat.domain.comment.entity.Comment
import com.whattoeat.domain.comment.repository.CommentRepository
import com.whattoeat.domain.feed.repository.FeedRepository
import com.whattoeat.domain.notification.event.CommentCreatedEvent
import com.whattoeat.domain.user.repository.UserRepository
import com.whattoeat.global.exception.CommentNotFoundException
import com.whattoeat.global.exception.FeedNotFoundException
import com.whattoeat.global.exception.UserNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CommentService(
    private val commentRepository: CommentRepository,
    private val feedRepository: FeedRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun getComments(feedId: Long): List<CommentResponse> =
        commentRepository.findByFeedId(feedId).map(CommentResponse::from)


    @Transactional
    fun createComment(feedId: Long, userId: Long, request: CommentRequest): CommentResponse {
        val feed = feedRepository.findById(feedId)
            .orElseThrow { FeedNotFoundException(feedId) }
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }
        val comment = Comment(
            feed = feed,
            user = user,
            content = requireNotNull(request.content){"댓글 내용이 없습니다."}
        )
        val saved = commentRepository.save(comment)
        eventPublisher.publishEvent(CommentCreatedEvent(feedId, saved.id!!, userId))
        return from(saved)
    }

    @Transactional
    fun deleteComment(feedId: Long, commentId: Long, userId: Long) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException(commentId) }
        if(comment.feed.id != feedId) throw CommentNotFoundException(commentId)
        if(comment.user.id != userId) throw AccessDeniedException("본인이 작성한 댓글만 삭제할 수 있습니다.")
        commentRepository.delete(comment)
    }
}
