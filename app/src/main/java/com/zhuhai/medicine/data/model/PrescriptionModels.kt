package com.zhuhai.medicine.data.model

import com.google.gson.annotations.SerializedName

/**
 * 处方信息（订单详情里的关联数据）。
 *
 * 字段来自后端 `OrderServiceImpl.detail` 手工拼的 Map，全部蛇形；
 * `image_urls` 后端已经把 DB 里的 JSON 字符串解析成数组了，这里直接按 List 收。
 */
data class PrescriptionBrief(
    @SerializedName("prescription_id") val prescriptionId: Long = 0,
    /** 0 待审核 / 1 已通过 / 2 已驳回 */
    @SerializedName("review_status") val reviewStatus: Int = 0,
    @SerializedName("review_remark") val reviewRemark: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList()
) {
    val statusLabel: String get() = PRESCRIPTION_STATUS_LABELS.getOrElse(reviewStatus) { "未知" }

    val isPending: Boolean get() = reviewStatus == 0
    val isRejected: Boolean get() = reviewStatus == 2
}

val PRESCRIPTION_STATUS_LABELS = arrayOf("待审核", "已通过", "已驳回")

/**
 * 处方审核状态 → 颜色。
 * 放在 model 层而不是 UI 层，是为了让「状态语义」只有一处定义。
 */
object PrescriptionStatusPalette {
    const val PENDING = 0xFFB26A00   // 琥珀：等待中
    const val APPROVED = 0xFF2E7D32  // 绿：通过
    const val REJECTED = 0xFFC62828  // 红：驳回
}
