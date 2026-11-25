package com.zhuhai.medicine.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.model.CartItem
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.CartRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * 购物车状态。
 *
 * 注意后端 `total_count` 是**条目种类数**（3 种药 = 3），不是件数；
 * 件数由前端自己 sum(quantity) 算，别混用。
 */
data class CartUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<CartItem> = emptyList(),
    val errorMessage: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false
) {
    val totalAmount: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.price.multiply(BigDecimal.valueOf(item.quantity.toLong()))
        }

    val totalQuantity: Int get() = items.sumOf { it.quantity }

    val hasPrescription: Boolean get() = items.any { it.isPrescription }

    val canCheckout: Boolean get() = items.isNotEmpty()
}

class CartViewModel @JvmOverloads constructor(
    private val repository: CartRepository = CartRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    /**
     * 每个购物车项一个待执行的「改数量」任务。
     * 用户连点 5 次「+」只会发 1 个请求——新任务开始前先取消旧的。
     */
    private val pendingUpdates = mutableMapOf<Long, Job>()

    fun load(showLoading: Boolean = true) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

        viewModelScope.launch {
            when (val result = repository.list()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        items = result.data.list,
                        errorMessage = null
                    )
                }

                is ApiResult.BusinessError -> finishWithError(result.message)
                is ApiResult.NetworkError -> finishWithError(result.message)

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, sessionExpired = true)
                    }
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        load(showLoading = false)
    }

    /**
     * 改数量：先本地改（界面立刻响应），400ms 内没有新操作才真正发请求。
     * 失败时用服务端数据覆盖本地，避免界面与后端长期不一致。
     */
    fun changeQuantity(cartId: Long, delta: Int) {
        val item = _uiState.value.items.firstOrNull { it.cartId == cartId } ?: return
        val target = (item.quantity + delta).coerceIn(1, maxOf(1, item.maxQuantity))
        if (target == item.quantity) {
            // 撞到上限/下限，给一句提示，否则用户会以为按钮坏了
            if (delta > 0) showMessage("库存仅剩 ${item.maxQuantity} 件")
            return
        }

        _uiState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.cartId == cartId) {
                        it.copy(
                            quantity = target,
                            subtotal = it.price.multiply(BigDecimal.valueOf(target.toLong()))
                        )
                    } else it
                }
            )
        }

        pendingUpdates[cartId]?.cancel()
        pendingUpdates[cartId] = viewModelScope.launch {
            delay(QUANTITY_DEBOUNCE_MS)
            when (val result = repository.updateQuantity(cartId, target)) {
                is ApiResult.Success -> Unit // 静默成功，界面已经是新值

                is ApiResult.BusinessError -> {
                    showMessage(result.message)
                    pendingUpdates.remove(cartId)
                    load(showLoading = false)
                }

                is ApiResult.NetworkError -> {
                    showMessage(result.message)
                    pendingUpdates.remove(cartId)
                    load(showLoading = false)
                }

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _uiState.update { it.copy(sessionExpired = true) }
                }
            }
        }
    }

    /** 删除同样是乐观更新：先从列表移除，失败再拉回真实数据 */
    fun remove(cartId: Long) {
        val backup = _uiState.value.items
        _uiState.update { state ->
            state.copy(items = state.items.filterNot { it.cartId == cartId })
        }

        viewModelScope.launch {
            when (val result = repository.remove(cartId)) {
                is ApiResult.Success -> showMessage("已移出购物车")

                is ApiResult.BusinessError -> {
                    _uiState.update { it.copy(items = backup) }
                    showMessage(result.message)
                }

                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(items = backup) }
                    showMessage(result.message)
                }

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _uiState.update { it.copy(sessionExpired = true) }
                }
            }
        }
    }

    private fun showMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }

    /**
     * 统一收口失败：购物车为空 → 整屏错误页；购物车已有数据 → Snackbar 提示，不清空屏幕。
     * 只设 errorMessage 的话，列表非空时错误没人消费 → 断网下拉刷新会静默失败。
     */
    private fun finishWithError(text: String) {
        _uiState.update {
            if (it.items.isEmpty()) {
                it.copy(isLoading = false, isRefreshing = false, errorMessage = text)
            } else {
                it.copy(isLoading = false, isRefreshing = false, message = text)
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private companion object {
        /** 400ms：比双击间隔长，比人的犹豫短 */
        const val QUANTITY_DEBOUNCE_MS = 400L
    }
}
