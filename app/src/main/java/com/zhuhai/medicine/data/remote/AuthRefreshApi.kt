package com.zhuhai.medicine.data.remote

import com.zhuhai.medicine.data.model.ApiResponse
import com.zhuhai.medicine.data.model.RefreshRequest
import com.zhuhai.medicine.data.model.RefreshResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 刷新 token 的独立接口声明。
 *
 * 为什么不复用 [ApiService]：那边全部是 `suspend` 挂起函数，
 * 而 OkHttp 的 [okhttp3.Authenticator.authenticate] 是**同步阻塞**回调，
 * 在里面没法调挂起函数。这里单独声明一个返回 `Call` 的同步版本，
 * 用 `.execute()` 在 OkHttp 的工作线程上直接跑。
 */
interface AuthRefreshApi {

    /**
     * 用 refresh_token 换新的 access_token。
     *
     * 后端返回体：`{code, message, data: {access_token, expires_in}, timestamp}`。
     * 注意后端**不返回新的 refresh_token**——它签发的是 7 天有效的固定 refresh_token，
     * 换 access_token 时沿用原来那个。
     */
    @POST("api/v1/auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<ApiResponse<RefreshResponse>>
}
