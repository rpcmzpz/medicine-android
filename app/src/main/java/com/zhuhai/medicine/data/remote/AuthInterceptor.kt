package com.zhuhai.medicine.data.remote

import com.zhuhai.medicine.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器：请求方向补 `Authorization` 头，响应方向处理 401。
 *
 * 挂在应用拦截器位置（addInterceptor），一次请求只执行一次，
 * 不会因为重定向被重复加头。
 */
class AuthInterceptor : Interceptor {

    /**
     * 无需携带 token 的路径。与后端 WebConfig 的 excludePathPatterns 中
     * 认证相关部分保持一致；药品接口虽也在白名单里，但有 token 时带上无害，
     * 不做排除，减少一处需要同步维护的逻辑。
     */
    private val noTokenPaths = setOf(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = TokenStore.accessToken

        val request = if (token.isNullOrBlank() || original.url.encodedPath in noTokenPaths) {
            original
        } else {
            original.newBuilder()
                // header() 是替换，addHeader() 是追加——用 header 避免同一请求出现两个 Authorization
                .header("Authorization", "Bearer $token")
                .build()
        }

        val response = chain.proceed(request)

        // ⚠️ 这里拿到的已经是「续期重试之后」的最终响应，不是最初那个 401：
        // OkHttp 的 401 处理（RetryAndFollowUpInterceptor）在应用拦截器的**内层**。
        //   - TokenAuthenticator 续期成功 → 最终 200 → 这里不会清 token
        //   - TokenAuthenticator 续期失败 → 最终仍是 401 → 这里清 token，UI 跳登录
        // 也就是说：能续期就续，续不了才算真的掉线。
        //
        // 后端 JwtInterceptor 只在 token 缺失/过期/无效时返回 401（HTTP 层），
        // 登录密码错误是 400，因此这里清 token 不会误伤登录失败。
        if (response.code == 401) {
            TokenStore.clear()
        }

        return response
    }
}
