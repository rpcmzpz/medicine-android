package com.zhuhai.medicine.data.local

import android.content.Context
import android.content.SharedPreferences
import com.zhuhai.medicine.data.model.LoginResponse

/**
 * 登录态本地存储。
 *
 * ⚠️ 使用前必须初始化：在 Application.onCreate 里调用 [init]。
 *
 * 为什么选 SharedPreferences 而不是 DataStore：
 * OkHttp 的 Interceptor.intercept() 是**同步阻塞**方法，不能调用 suspend 函数。
 * SharedPreferences 首次加载后数据常驻内存，getString 是同步内存读取，可以安全地在
 * 拦截器里用；DataStore 只提供 suspend / Flow API，在拦截器里得套 runBlocking，得不偿失。
 *
 * 安全边界：本文件明文存储 token，因此已在 backup_rules / data_extraction_rules
 * 中排除该 prefs 文件，避免 token 被云备份或换机迁移带走。
 */
object TokenStore {

    private const val PREF_NAME = "auth_prefs"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_USER_TYPE = "user_type"

    /** lateinit：先声明、后赋值。未初始化就访问会抛 UninitializedPropertyAccessException。 */
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        // applicationContext 而不是 context：避免静态单例长期持有 Activity 造成内存泄漏
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /** 当前 access_token；未登录时为 null */
    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)

    val userId: Long
        get() = prefs.getLong(KEY_USER_ID, 0L)

    val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    /** 1=普通用户 2=药师 3=配送员 */
    val userType: Int
        get() = prefs.getInt(KEY_USER_TYPE, 1)

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    /** 登录成功后一次性写入整份会话 */
    fun saveSession(response: LoginResponse) {
        prefs.edit()
            .putString(KEY_ACCESS, response.accessToken)
            .putString(KEY_REFRESH, response.refreshToken)
            .putLong(KEY_USER_ID, response.userInfo?.userId ?: 0L)
            .putString(KEY_USERNAME, response.userInfo?.username)
            .putInt(KEY_USER_TYPE, response.userInfo?.userType ?: 1)
            .apply()
    }

    /** refresh_token 换新 token 后只更新这一项，其余会话信息保留 */
    fun updateAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS, token).apply()
    }

    /** 退出登录 / token 失效时清空 */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
