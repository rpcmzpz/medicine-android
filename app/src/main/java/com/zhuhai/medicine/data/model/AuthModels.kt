package com.zhuhai.medicine.data.model

import com.google.gson.annotations.SerializedName

/**
 * 认证相关模型。
 * 后端 AuthServiceImpl 手工拼 Map，键名全部为蛇形（access_token / user_info）。
 */

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0L,
    @SerializedName("user_info") val userInfo: UserInfo? = null
)

data class UserInfo(
    @SerializedName("user_id") val userId: Long,
    val username: String,
    @SerializedName("user_type") val userType: Int,
    @SerializedName("real_name") val realName: String? = null
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val phone: String? = null,
    val email: String? = null,
    @SerializedName("real_name") val realName: String? = null,
    val age: Int? = null,
    /** 1=普通用户 2=药师 3=配送员，客户端固定传 1 */
    @SerializedName("user_type") val userType: Int = 1
)

data class RegisterResponse(
    @SerializedName("user_id") val userId: Long,
    val username: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class RefreshResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Long = 0L
)
