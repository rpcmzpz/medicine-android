package com.zhuhai.medicine.data.remote

/**
 * 数据层的统一结果类型。
 *
 * 设计要点：
 * 1. 用 `sealed interface` 而不是抛异常——业务失败（如"用户名或密码错误"）是可预期结果，
 *    不是程序错误，用类型表达比用异常控制流更清晰。
 * 2. `out T` 是**协变**声明：`ApiResult<Medicine>` 可以当作 `ApiResult<Any>` 使用。
 *    因为 T 只出现在返回值位置（数据从数据层流向 UI），不会出现在入参位置。
 * 3. 失败分支都是 `ApiResult<Nothing>`。`Nothing` 是所有类型的子类型，
 *    所以 `BusinessError` 能直接赋给任意 `ApiResult<T>`，不必写成 `ApiResult<T>.BusinessError`。
 */
sealed interface ApiResult<out T> {

    /** 请求成功且 data 非空 */
    data class Success<out T>(val data: T) : ApiResult<T>

    /**
     * 服务端明确返回的业务失败。
     * @param code 后端 Result.code（不是 HTTP 状态码），如 40102=用户名或密码错误
     * @param message 可直接展示给用户的中文提示
     */
    data class BusinessError(val code: Int, val message: String) : ApiResult<Nothing>

    /** 网络层失败：断网、超时、DNS 解析失败、服务端未启动 */
    data class NetworkError(
        val message: String,
        val cause: Throwable? = null
    ) : ApiResult<Nothing>

    /** token 缺失 / 过期 / 无效，调用方应清除本地会话并跳登录 */
    data object Unauthorized : ApiResult<Nothing>
}
