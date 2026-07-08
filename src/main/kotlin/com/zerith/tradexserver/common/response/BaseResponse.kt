package com.zerith.tradexserver.common.response

class BaseResponse<T> private constructor(
    val code: String,
    val message: String,
    val data: T?
) {
    companion object {
        fun <T> ok(data: T): BaseResponse<T> {
            return BaseResponse("OK", "ok", data)
        }

        fun ok(): BaseResponse<Unit> {
            return BaseResponse("OK", "ok", null)
        }

        fun <T> error(
            code: String,
            message: String
        ): BaseResponse<T> {
            return BaseResponse(code, message, null)
        }
    }
}