package com.zhuhai.medicine.data.remote

import com.google.gson.Gson
import com.zhuhai.medicine.data.model.ApiResponse
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * 把一次 Retrofit 调用收敛成 [ApiResult]。
 *
 * 需要分两条路径判断成败，这是本项目最容易踩的坑：
 * 1. **HTTP 非 2xx**：Retrofit 直接抛 [HttpException]，函数体拿不到返回值。
 *    401 来自 JwtInterceptor；400/500 来自 GlobalExceptionHandler。
 * 2. **HTTP 200 但业务失败**：不抛异常，要看 body 里的 `code != 200`。
 */
private const val CODE_UNAUTHORIZED = 40100
private const val CODE_TOKEN_EXPIRED = 40101
private val gson = Gson()

/** 后端错误体只取需要的两个字段，忽略其余 */
private data class ErrorBody(val code: Int = 0, val message: String? = null)

/**
 * 适用于返回数据非空的接口。
 * 成功但 data 为 null 视为服务端异常——写操作请用 [apiCallUnit]。
 */
suspend fun <T : Any> apiCall(block: suspend () -> ApiResponse<T>): ApiResult<T> {
    return try {
        val response = block()
        when {
            response.isSuccess && response.data != null ->
                ApiResult.Success(response.data)

            response.isSuccess ->
                ApiResult.BusinessError(50000, response.message ?: "服务端未返回数据")

            response.code == CODE_UNAUTHORIZED || response.code == CODE_TOKEN_EXPIRED ->
                ApiResult.Unauthorized

            else ->
                ApiResult.BusinessError(response.code, response.message ?: "请求失败")
        }
    } catch (e: CancellationException) {
        // 协程被取消时必须原样抛出，否则取消信号被吞、页面退出后请求仍在跑
        throw e
    } catch (e: Exception) {
        mapException(e)
    }
}

/**
 * 适用于后端 `Result.success(null)` 的写操作（加购物车、取消订单等）。
 * 这类接口成功时 data 就是 null，不能用 [apiCall] 判空。
 */
suspend fun apiCallUnit(block: suspend () -> ApiResponse<Unit>): ApiResult<Unit> {
    return try {
        val response = block()
        when {
            response.isSuccess -> ApiResult.Success(Unit)

            response.code == CODE_UNAUTHORIZED || response.code == CODE_TOKEN_EXPIRED ->
                ApiResult.Unauthorized

            else ->
                ApiResult.BusinessError(response.code, response.message ?: "请求失败")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        mapException(e)
    }
}

private fun mapException(e: Exception): ApiResult<Nothing> = when (e) {
    is HttpException -> mapHttpException(e)
    is IOException -> ApiResult.NetworkError(
        message = when (e) {
            is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
            is java.net.UnknownHostException -> "无法连接服务器，请检查网络"
            else -> e.message ?: "网络异常"
        },
        cause = e
    )
    else -> ApiResult.NetworkError(e.message ?: "未知错误", e)
}

private fun mapHttpException(e: HttpException): ApiResult<Nothing> {
    if (e.code() == 401) return ApiResult.Unauthorized

    // 400/500 的 body 也是 Result 结构，里面的 message 才是给用户看的文案
    val body = runCatching {
        e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            ?.let { gson.fromJson(it, ErrorBody::class.java) }
    }.getOrNull()

    return ApiResult.BusinessError(
        code = body?.code?.takeIf { it != 0 } ?: e.code(),
        message = body?.message?.takeIf { it.isNotBlank() } ?: "请求失败（HTTP ${e.code()}）"
    )
}
