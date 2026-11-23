package com.zhuhai.medicine.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * 订单模型。
 *
 * ⚠️ 这里是全项目唯一一处「命名不统一」的地方，必须记住：
 * - 订单**列表**：后端直接返回 List<Order> 实体，走 Jackson 默认策略 → 驼峰（orderId / orderNo）
 * - 订单**详情**与**下单结果**：Service 里手工拼 Map → 蛇形（order_id / order_no）
 *
 * 因此 OrderSummary 用 @SerializedName 的 alternate 同时兼容两种写法；
 * 这样即使后端以后统一了命名策略，客户端也不会挂。
 */

data class OrderSummary(
    @SerializedName(value = "orderId", alternate = ["order_id"]) val orderId: Long,
    @SerializedName(value = "orderNo", alternate = ["order_no"]) val orderNo: String? = null,
    @SerializedName(value = "totalAmount", alternate = ["total_amount"]) val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "discountAmount", alternate = ["discount_amount"]) val discountAmount: BigDecimal? = null,
    @SerializedName(value = "actualAmount", alternate = ["actual_amount"]) val actualAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "orderStatus", alternate = ["order_status"]) val orderStatus: Int = 0,
    @SerializedName(value = "hasPrescription", alternate = ["has_prescription"]) val hasPrescription: Int = 0,
    val remark: String? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: String? = null,
    @SerializedName(value = "paidAt", alternate = ["paid_at"]) val paidAt: String? = null,
    @SerializedName(value = "completedAt", alternate = ["completed_at"]) val completedAt: String? = null
) {
    /** 0待支付 1待审核 2待配药 3待配送 4配送中 5已完成 6已取消 7已退款 */
    val statusLabel: String get() = ORDER_STATUS_LABELS.getOrElse(orderStatus) { "未知" }

    /** 后端 cancel 接口只放行 status==0，这里提前对齐，别让用户点了才知道不行 */
    val canCancel: Boolean get() = orderStatus == 0

    /** 只有「待支付」能付款；处方单付款后才进「待审核」，别用 1 去放行 */
    val canPay: Boolean get() = orderStatus == 0
}

val ORDER_STATUS_LABELS = arrayOf(
    "待支付", "待审核", "待配药", "待配送", "配送中", "已完成", "已取消", "已退款"
)

data class CreateOrderRequest(
    @SerializedName("cart_item_ids") val cartItemIds: List<Long>,
    @SerializedName("address_id") val addressId: Long,
    val remark: String? = null,
    /** 处方药下单时需要带上已上传处方的 id */
    @SerializedName("prescription_ids") val prescriptionIds: List<Long>? = null
)

data class CreateOrderResult(
    @SerializedName("order_id") val orderId: Long,
    @SerializedName("order_no") val orderNo: String? = null,
    @SerializedName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("actual_amount") val actualAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("order_status") val orderStatus: Int = 0,
    @SerializedName("has_prescription") val hasPrescription: Int = 0,
    /** 订单内商品条目数，不是金额 */
    val items: Int = 0
)

data class OrderDetail(
    @SerializedName("order_id") val orderId: Long,
    @SerializedName("order_no") val orderNo: String? = null,
    @SerializedName("order_status") val orderStatus: Int = 0,
    @SerializedName("order_status_label") val orderStatusLabel: String? = null,
    @SerializedName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("discount_amount") val discountAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("actual_amount") val actualAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("has_prescription") val hasPrescription: Int = 0,
    val remark: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("paid_at") val paidAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    val items: List<OrderLine> = emptyList(),
    val address: OrderAddress? = null,
    val payment: PaymentInfo? = null,
    val delivery: DeliveryInfo? = null,
    /** 处方单才有；后端按 order_id 关联查出，含药师审核状态与驳回原因 */
    val prescriptions: List<PrescriptionBrief> = emptyList()
)

data class OrderLine(
    @SerializedName("medicine_name") val medicineName: String? = null,
    val price: BigDecimal = BigDecimal.ZERO,
    val quantity: Int = 0,
    val subtotal: BigDecimal = BigDecimal.ZERO
)

data class OrderAddress(
    @SerializedName("receiver_name") val receiverName: String? = null,
    val phone: String? = null,
    /** 后端已拼成「省市区+详细地址」一整串 */
    val detail: String? = null
)

data class PaymentInfo(
    @SerializedName("payment_method_label") val paymentMethodLabel: String? = null,
    @SerializedName("payment_status_label") val paymentStatusLabel: String? = null
)

data class DeliveryInfo(
    @SerializedName("delivery_status_label") val deliveryStatusLabel: String? = null
)

data class PayOrderRequest(
    /** 0=微信 1=支付宝 2=银联 */
    @SerializedName("payment_method") val paymentMethod: Int
)

/**
 * 支付结果。后端 pay 接口返回的是 Map，字段名全部蛇形。
 * `transaction_no` 是这笔支付的流水号，详情页的支付信息里不返回它，只在支付成功那一刻能拿到。
 */
data class PayResult(
    @SerializedName("payment_id") val paymentId: Long = 0,
    @SerializedName("transaction_no") val transactionNo: String? = null,
    val amount: BigDecimal = BigDecimal.ZERO,
    @SerializedName("order_status") val orderStatus: Int = 0
)

/**
 * 支付方式。
 *
 * 用 enum 而不是散落的 Int 常量，好处是 UI 里 `PaymentMethod.entries` 直接就能列出三个选项，
 * `code` 与后端 payment_method 一一对应，改顺序也不会错位。
 */
enum class PaymentMethod(val code: Int, val label: String) {
    WECHAT(0, "微信支付"),
    ALIPAY(1, "支付宝"),
    UNION_PAY(2, "银联")
}
