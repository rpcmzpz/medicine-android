package com.zhuhai.medicine.data.remote

import android.util.Log
import com.zhuhai.medicine.BuildConfig
import com.zhuhai.medicine.data.model.RefreshRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络层的总装配点：OkHttpClient -> Retrofit -> ApiService。
 *
 * 全 App 只创建一次，别在 Activity/Composable 里重复 new，否则每次都会重建
 * 连接池和线程池，既浪费内存又拿不到连接复用。
 *
 * 这里维护**两个** client，原因见 [uploadClient]。
 */
object ApiClient {

    /**
     * ⚠️ 结尾的 `/` 不能省。Retrofit 用它做路径拼接的基准，
     * 少了会直接抛 IllegalArgumentException: baseUrl must end in /。
     *
     * 10.0.2.2 是 Android 模拟器访问**宿主机**的固定回环地址；
     * 真机 / 外部设备访问不到，需用命令行参数覆盖：
     *   ./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.10:3000/
     *
     * 地址不再硬编码在源码里，而是从 BuildConfig 读——换环境不用改代码。
     */
    /** 对外暴露的站点根地址（无尾斜杠），用于拼接后端返回的相对图片路径 */
    val ORIGIN: String = BuildConfig.API_BASE_URL.trimEnd('/')

    private val BASE_URL = BuildConfig.API_BASE_URL

    private fun logLevel(body: Boolean): HttpLoggingInterceptor.Level = when {
        !BuildConfig.DEBUG -> HttpLoggingInterceptor.Level.NONE
        body -> HttpLoggingInterceptor.Level.BODY
        else -> HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * 普通 JSON 请求的客户端。
     *
     * 拦截器顺序即执行顺序（先加的是外层）：Auth 在前，日志才能看到加好的 Authorization 头。
     *
     * [TokenAuthenticator] 挂在 **authenticator** 而不是 interceptor 上：
     * 它的职责是「401 之后换 token 并重放」，OkHttp 对这件事有专门的回调位，
     * 自己用 interceptor 实现要手动重试、还容易漏掉拦截器链。
     */
    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)   // 建立 TCP 连接的上限
            .readTimeout(15, TimeUnit.SECONDS)      // 等响应数据的上限
            .writeTimeout(15, TimeUnit.SECONDS)     // 上传请求体的上限
            .addInterceptor(AuthInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = logLevel(body = true) })
            .authenticator(TokenAuthenticator())
            .build()
    }

    /**
     * 上传处方图片专用的客户端。
     *
     * **为什么不能用 BODY 日志**：HttpLoggingInterceptor 记录请求体时会把 body
     * 整个 `writeTo(buffer)` 一遍。普通 JSON body 可以重放，但我们上传用的是
     * `StreamRequestBody`（InputStream 只能读一次）——日志先读完，真正发请求时
     * 流已经关了，OkHttp 报 `IOException: Stream Closed`。
     *
     * 所以这里降到 BASIC：仍然能看到请求行、状态码、耗时，只是不打印二进制内容。
     * 顺带把写超时放宽到 60 秒，给多张大图留出上传时间。
     */
    private val uploadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = logLevel(body = false) })
            .authenticator(TokenAuthenticator())
            .build()
    }

    /**
     * 刷新 token 专用的 client——**完全独立的一条链路**。
     *
     * ⚠️ 这里绝对不能加 AuthInterceptor / TokenAuthenticator：
     * 刷新接口自己返回 401 时，Authenticator 会被再次触发去刷新，刷新请求又 401……
     * 就是无限递归。所以刷新请求只发一次，失败就返回 null，让上层把 401 透出去。
     *
     * 超时也比业务请求短：续期是「挡在用户操作前面」的一步，
     * 卡住 5 秒还没结果就不如直接让用户重新登录。
     */
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = logLevel(body = false) })
            .build()
    }

    /** Converter 负责 JSON <-> Kotlin data class 的转换。 */
    private fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    /** 接口代理对象。Retrofit 用动态代理生成实现，调用方法即发起请求。 */
    val api: ApiService by lazy {
        retrofit(apiClient).create(ApiService::class.java)
    }

    /** 同一个接口，但走「不打印 body」的 client；只有 multipart 上传该用它 */
    val uploadApi: ApiService by lazy {
        retrofit(uploadClient).create(ApiService::class.java)
    }

    /** 刷新 token 的同步接口；走独立的 refreshClient，不带任何鉴权逻辑 */
    val refreshApi: AuthRefreshApi by lazy {
        retrofit(refreshClient).create(AuthRefreshApi::class.java)
    }

    /**
     * 用 refresh_token 换一个新的 access_token；失败返回 null。
     *
     * 放在 ApiClient 而不是 TokenAuthenticator 里，是因为它依赖 Retrofit 实例，
     * 而 Retrofit 实例统一由这里装配——Authenticator 只负责「什么时候该刷、刷完怎么用」，
     * 不负责「怎么发这个请求」，职责分开更好测。
     *
     * ⚠️ `.execute()` 是同步阻塞的，只能在 OkHttp 的工作线程上调用（Authenticator 回调满足这一点）。
     */
    fun refreshAccessToken(refreshToken: String): String? {
        return try {
            val response = refreshApi.refresh(RefreshRequest(refreshToken)).execute()
            val body = response.body()
            if (body != null && body.isSuccess) {
                body.data?.accessToken?.takeIf { it.isNotBlank() }
            } else {
                // ⚠️ HTTP 非 2xx 时 Retrofit 的 body() 是 null，真正的错误信息在 errorBody() 里。
                // 只打 body?.code 会得到一片 null，等于没打。
                val errorText = runCatching { response.errorBody()?.string()?.take(120) }.getOrNull()
                Log.w(
                    TAG,
                    "刷新 token 被拒：HTTP ${response.code()} " +
                        "body=${body?.code}/${body?.message} err=$errorText"
                )
                null
            }
        } catch (e: Exception) {
            // 网络异常同样算续期失败：宁可让用户重新登录，也不要把他卡在「一直转圈」
            Log.w(TAG, "刷新 token 异常：${e.javaClass.simpleName} ${e.message}")
            null
        }
    }

    private const val TAG = "ApiClient"
}
