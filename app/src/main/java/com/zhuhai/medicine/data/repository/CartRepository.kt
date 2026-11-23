package com.zhuhai.medicine.data.repository

import com.zhuhai.medicine.data.model.AddCartItemRequest
import com.zhuhai.medicine.data.model.CartData
import com.zhuhai.medicine.data.model.UpdateCartQuantityRequest
import com.zhuhai.medicine.data.remote.ApiClient
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.remote.ApiService
import com.zhuhai.medicine.data.remote.apiCall
import com.zhuhai.medicine.data.remote.apiCallUnit

/**
 * 购物车数据源。全部接口都需要登录（不在后端 JWT 白名单内），
 * 因此都会走 Authorization 头；token 失效时返回 [ApiResult.Unauthorized]。
 */
class CartRepository(
    private val api: ApiService = ApiClient.api
) {

    /**
     * 加入购物车。
     * 后端返回 `Result.success(null)`，data 就是 null——必须用 apiCallUnit，
     * 用 apiCall 会被误判成「服务端未返回数据」。
     */
    suspend fun addItem(medicineId: Long, quantity: Int = 1): ApiResult<Unit> =
        apiCallUnit { api.addCartItem(AddCartItemRequest(medicineId, quantity)) }

    suspend fun list(): ApiResult<CartData> = apiCall { api.listCartItems() }

    /** 改数量。quantity 由后端校验库存上限，超限返回业务错误而不是静默截断 */
    suspend fun updateQuantity(cartId: Long, quantity: Int): ApiResult<Unit> =
        apiCallUnit { api.updateCartQuantity(cartId, UpdateCartQuantityRequest(quantity)) }

    suspend fun remove(cartId: Long): ApiResult<Unit> =
        apiCallUnit { api.removeCartItem(cartId) }
}
