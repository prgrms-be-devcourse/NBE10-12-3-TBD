package com.whattoeat.global.exception

class CommentNotFoundException(id: Long) : RuntimeException("Comment not found: $id")
