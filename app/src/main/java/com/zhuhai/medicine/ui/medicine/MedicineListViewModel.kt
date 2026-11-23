package com.zhuhai.medicine.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.model.MedicineSummary
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.MedicineRepository
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
 */
data class MedicineListUiState(
    val items: List<MedicineSummary> = emptyList(),
    val total: Long = 0L,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class MedicineListViewModel @JvmOverloads constructor(
    private val repository: MedicineRepository = MedicineRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicineListUiState())

    /** 对外只读：UI 拿到的是 StateFlow 接口，无法反向写入 */
    val uiState: StateFlow<MedicineListUiState> = _uiState.asStateFlow()

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

    fun load() {
        viewModelScope.launch {
            // 首屏显示整页 loading；已有数据时改用下拉刷新指示器，避免列表闪烁
            _uiState.update {
                it.copy(
                    isLoading = it.items.isEmpty(),
                    isRefreshing = it.items.isNotEmpty(),
                    errorMessage = null
                )
            }

            when (val result = repository.list(page = 1, pageSize = 20)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        items = result.data.list,
                        total = result.data.total,
                        isLoading = false,
                        isRefreshing = false
                    )
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }

                ApiResult.Unauthorized -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "登录状态已失效，请重新登录"
                    )
                }
            }
        }
    }

    fun refresh() = load()
}
