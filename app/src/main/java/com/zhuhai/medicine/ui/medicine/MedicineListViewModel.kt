package com.zhuhai.medicine.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.model.MedicineSummary
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.MedicineListSource
import com.zhuhai.medicine.data.repository.MedicineRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 列表页状态。
 *
 * 这里用 data class 而不是 sealed interface：
 * 列表页的「加载中」和「已有数据」是**可以同时存在**的（下拉刷新时旧数据要留在屏幕上），
 * sealed 的互斥语义反而表达不了。sealed 更适合登录页那种「加载/成功/失败」三选一的场景。
 *
 * 两个错误字段分工明确，别混用：
 * - `errorMessage`：**首屏就失败**（列表为空），UI 渲染整屏错误页 + 重试按钮；
 * - `message`：**已经有数据了**才失败（下拉刷新失败、加载更多失败），UI 弹 Snackbar 提示。
 *   如果只有 errorMessage 一个字段，列表非空时错误就没人消费 → 用户以为刷新成功了。
 */
data class MedicineListUiState(
    val items: List<MedicineSummary> = emptyList(),
    val total: Long = 0L,
    /** 已经加载到第几页，从 1 开始 */
    val page: Int = 1,
    /** 是否还有下一页：拿到的条数 < 总数，说明后面还有 */
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    /** token 失效：清会话并通知上层跳登录页 */
    val sessionExpired: Boolean = false
)

/**
 * 药品列表。分页采用「累加」而不是「替换」：
 * 每次 loadMore 都把新一页 append 到 items 后面，LazyColumn 才能边滚边加载。
 */
class MedicineListViewModel @JvmOverloads constructor(
    private val repository: MedicineListSource = MedicineRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicineListUiState())

    /** 对外只读：UI 拿到的是 StateFlow 接口，无法反向写入 */
    val uiState: StateFlow<MedicineListUiState> = _uiState.asStateFlow()

    /** 只保留一个加载任务：下拉刷新和首屏加载互斥，避免两次请求打架 */
    private var loadJob: Job? = null

    /** 触底加载的任务：刷新开始时要把它取消，否则旧的一页会追加到新列表后面 */
    private var loadMoreJob: Job? = null

    private var hasLoaded = false

    /**
     * 由 UI 在本地网络权限就绪后调用。
     * 不放在 init 里，否则权限弹窗还没点，请求就已经超时失败了一次。
     */
    fun loadOnce() {
        if (hasLoaded) return
        hasLoaded = true
        load()
    }

    /** 加载第 1 页（首屏 + 下拉刷新都走这里，结果替换而不是追加） */
    fun load() {
        // 刷新时先取消还在飞的「加载更多」，否则它回来会把旧页追加到新列表后面
        loadMoreJob?.cancel()
        loadMoreJob = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 首屏显示整页 loading；已有数据时改用下拉刷新指示器，避免列表闪烁
            _uiState.update {
                it.copy(
                    isLoading = it.items.isEmpty(),
                    isRefreshing = it.items.isNotEmpty(),
                    // 取消了在飞的「加载更多」后要把这个标志复位，否则永远加载不了下一页
                    isLoadingMore = false,
                    errorMessage = null
                )
            }

            when (val result = repository.list(page = 1, pageSize = PAGE_SIZE)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        items = result.data.list,
                        total = result.data.total,
                        page = 1,
                        hasMore = result.data.list.size < result.data.total,
                        isLoading = false,
                        isRefreshing = false,
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

    fun refresh() = load()

    /**
     * 触底加载下一页。
     * 入口在 UI 的列表底部：那个「加载中」item 一旦被组合出来就说明用户滚到底了。
     */
    fun loadMore() {
        val state = _uiState.value
        // 四个条件缺一不可：任何一次重复触发都会让同一页被请求两遍
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true) }

        loadMoreJob = viewModelScope.launch {
            val nextPage = state.page + 1
            when (val result = repository.list(page = nextPage, pageSize = PAGE_SIZE)) {
                is ApiResult.Success -> _uiState.update { current ->
                    // 请求飞在路上时用户下了拉刷新：这一页属于旧列表，直接丢掉
                    if (current.page != state.page) {
                        current.copy(isLoadingMore = false)
                    } else {
                        val merged = current.items + result.data.list
                        current.copy(
                            isLoadingMore = false,
                            items = merged,
                            total = result.data.total,
                            page = nextPage,
                            hasMore = merged.size < result.data.total
                        )
                    }
                }

                // 加载更多失败不清空已有列表，只提示一句：用户还能继续看已加载的内容
                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isLoadingMore = false, message = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoadingMore = false, message = result.message)
                }

                ApiResult.Unauthorized -> {
                    TokenStore.clear()
                    _uiState.update {
                        it.copy(isLoadingMore = false, sessionExpired = true)
                    }
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 统一收口失败：列表为空 → 整屏错误页；列表已有数据 → Snackbar 提示，不清空屏幕。
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

    private companion object {
        /** 后端默认 page_size 也是 20，两边保持一致 */
        const val PAGE_SIZE = 20
    }
}
