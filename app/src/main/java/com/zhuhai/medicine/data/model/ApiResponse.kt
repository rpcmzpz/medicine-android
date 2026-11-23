package com.zhuhai.medicine.data.model

import com.google.gson.annotations.SerializedName

/**
 * 后端统一返回体：Result{code, message, data, timestamp}
 *
 * 注意：后端业务失败时 HTTP 状态码仍然是 200，错误信息在 body 的 code 里。
 * 因此判断成败看 [isSuccess]，不能看 HTTP 状态码。
 */
data class ApiResponse<T>(
    val code: Int,
    val message: String? = null,
    val data: T? = null,
    val timestamp: Long = 0L
) {
    val isSuccess: Boolean get() = code == 200
}

/** 分页结构：{list, total, page, page_size} */
data class PageData<T>(
    val list: List<T> = emptyList(),
    val total: Long = 0L,
    val page: Int = 1,
    @SerializedName("page_size") val pageSize: Int = 20
)

/** 只有 list 的返回：{list: [...]} */
data class ListData<T>(
    val list: List<T> = emptyList()
)
