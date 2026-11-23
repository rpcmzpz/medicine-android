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

    /**
     * 请求日志拦截器。
     * debug 打全量（含请求体、响应体、Header），release 直接关闭——
     * 登录请求体里带明文密码，线上绝不能进日志。
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * OkHttp 客户端。`by lazy` 表示第一次被访问时才初始化，
     * 之后直接复用同一个实例（默认 SYNCHRONIZED 模式，多线程安全）。
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)   // 建立 TCP 连接的上限
            .readTimeout(15, TimeUnit.SECONDS)      // 等响应数据的上限
            .writeTimeout(15, TimeUnit.SECONDS)     // 上传请求体的上限
            // 顺序即执行顺序（先加的是外层）：Auth 在前，日志才能看到加好的 Authorization 头
            .addInterceptor(AuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /** Retrofit 实例。Converter 负责 JSON <-> Kotlin data class 的转换。 */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** 接口代理对象。Retrofit 用动态代理生成实现，调用方法即发起请求。 */
    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
