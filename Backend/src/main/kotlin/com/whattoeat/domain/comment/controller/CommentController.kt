package com.whattoeat.domain.comment.controller

import com.whattoeat.domain.comment.dto.CommentRequest
import com.whattoeat.domain.comment.dto.CommentResponse
import com.whattoeat.domain.comment.service.CommentService
import com.whattoeat.global.rsData.RsData
import com.whattoeat.global.rsData.RsData.Companion.success
import com.whattoeat.global.security.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/feeds")
class CommentController(private val commentService: CommentService) {

    //댓글 조회
    @GetMapping("/{feedId}/comments")
    fun getComments(@PathVariable feedId: Long): ResponseEntity<RsData<List<CommentResponse>>> {
        val comments = commentService.getComments(feedId)
        return ResponseEntity.ok(success(
            comments,
            "댓글 목록 조회 성공"
        )
        )
    }

    //댓글 작성
    @PostMapping("/{feedId}/comments")
    fun createComment(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable feedId: Long,
        @RequestBody @Valid request: CommentRequest
    ): ResponseEntity<RsData<CommentResponse>> {
        val comment = commentService.createComment(feedId, userDetails.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(success(comment, "댓글 작성 성공"))
    }

    //댓글 삭제
    @DeleteMapping("/{feedId}/comments/{commentId}")
    fun deleteComment(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @PathVariable feedId: Long,
        @PathVariable commentId: Long
    ): ResponseEntity<RsData<Void>> {
        commentService.deleteComment(feedId, commentId, userDetails.userId)
        return ResponseEntity.ok(success(null, "댓글이 삭제되었습니다."))
    }
}
