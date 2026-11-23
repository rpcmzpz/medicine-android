package com.zhuhai.medicine

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zhuhai.medicine.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * 验证 b2 的模型层能正确解析后端真实返回。
 * 重点是「订单列表是驼峰、其余是蛇形」这个不一致点。
 */
class ApiModelMappingTest {

    private val gson = Gson()

    @Test
    fun `药品列表 蛇形键能解析`() {
        // 取自 2026-11-21 对 GET /api/v1/medicines?page=1&page_size=2 的真实返回（截取 1 条）
        val json = """
        {"code":200,"message":"success","data":{"list":[
          {"medicine_id":1,"name":"布洛芬缓释胶囊","generic_name":"布洛芬","brand":"中美史克",
           "specification":"0.3g×20粒","image_url":null,"price":18.50,"original_price":25.00,
           "sales_count":2580,"drug_type":0,"drug_type_label":"OTC","stock_available":500}
        ],"total":8,"page":1,"page_size":20},"timestamp":1795258030437}
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<PageData<MedicineSummary>>>() {}.type
        val resp: ApiResponse<PageData<MedicineSummary>> = gson.fromJson(json, type)

        assertTrue(resp.isSuccess)
        assertEquals(8L, resp.data?.total)
        assertEquals(20, resp.data?.pageSize)

        val m = resp.data!!.list.first()
        assertEquals(1L, m.medicineId)
        assertEquals("布洛芬缓释胶囊", m.name)
        assertEquals(0, BigDecimal("18.50").compareTo(m.price))
        assertEquals(500, m.stockAvailable)
        assertEquals("OTC", m.drugTypeLabel)
        assertEquals(false, m.isPrescription)
        // image_url 为 null 时必须能安全落成 null，而不是抛异常
        assertEquals(null, m.imageUrl)
    }

    @Test
    fun `订单列表 驼峰键能解析`() {
        // 后端 list() 直接返回 List<Order> 实体，Jackson 默认策略 → 驼峰
        val json = """
        {"code":200,"message":"success","data":{"list":[
          {"orderId":101,"orderNo":"MO202611220001","userId":5,"addressId":3,
           "totalAmount":47.30,"discountAmount":0,"actualAmount":47.30,
           "paymentMethod":0,"orderStatus":0,"hasPrescription":0,"remark":null,
           "version":0,"createdAt":"2026-11-22T10:30:00","paidAt":null,"completedAt":null,
           "username":"admin"}
        ],"total":1,"page":1,"page_size":20},"timestamp":1795258030437}
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<PageData<OrderSummary>>>() {}.type
        val resp: ApiResponse<PageData<OrderSummary>> = gson.fromJson(json, type)

        val o = resp.data!!.list.first()
        assertEquals(101L, o.orderId)
        assertEquals("MO202611220001", o.orderNo)
        assertEquals(0, BigDecimal("47.30").compareTo(o.actualAmount))
        assertEquals("待支付", o.statusLabel)
        assertTrue(o.canCancel)
        assertEquals("2026-11-22T10:30:00", o.createdAt)
    }

    @Test
    fun `订单详情 蛇形键能解析 包括嵌套项`() {
        val json = """
        {"code":200,"message":"success","data":{
          "order_id":101,"order_no":"MO202611220001","order_status":1,"order_status_label":"待审核",
          "total_amount":47.30,"discount_amount":0,"actual_amount":47.30,"has_prescription":1,
          "remark":"尽快发货","created_at":"2026-11-22T10:30:00","paid_at":"2026-11-22T10:31:00",
          "completed_at":null,
          "items":[{"medicine_name":"布洛芬缓释胶囊","price":18.50,"quantity":2,"subtotal":37.00}],
          "address":{"receiver_name":"张三","phone":"138****8000","detail":"辽宁省沈阳市沈北新区道义南大街37号"},
          "payment":{"payment_method_label":"微信","payment_status_label":"成功"},
          "delivery":null
        },"timestamp":1795258030437}
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<OrderDetail>>() {}.type
        val resp: ApiResponse<OrderDetail> = gson.fromJson(json, type)

        val d = resp.data!!
        assertEquals(101L, d.orderId)
        assertEquals("待审核", d.orderStatusLabel)
        assertEquals(1, d.items.size)
        assertEquals(2, d.items.first().quantity)
        assertEquals("张三", d.address?.receiverName)
        assertEquals("微信", d.payment?.paymentMethodLabel)
        assertEquals(null, d.delivery)
    }

    @Test
    fun `地址列表 驼峰键能解析`() {
        // 2026-11-23 实测：GET /user/addresses 直接返回 Address 实体，字段是驼峰
        val json = """
        {"code":200,"message":"success","data":{"list":[
          {"addressId":1,"userId":14,"receiverName":"ZhuHai","phone":"13800000001",
           "province":"Liaoning","city":"Shenyang","district":"Shenbei",
           "detail":"No37DaoyiSouthSt","isDefault":1,"createdAt":"2026-11-23T17:21:59"}
        ]},"timestamp":1795425739373}
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<ListData<Address>>>() {}.type
        val resp: ApiResponse<ListData<Address>> = gson.fromJson(json, type)

        val a = resp.data!!.list.first()
        // 若模型写成 @SerializedName("address_id")，这里会得到 0 → 结算页永远选不中地址
        assertEquals(1L, a.addressId)
        assertEquals("ZhuHai", a.receiverName)
        assertEquals(1, a.isDefault)
        assertEquals("LiaoningShenyangShenbeiNo37DaoyiSouthSt", a.fullAddress)
    }

    @Test
    fun `地址 蛇形键也能解析`() {
        // POST /user/addresses 的响应是 Service 手工拼 Map → 蛇形；alternate 必须同时兼容
        val json = """
        {"code":200,"message":"添加成功","data":{"address_id":2,"receiver_name":"张三",
         "phone":"13800000002","province":"辽宁省","city":"沈阳市","district":"沈北新区",
         "detail":"道义南大街37号","is_default":0},"timestamp":1795425739373}
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<Address>>() {}.type
        val resp: ApiResponse<Address> = gson.fromJson(json, type)

        val a = resp.data!!
        assertEquals(2L, a.addressId)
        assertEquals("张三", a.receiverName)
        assertEquals(0, a.isDefault)
    }

    @Test
    fun `支付结果 蛇形键能解析`() {
        // 2026-11-23 实测 POST /orders/{id}/pay 的返回（订单 MEDMWT2ET4W017A，支付宝支付）
        val json = """
        {"code":200,"message":"支付成功","data":{"payment_id":6,
         "transaction_no":"TXNMWT2P31QB1CC74","amount":8.80,"order_status":2},
         "timestamp":1795426680000}
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<PayResult>>() {}.type
        val resp: ApiResponse<PayResult> = gson.fromJson(json, type)

        val p = resp.data!!
        assertEquals(6L, p.paymentId)
        assertEquals("TXNMWT2P31QB1CC74", p.transactionNo)
        assertEquals(0, BigDecimal("8.80").compareTo(p.amount))
        // 无处方药的订单支付后进入「待配药」，不是「已完成」
        assertEquals(2, p.orderStatus)
        assertEquals("待配药", ORDER_STATUS_LABELS[p.orderStatus])
    }

    @Test
    fun `待审核订单也允许支付 但只有待支付能取消`() {
        val auditing = OrderSummary(orderId = 1, orderStatus = 1)
        assertTrue(auditing.canPay)
        // 后端 cancel 只放行 status==0，客户端提前对齐
        assertEquals(false, auditing.canCancel)

        val unpaid = OrderSummary(orderId = 2, orderStatus = 0)
        assertTrue(unpaid.canPay)
        assertTrue(unpaid.canCancel)

        val finished = OrderSummary(orderId = 3, orderStatus = 5)
        assertEquals(false, finished.canPay)
        assertEquals(false, finished.canCancel)
    }

    @Test
    fun `支付方式枚举的 code 与后端一一对应`() {
        // 后端 new String[]{"微信","支付宝","银联"}[paymentMethod]，顺序不能动
        assertEquals(listOf(0, 1, 2), PaymentMethod.entries.map { it.code })
        assertEquals("微信支付", PaymentMethod.WECHAT.label)
    }
}
