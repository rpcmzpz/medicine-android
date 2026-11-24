package com.zhuhai.medicine.data.remote

import com.zhuhai.medicine.data.local.TokenProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [TokenAuthenticator] 的纯 JVM 单元测试。
 *
 * 不连服务器、不碰 Android：用假的 [TokenProvider] 和假的刷新函数，
 * 把「什么时候刷、刷几次、拿哪个 token 重放」这几条分支钉死。
 *
 * 这些分支在真机上很难触发（要等 token 真的过期），但恰恰是最容易写错的地方，
 * 所以用单测覆盖比靠手点可靠。
 */
class TokenAuthenticatorTest {

    /** 假的令牌仓库：纯内存，不依赖 SharedPreferences */
    private class FakeTokens(
        override var accessToken: String? = null,
        override var refreshToken: String? = null
    ) : TokenProvider {
        var updatedToken: String? = null
        override fun updateAccessToken(token: String) {
            updatedToken = token
            accessToken = token
        }
    }

    private fun request(token: String?): Request = Request.Builder()
        .url("http://10.0.2.2:3000/api/v1/cart/items")
        .apply { if (token != null) header("Authorization", "Bearer $token") }
        .build()

    private fun unauthorized(request: Request, prior: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .apply { if (prior != null) priorResponse(prior) }
            .build()

    /**
     * 「上一次尝试」的响应。
     *
     * ⚠️ OkHttp 要求 priorResponse 的 body 为 null——上一轮的 body 早就被读掉了，
     * 再挂上去会被 builder 拒绝（IllegalArgumentException: priorResponse.body != null）。
     * 这是实测出来的，不是猜的。
     */
    private fun priorAttempt(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

    @Test
    fun `续期成功后用新 token 重放原请求`() {
        val tokens = FakeTokens(accessToken = "old", refreshToken = "refresh-1")
        var refreshCalls = 0
        val authenticator = TokenAuthenticator(tokens) {
            refreshCalls++
            "new"
        }

        val retry = authenticator.authenticate(null, unauthorized(request("old")))

        assertNotNull("续期成功应当返回重放请求", retry)
        assertEquals("Bearer new", retry!!.header("Authorization"))
        assertEquals("new", tokens.updatedToken)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `别的线程已经续期时直接复用新 token 不重复刷新`() {
        val tokens = FakeTokens(accessToken = "new", refreshToken = "refresh-1")
        var refreshCalls = 0
        val authenticator = TokenAuthenticator(tokens) {
            refreshCalls++
            "should-not-be-used"
        }

        val retry = authenticator.authenticate(null, unauthorized(request("old")))

        assertEquals("Bearer new", retry!!.header("Authorization"))
        assertEquals("并发场景不应再发一次刷新请求", 0, refreshCalls)
    }

    @Test
    fun `刷新失败则不重试把 401 透出去`() {
        val tokens = FakeTokens(accessToken = "old", refreshToken = "refresh-1")
        val authenticator = TokenAuthenticator(tokens) { null }

        assertNull(authenticator.authenticate(null, unauthorized(request("old"))))
    }

    @Test
    fun `没有 refresh_token 则不重试`() {
        val tokens = FakeTokens(accessToken = "old", refreshToken = null)
        var refreshCalls = 0
        val authenticator = TokenAuthenticator(tokens) {
            refreshCalls++
            "new"
        }

        assertNull(authenticator.authenticate(null, unauthorized(request("old"))))
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `已经重试过一轮仍是 401 则放弃`() {
        val tokens = FakeTokens(accessToken = "old", refreshToken = "refresh-1")
        var refreshCalls = 0
        val authenticator = TokenAuthenticator(tokens) {
            refreshCalls++
            "new"
        }

        val first = priorAttempt(request("old"))
        val second = unauthorized(request("old"), prior = first)

        assertNull(authenticator.authenticate(null, second))
        assertEquals("已经重试过一轮就不该再刷", 0, refreshCalls)
    }

    @Test
    fun `重放请求不会带两个 Authorization 头`() {
        val tokens = FakeTokens(accessToken = "old", refreshToken = "refresh-1")
        val authenticator = TokenAuthenticator(tokens) { "new" }

        val retry = authenticator.authenticate(null, unauthorized(request("old")))!!

        assertEquals(1, retry.headers.values("Authorization").size)
    }
}
