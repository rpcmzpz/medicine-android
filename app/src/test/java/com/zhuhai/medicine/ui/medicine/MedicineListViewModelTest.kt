package com.zhuhai.medicine.ui.medicine

import com.zhuhai.medicine.data.model.MedicineSummary
import com.zhuhai.medicine.data.model.PageData
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.MedicineListSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * 药品列表分页的纯 JVM 单元测试。
 *
 * 为什么值得单独测：分页是最容易写错的地方——
 * 页码从几开始、hasMore 怎么判、重复触发会不会把同一页请求两遍、
 * 下拉刷新会不会把正在飞的「加载更多」结果追加到新列表后面。
 * 这些分支靠手点很难覆盖，用单测钉死比靠手点可靠。
 *
 * 不连服务器、不碰 Android：用假的 [MedicineListSource] + 替换主线程调度器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicineListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope 用 Dispatchers.Main.immediate，JVM 上没有主线程，必须替换
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 假的药品数据源：按页返回构造好的数据，并记录每次请求的页码。
     * 用 [failAt] 可以让指定页返回错误，用来测失败分支。
     */
    private class FakeSource(
        private val total: Long,
        private val pageSize: Int = 20,
        private val failAt: Int? = null
    ) : MedicineListSource {

        val requestedPages = mutableListOf<Int>()

        override suspend fun list(page: Int, pageSize: Int): ApiResult<PageData<MedicineSummary>> {
            requestedPages += page
            if (page == failAt) {
                return ApiResult.NetworkError("网络不可用")
            }
            val from = (page - 1) * this.pageSize
            val to = minOf(from + this.pageSize, total.toInt())
            val items = (from until to).map { index ->
                MedicineSummary(
                    medicineId = (index + 1).toLong(),
                    name = "药品$index",
                    brand = "品牌",
                    specification = "规格",
                    price = BigDecimal("10.00"),
                    salesCount = index,
                    drugType = 0,
                    stockAvailable = 100
                )
            }
            return ApiResult.Success(PageData(list = items, total = total, page = page))
        }
    }

    @Test
    fun `首屏加载第一页并按总数算出还有下一页`() = runTest(dispatcher) {
        val source = FakeSource(total = 50)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(20, state.items.size)
        assertEquals(50L, state.total)
        assertEquals(1, state.page)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf(1), source.requestedPages)
    }

    @Test
    fun `总数不足一页时没有下一页`() = runTest(dispatcher) {
        val source = FakeSource(total = 8)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(8, state.items.size)
        assertFalse(state.hasMore)
    }

    @Test
    fun `触底加载下一页是追加而不是替换`() = runTest(dispatcher) {
        val source = FakeSource(total = 50)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(40, state.items.size)
        assertEquals(2, state.page)
        assertTrue(state.hasMore)
        // 第一页的 20 条必须还在，不是被第二页顶掉
        assertEquals(1L, state.items.first().medicineId)
        assertEquals(listOf(1, 2), source.requestedPages)
    }

    @Test
    fun `加载到最后一页后 hasMore 变 false`() = runTest(dispatcher) {
        val source = FakeSource(total = 50)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(50, state.items.size)
        assertEquals(3, state.page)
        assertFalse(state.hasMore)
        // 没有下一页时不该再发请求
        val afterLast = source.requestedPages.size
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(afterLast, source.requestedPages.size)
    }

    @Test
    fun `连续触发 loadMore 不会把同一页请求两遍`() = runTest(dispatcher) {
        val source = FakeSource(total = 100)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()

        // 模拟用户连续快速触底：三个 loadMore 排在一起
        vm.loadMore()
        vm.loadMore()
        vm.loadMore()
        advanceUntilIdle()

        // 只应请求了第 2 页一次
        assertEquals(listOf(1, 2), source.requestedPages)
        assertEquals(40, vm.uiState.value.items.size)
    }

    @Test
    fun `加载更多失败时保留已有列表并给出提示`() = runTest(dispatcher) {
        val source = FakeSource(total = 50, failAt = 2)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        val state = vm.uiState.value
        // 老数据不能被清掉
        assertEquals(20, state.items.size)
        assertNotNull(state.message)
        assertNull(state.errorMessage)
        // 失败后标志要复位，否则永远加载不了下一页
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `下拉刷新回到第一页并清掉加载更多的结果`() = runTest(dispatcher) {
        val source = FakeSource(total = 50)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(40, vm.uiState.value.items.size)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(20, state.items.size)
        assertEquals(1, state.page)
        assertTrue(state.hasMore)
        assertEquals(listOf(1, 2, 1), source.requestedPages)
    }

    @Test
    fun `首屏失败时列表为空走整屏错误`() = runTest(dispatcher) {
        val source = FakeSource(total = 50, failAt = 1)
        val vm = MedicineListViewModel(source)

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.items.isEmpty())
        assertNotNull(state.errorMessage)
        assertNull(state.message)
        assertFalse(state.isLoading)
    }

    @Test
    fun `已有数据后刷新失败不清空列表`() = runTest(dispatcher) {
        // 可控的数据源：第一页成功，之后可以随时切成失败
        var shouldFail = false
        val source = object : MedicineListSource {
            override suspend fun list(page: Int, pageSize: Int): ApiResult<PageData<MedicineSummary>> {
                if (shouldFail) return ApiResult.NetworkError("网络不可用")
                val from = (page - 1) * pageSize
                val to = minOf(from + pageSize, 50)
                val items = (from until to).map { index ->
                    MedicineSummary(
                        medicineId = (index + 1).toLong(),
                        name = "药品$index",
                        brand = "品牌",
                        specification = "规格",
                        price = BigDecimal("10.00"),
                        salesCount = index,
                        drugType = 0,
                        stockAvailable = 100
                    )
                }
                return ApiResult.Success(PageData(list = items, total = 50L, page = page))
            }
        }

        val vm = MedicineListViewModel(source)
        vm.load()
        advanceUntilIdle()
        assertEquals(20, vm.uiState.value.items.size)

        // 网络断了，再刷新一次
        shouldFail = true
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        // 刷新失败时列表里的数据必须还在，且走的是 Snackbar 提示而不是整屏错误
        assertEquals(20, state.items.size)
        assertNotNull(state.message)
        assertNull(state.errorMessage)
        assertFalse(state.isRefreshing)
    }
}
