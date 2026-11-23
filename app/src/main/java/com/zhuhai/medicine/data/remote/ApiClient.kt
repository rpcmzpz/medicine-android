package com.zhuhai.medicine.data.remote

import com.zhuhai.medicine.BuildConfig
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
     * 真机 / 外部设备访问不到，需换成电脑的局域网 IP。
     */
    /** 对外暴露的站点根地址（无尾斜杠），用于拼接后端返回的相对图片路径 */
    const val ORIGIN = "http://10.0.2.2:3000"

    private const val BASE_URL = "$ORIGIN/"

    private fun logLevel(body: Boolean): HttpLoggingInterceptor.Level = when {
        !BuildConfig.DEBUG -> HttpLoggingInterceptor.Level.NONE
        body -> HttpLoggingInterceptor.Level.BODY
        else -> HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * 普通 JSON 请求的客户端。
     *
     * 拦截器顺序即执行顺序（先加的是外层）：Auth 在前，日志才能看到加好的 Authorization 头。
     */
    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)   // 建立 TCP 连接的上限
            .readTimeout(15, TimeUnit.SECONDS)      // 等响应数据的上限
            .writeTimeout(15, TimeUnit.SECONDS)     // 上传请求体的上限
            .addInterceptor(AuthInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = logLevel(body = true) })
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
}
