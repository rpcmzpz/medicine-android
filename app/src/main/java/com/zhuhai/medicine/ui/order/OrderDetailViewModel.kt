package com.zhuhai.medicine.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.model.OrderDetail
import com.zhuhai.medicine.data.model.PaymentMethod
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.OrderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val isLoading: Boolean = true,
    val detail: OrderDetail? = null,
    val errorMessage: String? = null,
    /** 支付/取消/确认收货进行中，用来禁用按钮防连点 */
    val isActionRunning: Boolean = false,
    val showPaySheet: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false
) {
    // 三个开关都从 detail 推导，不额外存状态——避免「详情刷新了但按钮状态没变」这类不一致
    // 状态机：0 待支付 → 支付 → 1 待审核(处方单)/2 待配药 → 3 待配送 → 4 配送中 → 5 已完成
    val canPay: Boolean get() = detail?.orderStatus == 0
    val canCancel: Boolean get() = detail?.orderStatus == 0
    val canConfirm: Boolean get() = detail?.orderStatus == 3 || detail?.orderStatus == 4
    val hasAction: Boolean get() = canPay || canCancel || canConfirm

    /** 处方单已付款、等药师审核：页面需要自动轮询审核结果 */
    val isWaitingReview: Boolean
        get() = detail?.orderStatus == 1 && (detail.hasPrescription == 1)
}

class OrderDetailViewModel @JvmOverloads constructor(
    /** 订单 id 来自导航参数，必须由创建方传入 */
    private val orderId: Long,
    private val repository: OrderRepository = OrderRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    private var reviewPollJob: Job? = null

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = repository.detail(orderId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, detail = result.data, errorMessage = null)
                    }
                    startReviewPolling()
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                ApiResult.Unauthorized -> unauthorized()
            }
        }
    }

    /**
     * 处方审核轮询。
     *
     * 药师在后台审方，客户端拿不到推送，只能主动问。
     * 这里用「短轮询 + 上限」：每 5 秒拉一次详情，最多 2 分钟。
     * 不用 WebSocket/长连接是因为 MVP 阶段没有服务端推送通道，
     * 而轮询的代价（一次订单详情查询）在这个体量下可以接受。
     *
     * 三重退出条件：订单状态离开「待审核」、页面销毁（viewModelScope 取消）、次数耗尽。
     */
    private fun startReviewPolling() {
        if (!_uiState.value.isWaitingReview) return
        if (reviewPollJob?.isActive == true) return

        reviewPollJob = viewModelScope.launch {
            repeat(REVIEW_POLL_TIMES) {
                delay(REVIEW_POLL_INTERVAL_MS)
                if (!_uiState.value.isWaitingReview) return@launch
                refreshSilently()
            }
        }
    }

    /** 静默刷新：不动 isLoading，避免整页闪一下骨架屏 */
    private suspend fun refreshSilently() {
        when (val result = repository.detail(orderId)) {
            is ApiResult.Success -> _uiState.update { it.copy(detail = result.data) }
            is ApiResult.BusinessError -> Unit
            is ApiResult.NetworkError -> Unit
            ApiResult.Unauthorized -> unauthorized()
        }
    }

    fun openPaySheet() = _uiState.update { it.copy(showPaySheet = true) }

    fun closePaySheet() = _uiState.update { it.copy(showPaySheet = false) }

    /**
     * 支付。
     * 成功后 **必须重新拉一次详情**：接口只返回新的 order_status，
     * 支付时间/支付方式标签都是详情接口拼的，不刷新就显示不出来。
     */
    fun pay(method: PaymentMethod) {
        if (_uiState.value.isActionRunning) return

        _uiState.update { it.copy(isActionRunning = true, showPaySheet = false) }

        viewModelScope.launch {
            when (val result = repository.pay(orderId, method.code)) {
                is ApiResult.Success -> {
                    showMessage("支付成功，流水号 ${result.data.transactionNo ?: "-"}")
                    reloadAfterAction()
                }

                is ApiResult.BusinessError -> failAction(result.message)
                is ApiResult.NetworkError -> failAction(result.message)
                ApiResult.Unauthorized -> unauthorized()
            }
        }
    }

    fun cancel() {
        if (_uiState.value.isActionRunning) return

        _uiState.update { it.copy(isActionRunning = true) }

        viewModelScope.launch {
            when (val result = repository.cancel(orderId)) {
                is ApiResult.Success -> {
                    showMessage("订单已取消，库存已释放")
                    reloadAfterAction()
                }

                is ApiResult.BusinessError -> failAction(result.message)
                is ApiResult.NetworkError -> failAction(result.message)
                ApiResult.Unauthorized -> unauthorized()
            }
        }
    }

    fun confirmReceipt() {
        if (_uiState.value.isActionRunning) return

        _uiState.update { it.copy(isActionRunning = true) }

        viewModelScope.launch {
            when (val result = repository.confirm(orderId)) {
                is ApiResult.Success -> {
                    showMessage("已确认收货")
                    reloadAfterAction()
                }

                is ApiResult.BusinessError -> failAction(result.message)
                is ApiResult.NetworkError -> failAction(result.message)
                ApiResult.Unauthorized -> unauthorized()
            }
        }
    }

    /** 动作成功后的统一收尾：解锁按钮 + 重新拉详情（内部会再解一次锁） */
    private suspend fun reloadAfterAction() {
        _uiState.update { it.copy(isActionRunning = false) }
        load()
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun failAction(text: String) {
        _uiState.update { it.copy(isActionRunning = false, message = text) }
        // 业务错误常见于「订单状态已变更」，刷新一次让界面回到真实状态
        load()
    }

    private fun unauthorized() {
        TokenStore.clear()
        _uiState.update {
            it.copy(isLoading = false, isActionRunning = false, sessionExpired = true)
        }
    }

    private fun showMessage(text: String) = _uiState.update { it.copy(message = text) }

    companion object {
        private const val REVIEW_POLL_INTERVAL_MS = 5_000L
        /** 5s × 24 = 2 分钟；超时后不再自动刷新，用户可手动下拉/重进页面 */
        private const val REVIEW_POLL_TIMES = 24
    }
}
