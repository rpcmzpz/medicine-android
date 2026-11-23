package com.zhuhai.medicine.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * 购物车模型。字段取自 CartServiceImpl.list()，全部蛇形键。
 */

data class CartItem(
    @SerializedName("cart_id") val cartId: Long,
    @SerializedName("medicine_id") val medicineId: Long,
    @SerializedName("medicine_name") val medicineName: String,
    @SerializedName("image_url") val imageUrl: String? = null,
    val price: BigDecimal = BigDecimal.ZERO,
    val quantity: Int = 0,
    val subtotal: BigDecimal = BigDecimal.ZERO,
    @SerializedName("drug_type") val drugType: Int = 0,
    @SerializedName("stock_available") val stockAvailable: Int = 0,
    /** 后端已算好的可购上限，UI 的「+」按钮用它做封顶判断 */
    @SerializedName("max_quantity") val maxQuantity: Int = 0
) {
    val isPrescription: Boolean get() = drugType == 1
}

data class CartData(
    val list: List<CartItem> = emptyList(),
    @SerializedName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("total_count") val totalCount: Int = 0
)

data class AddCartItemRequest(
    @SerializedName("medicine_id") val medicineId: Long,
    val quantity: Int = 1
)

data class UpdateCartQuantityRequest(
    val quantity: Int
)
