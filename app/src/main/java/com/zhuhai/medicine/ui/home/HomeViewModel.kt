package com.zhuhai.medicine.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.CartRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页顶栏的购物车角标数据。
 *
 * 为什么不做全局事件总线（如 `CartBus` 单例 + SharedFlow）：
 * 本项目只有「回到首页」这一个观察点，用**生命周期刷新**（ON_RESUME）就够了，
 * 每多一层全局总线，就多一处内存泄漏与「谁先注册」的时序问题。
 * 代价是每次回首页多一个轻量 GET /cart/items 请求，可接受。
 */
class HomeViewModel(
    private val repository: CartRepository = CartRepository()
) : ViewModel() {

    private val _cartCount = MutableStateFlow(0)

    /** 角标数字 = 购物车件数（sum(quantity)），不是后端 total_count（那是条目种类数） */
    val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    /**
     * 静默拉一次购物车。
     * 网络失败时**保留上一次的数字**，不清零——否则地铁里断网一次，角标就莫名消失。
     */
    fun refreshCartCount() {
        viewModelScope.launch {
            when (val result = repository.list()) {
                is ApiResult.Success -> {
                    val count = result.data.list.sumOf { it.quantity }
                    if (count != _cartCount.value) _cartCount.value = count
                }

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _sessionExpired.value = true
                }

                else -> Unit
            }
        }
    }
}
