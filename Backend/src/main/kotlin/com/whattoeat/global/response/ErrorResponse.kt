package com.whattoeat.global.response

import org.springframework.http.HttpStatus

data class ErrorResponse(
    val status: Int,
    val message: String,
) {
    companion object {
        @JvmStatic
        fun of(status: HttpStatus, message: String): ErrorResponse =
            ErrorResponse(status.value(), message)
    }
}
