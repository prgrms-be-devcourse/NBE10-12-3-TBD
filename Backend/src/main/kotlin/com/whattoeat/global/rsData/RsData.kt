package com.whattoeat.global.rsData

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RsData<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
) {
    companion object {
        @JvmStatic
        fun <T> success(data: T?, message: String?): RsData<T> =
            RsData(true, data, message)

        @JvmStatic
        fun <T> success(data: T?): RsData<T> =
            RsData(true, data, null)

        @JvmStatic
        fun <T> failure(message: String?): RsData<T> =
            RsData(false, null, message)

        @JvmStatic
        fun <T> failure(data: T?, message: String?): RsData<T> =
            RsData(false, data, message)
    }
}
