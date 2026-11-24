package com.zhuhai.medicine.data.local

/**
 * 令牌读写能力的抽象。
 *
 * 单独抽一层接口只有一个目的：**让续期逻辑能在 JVM 单元测试里跑**。
 * TokenStore 直接依赖 Android 的 SharedPreferences（必须 `init(context)`），
 * 纯 JVM 单元测试拿不到 Context；而 [com.zhuhai.medicine.data.remote.TokenAuthenticator]
 * 关心的只是「当前 token 是什么、能不能换新的」这几个动作，与存储实现无关。
 */
interface TokenProvider {

    /** 当前 access_token；未登录时为 null */
    val accessToken: String?

    /** 当前 refresh_token；后端不支持刷新时为 null */
    val refreshToken: String?

    /** 换到新的 access_token 后写回本地（其余会话信息保持不变） */
    fun updateAccessToken(token: String)
}
