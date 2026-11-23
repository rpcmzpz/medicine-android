package com.zhuhai.medicine.data.repository

import com.zhuhai.medicine.data.model.CreateOrderRequest
import com.zhuhai.medicine.data.model.CreateOrderResult
import com.zhuhai.medicine.data.model.OrderDetail
import com.zhuhai.medicine.data.model.OrderSummary
import com.zhuhai.medicine.data.model.PageData
import com.zhuhai.medicine.data.model.PayOrderRequest
import com.zhuhai.medicine.data.model.PayResult
import com.zhuhai.medicine.data.remote.ApiClient
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.remote.ApiService
import com.zhuhai.medicine.data.remote.apiCall
import com.zhuhai.medicine.data.remote.apiCallUnit

/**
 * 订单数据源。全部接口都需要登录（不在 JWT 白名单里）。
 *
 * 注意三个写接口的返回体不一样，别照抄：
 * - create    → data 是对象   → apiCall
 * - pay       → data 是对象   → apiCall
 * - cancel    → data 是 null    → apiCallUnit
 * - confirm   → data 是 null    → apiCallUnit
 */
class OrderRepository(
    private val api: ApiService = ApiClient.api
) {

    /**
     * 下单。成功后后端会**直接删除**已下单的购物车条目，
     * 所以调用方拿到 Success 后不要再用本地缓存的购物车列表。
     */
    suspend fun create(request: CreateOrderRequest): ApiResult<CreateOrderResult> =
        apiCall { api.createOrder(request) }

    /**
     * 订单列表（分页）。
     * @param status null=全部，其余见 [com.zhuhai.medicine.data.model.ORDER_STATUS_LABELS] 的下标
     */
    suspend fun list(
        status: Int? = null,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): ApiResult<PageData<OrderSummary>> =
        apiCall { api.listOrders(status = status, page = page, pageSize = pageSize) }

    suspend fun detail(orderId: Long): ApiResult<OrderDetail> =
        apiCall { api.orderDetail(orderId) }

    suspend fun pay(orderId: Long, paymentMethod: Int): ApiResult<PayResult> =
        apiCall { api.payOrder(orderId, PayOrderRequest(paymentMethod)) }

    suspend fun cancel(orderId: Long): ApiResult<Unit> =
        apiCallUnit { api.cancelOrder(orderId) }

    /** 确认收货：只在「待配送 / 配送中」可用，由后端二次校验 */
    suspend fun confirm(orderId: Long): ApiResult<Unit> =
        apiCallUnit { api.confirmOrder(orderId) }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
