package com.zhuhai.medicine.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.model.OrderSummary
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.OrderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 订单状态筛选。
 *
 * `enum class` 带构造参数（status / label）是 Kotlin 很常用的写法：
 * 每个枚举项都必须把参数传全，相当于「一个常量 + 两个字段」打包在一起。
 * `ALL` 的 status 是 null，正好对应后端「不传 status 就查全部」。
 *
 * 注意：后端 status 只能传单个值，所以 status=2/3 的订单只在「全部」里出现。
 */
enum class OrderFilter(val status: Int?, val label: String) {
    ALL(null, "全部"),
    UNPAID(0, "待支付"),
    AUDITING(1, "待审核"),
    DELIVERING(4, "配送中"),
    FINISHED(5, "已完成"),
    CANCELED(6, "已取消")
}

data class OrderListUiState(
    val filter: OrderFilter = OrderFilter.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val orders: List<OrderSummary> = emptyList(),
    /** 已经加载到第几页，从 1 开始 */
    val page: Int = 1,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false
)

/**
 * 订单列表。分页采用「累加」而不是「替换」：
 * 每次 loadMore 都把新一页 append 到 orders 后面，LazyColumn 才能边滚边加载。
 */
class OrderListViewModel @JvmOverloads constructor(
    private val repository: OrderRepository = OrderRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderListUiState())
    val uiState: StateFlow<OrderListUiState> = _uiState.asStateFlow()

    /** 只保留一个加载任务：切 Tab 时直接取消上一次，避免旧结果盖掉新结果 */
    private var loadJob: Job? = null

    /** 是否成功加载过。用于区分「首次进入要显示大 loading」和「从详情页回来只静默刷新」 */
    private var loadedOnce = false

    /** 首次进入：显示 loading 骨架 */
    fun load() = loadInternal(showLoading = true, isRefresh = false)

    /** 下拉刷新：保留旧列表，只在顶部转圈 */
    fun refresh() = loadInternal(showLoading = false, isRefresh = true)

    /**
     * 从详情页返回时调用。
     * 只做静默刷新——用户刚看完详情，回到列表时列表突然整体闪一下白屏会很难受。
     */
    fun onResumed() {
        if (loadedOnce) loadInternal(showLoading = false, isRefresh = false)
    }

    fun selectFilter(filter: OrderFilter) {
        if (_uiState.value.filter == filter) return

        // 换 Tab 先清空列表：否则旧 Tab 的订单会在新 Tab 下短暂残留
        _uiState.update {
            it.copy(filter = filter, orders = emptyList(), page = 1, hasMore = false)
        }
        loadInternal(showLoading = true, isRefresh = false)
    }

    private fun loadInternal(showLoading: Boolean, isRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = showLoading,
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }

            when (val result = repository.list(status = _uiState.value.filter.status, page = 1)) {
                is ApiResult.Success -> {
                    loadedOnce = true
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            orders = result.data.list,
                            page = 1,
                            // 拿到的条数 < 总数，说明后面还有
                            hasMore = result.data.list.size < result.data.total,
                            errorMessage = null
                        )
                    }
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

    /**
     * 触底加载下一页。
     * 入口在 UI 的列表底部：那个「加载中」item 一旦被组合出来就说明用户滚到底了。
     */
    fun loadMore() {
        val state = _uiState.value
        // 五个条件缺一不可：任何一次重复触发都会让同一页被请求两遍
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val nextPage = state.page + 1
            when (val result = repository.list(status = state.filter.status, page = nextPage)) {
                is ApiResult.Success -> _uiState.update { current ->
                    // 请求飞在路上时用户切了 Tab：这一页属于旧筛选，直接丢掉
                    if (current.filter != state.filter) {
                        current.copy(isLoadingMore = false)
                    } else {
                        val merged = current.orders + result.data.list
                        current.copy(
                            isLoadingMore = false,
                            orders = merged,
                            page = nextPage,
                            hasMore = merged.size < result.data.total
                        )
                    }
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isLoadingMore = false, message = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoadingMore = false, message = result.message)
                }

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _uiState.update { it.copy(isLoadingMore = false, sessionExpired = true) }
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun finishWithError(text: String) {
        _uiState.update {
            // 订单列表非空时不能把整页替换成错误页，否则用户刚加载完的订单全没了；
            // 这种情况用 Snackbar 提示一句「刷新失败」，旧数据继续留着。
            if (it.orders.isEmpty()) {
                it.copy(isLoading = false, isRefreshing = false, errorMessage = text)
            } else {
                it.copy(isLoading = false, isRefreshing = false, message = text)
            }
        }
    }
}
