package com.zhuhai.medicine.data.model

import com.google.gson.annotations.SerializedName

/**
 * 用户与地址模型。字段取自 UserServiceImpl，全部蛇形键。
 * 注意：profile 里的 phone / email 后端已做掩码（如 138****8000、a***@qq.com），不能回填到编辑框。
 */

data class UserProfile(
    @SerializedName("user_id") val userId: Long,
    val username: String,
    @SerializedName("user_type") val userType: Int = 1,
    @SerializedName("real_name") val realName: String? = null,
    val age: Int? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerializedName("membership_level") val membershipLevel: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class UpdateProfileRequest(
    @SerializedName("real_name") val realName: String? = null,
    val age: Int? = null,
    val phone: String? = null,
    val email: String? = null
)

/**
 * 收货地址。
 *
 * ⚠️ 命名坑：`GET /user/addresses` 直接返回 Address 实体列表，走 Jackson 默认策略 → **驼峰**
 * （addressId / receiverName / isDefault）；而 `POST /user/addresses` 的响应是 Service 手工拼的
 * Map → **蛇形**（address_id）。所以读地址用 alternate 兼容两种写法。
 *
 * 这与 OrderSummary 是同一个套路，别看到「后端全蛇形」就照抄。
 */
data class Address(
    @SerializedName(value = "addressId", alternate = ["address_id"]) val addressId: Long,
    @SerializedName(value = "receiverName", alternate = ["receiver_name"]) val receiverName: String? = null,
    val phone: String? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val detail: String? = null,
    /** 1=默认地址（DB tinyint，读出来是 Int） */
    @SerializedName(value = "isDefault", alternate = ["is_default"]) val isDefault: Int = 0
) {
    val fullAddress: String
        get() = listOfNotNull(province, city, district, detail).joinToString("")
}

data class AddressRequest(
    @SerializedName("receiver_name") val receiverName: String,
    val phone: String,
    val province: String,
    val city: String,
    val district: String,
    val detail: String,
    /**
     * ⚠️ 这里必须是 Boolean，不能是 Int。
     * 后端 `UserServiceImpl.addAddress` 写的是 `(Boolean) params.get("is_default")`，
     * 传 1 会抛 ClassCastException → 500「Integer cannot be cast to Boolean」。
     * 但读取地址时后端返回的仍是 0/1（DB tinyint），所以 [Address.isDefault] 保持 Int。
     */
    @SerializedName("is_default") val isDefault: Boolean = false
)

/** 地址增/删/设默认统一返回 {address_id} */
data class AddressIdResult(
    @SerializedName("address_id") val addressId: Long
)

/** 处方上传返回 */
data class PrescriptionUploadResult(
    @SerializedName("prescription_id") val prescriptionId: Long,
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerializedName("review_status") val reviewStatus: Int = 0
)

data class PrescriptionStatus(
    @SerializedName("prescription_id") val prescriptionId: Long,
    @SerializedName("order_id") val orderId: Long? = null,
    @SerializedName("review_status") val reviewStatus: Int = 0,
    @SerializedName("review_remark") val reviewRemark: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList()
)
