package com.zhuhai.medicine.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.model.MedicineDetail
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.CartRepository
import com.zhuhai.medicine.data.repository.MedicineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 药品详情页状态。
 *
 * `message` 是一次性事件：Snackbar 展示完由 UI 调 [MedicineDetailViewModel.consumeMessage]
 * 复位，否则旋转屏幕会重复弹一次。
 */
data class MedicineDetailUiState(
    val isLoading: Boolean = false,
    val detail: MedicineDetail? = null,
    val errorMessage: String? = null,
    val quantity: Int = 1,
    val isAdding: Boolean = false,
    val message: String? = null,
    /** token 失效，UI 据此跳回登录页 */
    val sessionExpired: Boolean = false
) {
    /** 单次购买上限：库存与 99 取小，防止手滑买了 500 盒 */
    val maxQuantity: Int get() = (detail?.stockQuantity ?: 0).coerceAtMost(99)

    val inStock: Boolean get() = (detail?.stockQuantity ?: 0) > 0

    val canAddToCart: Boolean get() = detail != null && inStock && !isAdding
}

class MedicineDetailViewModel @JvmOverloads constructor(
    private val medicineRepository: MedicineRepository = MedicineRepository(),
    private val cartRepository: CartRepository = CartRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicineDetailUiState())
    val uiState: StateFlow<MedicineDetailUiState> = _uiState.asStateFlow()

    /** 已经加载过的药品 id，避免每次重组都重新请求 */
    private var loadedId: Long = 0L

    fun load(medicineId: Long, force: Boolean = false) {
        if (medicineId <= 0L) {
            _uiState.update { it.copy(errorMessage = "药品参数无效") }
            return
        }
        if (!force && loadedId == medicineId && _uiState.value.detail != null) return

        loadedId = medicineId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = medicineRepository.detail(medicineId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        detail = result.data,
                        // 缺货时把数量压到 0，按钮本来就禁用，这里只是让展示一致
                        quantity = if (result.data.stockQuantity > 0) 1 else 0
                    )
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "登录已过期，请重新登录",
                            sessionExpired = true
                        )
                    }
                }
            }
        }
    }

    fun increaseQuantity() {
        _uiState.update { state ->
            if (state.quantity >= state.maxQuantity) state
            else state.copy(quantity = state.quantity + 1)
        }
    }

    fun decreaseQuantity() {
        _uiState.update { state ->
            if (state.quantity <= 1) state else state.copy(quantity = state.quantity - 1)
        }
    }

    fun addToCart() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (state.isAdding) return
        if (!state.inStock) {
            _uiState.update { it.copy(message = "该药品暂时缺货") }
            return
        }

        _uiState.update { it.copy(isAdding = true) }

        viewModelScope.launch {
            when (val result = cartRepository.addItem(detail.medicineId, state.quantity)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isAdding = false, message = "已加入购物车")
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isAdding = false, message = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isAdding = false, message = result.message)
                }

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _uiState.update {
                        it.copy(
                            isAdding = false,
                            message = "登录已过期，请重新登录",
                            sessionExpired = true
                        )
                    }
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
