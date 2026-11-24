package com.zhuhai.medicine.data.remote

import android.util.Log
import com.zhuhai.medicine.data.local.TokenProvider
import com.zhuhai.medicine.data.local.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * access_token 过期时自动用 refresh_token 换新，并重放原请求。
 *
 * ## 为什么用 Authenticator 而不是 Interceptor
 *
 * Interceptor 的职责是「加工请求」，拿到 401 之后想重放，就得自己再调一次
 * `chain.proceed(request)` —— 那样会跳过后续拦截器，重试次数也得自己控制，很容易写错。
 * OkHttp 专门留了 [Authenticator] 这个口子：收到 401 时自动回调，
 * 回调返回新的 [Request] 就用它再试一次；返回 null 就放弃，把 401 交给上层。
 *
 * ## 401 的传导路径（决定了谁负责清 token）
 *
 * OkHttp 内部链的顺序是：
 * `应用拦截器(Auth/Log) → RetryAndFollowUpInterceptor → ... → CallServerInterceptor`
 *
 * 401 的续期逻辑在 RetryAndFollowUpInterceptor 这一层，**位于应用拦截器的内层**。
 * 所以应用拦截器拿到的已经是「重试之后的最终响应」：
 * - 续期成功 → 最终是 200 → [AuthInterceptor] 不会清 token
 * - 续期失败 → 最终仍是 401 → [AuthInterceptor] 清 token，UI 收到 Unauthorized 跳登录
 *
 * 这正好是想要的行为：**能续期就续，续不了才算真的掉线**。
 *
 * ## 并发
 *
 * 列表页刷新 + 购物车加载可能同时 401，不能各自去换 token（后端的 refresh_token
 * 虽然不轮换，但白白多发请求、也多一次出错机会）。这里用「加锁 + 双重检查」：
 * 排队期间如果发现本地 token 已经和失败请求里带的不一致，说明别的线程已经换过了，
 * 直接拿新 token 重试，**不再发第二次刷新请求**。
 */
class TokenAuthenticator(
    private val tokens: TokenProvider = TokenStore,
    /** 刷新动作：入参 refresh_token，返回新的 access_token；失败返回 null */
    private val refresher: (String) -> String? = { ApiClient.refreshAccessToken(it) }
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 已经重试过一轮还是 401 → 新 token 也不被接受，放弃，别和后端无限互相甩锅
        if (responseCount(response) >= 2) return null

        // 失败这次请求时用的 token
        val failedToken = response.request.header(HEADER_AUTHORIZATION)
            ?.removePrefix(BEARER_PREFIX)
            .orEmpty()

        // 情况一：别的线程已经续期过了，本地 token 与失败请求里的不一致 → 直接复用
        val current = tokens.accessToken
        if (!current.isNullOrBlank() && current != failedToken) {
            return response.request.withToken(current)
        }

        // 没有 refresh_token（未登录，或后端不签发）→ 无从续期
        val refreshToken = tokens.refreshToken
        if (refreshToken.isNullOrBlank()) return null

        // 情况二：真正去换新。加锁，保证同一时刻只有一个线程在刷
        val newToken = synchronized(refreshLock) {
            // 进入锁后**再查一次**：可能刚才排队时别的线程已经刷好了
            val latest = tokens.accessToken
            if (!latest.isNullOrBlank() && latest != failedToken) {
                latest
            } else {
                refresher(refreshToken)?.also {
                    tokens.updateAccessToken(it)
                    Log.i(TAG, "access_token 已续期，新 token 尾号 ${it.takeLast(8)}")
                }
            }
        } ?: return null

        return response.request.withToken(newToken)
    }

    /**
     * 换掉 Authorization 头。
     *
     * 用 header()（替换）而不是 addHeader()（追加）：重放时若变成两个 Authorization，
     * 后端只认第一个，容易出现「明明换了 token 却还是 401」的灵异现象。
     */
    private fun Request.withToken(token: String): Request = newBuilder()
        .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
        .build()

    /**
     * 这条请求已经被尝试过几次。
     *
     * priorResponse 是「上一次尝试的响应」：第一次 401 时它是 null，
     * 用新 token 重试后又 401，它就指向上一次那个 401，链式串起来能数出次数。
     */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val TAG = "TokenAuth"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "

        /** 同一时刻只允许一个线程在换 token */
        private val refreshLock = Any()
    }
}
