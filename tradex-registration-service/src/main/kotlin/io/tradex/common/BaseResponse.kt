package io.tradex.common

class BaseResponse<T> private constructor(
    val code: String,
    val message: String,
    val data: T?,
) {
    companion object {
        fun <T> ok(data: T): BaseResponse<T> = BaseResponse("OK", "ok", data)
        fun ok(): BaseResponse<Unit> = BaseResponse("OK", "ok", null)
        fun <T> error(code: String, message: String): BaseResponse<T> = BaseResponse(code, message, null)
    }
}
