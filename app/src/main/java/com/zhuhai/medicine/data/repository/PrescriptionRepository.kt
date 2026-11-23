package com.zhuhai.medicine.data.repository

import com.zhuhai.medicine.data.model.PrescriptionStatus
import com.zhuhai.medicine.data.model.PrescriptionUploadResult
import com.zhuhai.medicine.data.remote.ApiClient
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.remote.ApiService
import com.zhuhai.medicine.data.remote.apiCall
import okhttp3.MultipartBody

/**
 * 处方接口。
 *
 * 仓库只负责「发请求 + 收敛错误」，**不碰 Android 的 Uri / ContentResolver**：
 * 把 Uri 读成字节流是 UI 层（有 Context）的活，见 `ui/common/ImageParts.kt`。
 * 这样这个类可以在纯 JVM 单测里直接 new 出来跑。
 */
class PrescriptionRepository @JvmOverloads constructor(
    // 必须用 uploadApi：它对应的 OkHttpClient 不打印请求体，
    // 否则日志拦截器会先把流式 body 读完，真正发送时报 Stream Closed
    private val api: ApiService = ApiClient.uploadApi
) {

    /**
     * 上传处方图片。
     * 后端签名是 `@RequestParam("files") MultipartFile[]`，
     * 所以每个 part 的 name 必须固定写 `files`，少一张都不行。
     */
    suspend fun upload(parts: List<MultipartBody.Part>): ApiResult<PrescriptionUploadResult> =
        apiCall { api.uploadPrescriptions(parts) }

    suspend fun status(prescriptionId: Long): ApiResult<PrescriptionStatus> =
        apiCall { api.prescriptionStatus(prescriptionId) }
}
