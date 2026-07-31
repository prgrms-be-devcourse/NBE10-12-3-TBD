package com.whattoeat.domain.comment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@JvmRecord
data class CommentRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val content: String?
)