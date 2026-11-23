package com.zhuhai.medicine.data.remote

import com.zhuhai.medicine.data.model.ApiResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 验证 ApiCall 的两条判断路径覆盖完整：
 * HTTP 非 2xx（Retrofit 抛异常）与 HTTP 200 + 业务 code 失败（不抛异常）。
 */
class ApiCallTest {

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            body.toResponseBody("application/json".toMediaType())
        )
    )

    @Test
    fun `成功响应返回 Success 并携带数据`() = runBlocking {
        val result = apiCall { ApiResponse(code = 200, data = "ok") }
        assertEquals(ApiResult.Success("ok"), result)
    }

    @Test
    fun `HTTP 200 但业务 code 非 200 时返回 BusinessError`() = runBlocking {
        val result = apiCall<String> {
            ApiResponse(code = 40003, message = "库存不足")
        }
        assertEquals(ApiResult.BusinessError(40003, "库存不足"), result)
    }

    @Test
    fun `HTTP 401 转成 Unauthorized`() = runBlocking {
        val result = apiCall<String> {
            throw httpException(401, """{"code":40101,"message":"Token已过期，请重新登录"}""")
        }
        assertEquals(ApiResult.Unauthorized, result)
    }

    @Test
    fun `HTTP 400 解析错误体里的业务码与中文文案`() = runBlocking {
        val result = apiCall<String> {
            throw httpException(400, """{"code":40102,"message":"用户名或密码错误"}""")
        }
        assertEquals(ApiResult.BusinessError(40102, "用户名或密码错误"), result)
    }

    @Test
    fun `IOException 转成 NetworkError`() = runBlocking {
        val result = apiCall<String> { throw IOException("connect timed out") }
        assertTrue(result is ApiResult.NetworkError)
    }

    @Test
    fun `apiCallUnit 在 data 为 null 时仍然算成功`() = runBlocking {
        val result = apiCallUnit { ApiResponse<Unit>(code = 200, message = "已添加到购物车") }
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `apiCall 遇到成功但 data 为空时判为业务失败`() = runBlocking {
        val result = apiCall<String> { ApiResponse(code = 200, message = "success") }
        assertTrue(result is ApiResult.BusinessError)
    }
}
