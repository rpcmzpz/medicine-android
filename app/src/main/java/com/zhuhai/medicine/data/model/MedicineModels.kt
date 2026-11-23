package com.zhuhai.medicine.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * 药品模型。
 * 列表项字段取自 MedicineServiceImpl.list()，详情取自 detail()，均为蛇形键。
 */

data class MedicineSummary(
    @SerializedName("medicine_id") val medicineId: Long,
    val name: String,
    @SerializedName("generic_name") val genericName: String? = null,
    val brand: String? = null,
    val specification: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val price: BigDecimal = BigDecimal.ZERO,
    @SerializedName("original_price") val originalPrice: BigDecimal? = null,
    @SerializedName("sales_count") val salesCount: Int = 0,
    /** 0=OTC 1=处方药 */
    @SerializedName("drug_type") val drugType: Int = 0,
    @SerializedName("drug_type_label") val drugTypeLabel: String? = null,
    @SerializedName("stock_available") val stockAvailable: Int = 0
) {
    val isPrescription: Boolean get() = drugType == 1
    val inStock: Boolean get() = stockAvailable > 0
}

data class MedicineDetail(
    @SerializedName("medicine_id") val medicineId: Long,
    val name: String,
    @SerializedName("generic_name") val genericName: String? = null,
    val brand: String? = null,
    val specification: String? = null,
    /** 详情接口当前不返回该字段，保留是为了后端补上后无需改客户端 */
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("approval_number") val approvalNumber: String? = null,
    @SerializedName("drug_type") val drugType: Int = 0,
    @SerializedName("drug_type_label") val drugTypeLabel: String? = null,
    val description: String? = null,
    val price: BigDecimal = BigDecimal.ZERO,
    @SerializedName("original_price") val originalPrice: BigDecimal? = null,
    @SerializedName("stock_quantity") val stockQuantity: Int = 0,
    val category: CategoryRef? = null,
    val reviews: List<ReviewItem> = emptyList(),
    @SerializedName("reviews_count") val reviewsCount: Int = 0,
    @SerializedName("avg_rating") val avgRating: Double? = null
) {
    val isPrescription: Boolean get() = drugType == 1
}

data class CategoryRef(
    @SerializedName("category_id") val categoryId: Long? = null,
    val name: String? = null
)

data class ReviewItem(
    @SerializedName("user_name") val userName: String? = null,
    val rating: Int = 0,
    val content: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

/** 分类树，children 只有父节点才有 */
data class CategoryNode(
    @SerializedName("category_id") val categoryId: Long,
    @SerializedName("parent_id") val parentId: Int? = null,
    val name: String,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    val children: List<CategoryNode> = emptyList()
)
