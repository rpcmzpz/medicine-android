package com.zhuhai.medicine.data.repository

import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.model.LoginRequest
import com.zhuhai.medicine.data.model.LoginResponse
import com.zhuhai.medicine.data.model.RefreshRequest
import com.zhuhai.medicine.data.model.RefreshResponse
import com.zhuhai.medicine.data.model.RegisterRequest
import com.zhuhai.medicine.data.model.RegisterResponse
import com.zhuhai.medicine.data.remote.ApiClient
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.remote.ApiService
import com.zhuhai.medicine.data.remote.apiCall

/**
 * 认证数据源。
 *
 * `api` 用默认参数注入：正常运行时取 [ApiClient.api]，
 * 写测试时可以传入 Fake 实现，不必启动 HTTP 服务。
 */
class AuthRepository(
    private val api: ApiService = ApiClient.api
) {

    /** 登录成功会自动持久化会话，避免上层忘记保存 token */
    suspend fun login(username: String, password: String): ApiResult<LoginResponse> {
        val result = apiCall { api.login(LoginRequest(username, password)) }
        if (result is ApiResult.Success) {
            TokenStore.saveSession(result.data)
        }
        return result
    }

    suspend fun register(
        username: String,
        password: String,
        phone: String? = null,
        email: String? = null,
        realName: String? = null,
        age: Int? = null
    ): ApiResult<RegisterResponse> = apiCall {
        api.register(
            RegisterRequest(
                username = username,
                password = password,
                phone = phone,
                email = email,
                realName = realName,
                age = age
            )
        )
    }

    /** 用本地 refresh_token 换新 access_token；本地没有 refresh_token 直接判未授权 */
    suspend fun refreshToken(): ApiResult<RefreshResponse> {
        val refresh = TokenStore.refreshToken
            ?: return ApiResult.Unauthorized
        return apiCall { api.refresh(RefreshRequest(refresh)) }
    }
}
