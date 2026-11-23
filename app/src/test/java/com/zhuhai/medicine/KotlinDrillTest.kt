package com.zhuhai.medicine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Kotlin 速通练习（对应作战计划 a5）。
 *
 * 用法：把每个 TODO() 换成实现，然后运行本文件。
 * 全绿 = a5 通过。现在是 6 个红，属于正常。
 *
 * 运行方式二选一：
 *   1) Android Studio 里点类名左边的 ▶（推荐，能单题跑）
 *   2) 命令行：gradlew :app:testDebugUnitTest --tests "com.zhuhai.medicine.KotlinDrillTest"
 */

// ============================================================
// 数据模型：与后端 /api/v1/medicines 返回字段一一对应
// ============================================================

data class Medicine(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val imageUrl: String?,   // 后端 image_url，实测 8 条全为 null
    val stockAvailable: Int,
    val drugType: Int        // 0 = OTC，1 = 处方药
)

data class CartItem(
    val medicine: Medicine,
    val quantity: Int
)

class KotlinDrillTest {

    private val ibuprofen = Medicine(1L, "布洛芬缓释胶囊", BigDecimal("18.50"), null, 500, 0)
    private val amoxicillin = Medicine(6L, "阿莫西林胶囊", BigDecimal("15.80"), null, 150, 1)

    // ------------------------------------------------------------
    // 第 1 题 · data class：copy 与解构
    // ------------------------------------------------------------
    // 要求：不要 new 一个新对象。用 ibuprofen.copy(...) 把库存改成 480、价格改成 19.90，
    //       然后用解构声明取前两个字段（注意：解构按构造参数顺序，不是按名字挑）。
    // 面试点：data class 自动生成 equals / hashCode / toString / copy / componentN。
    @Test
    fun `第1题 data class 的 copy 与解构`() {
        val changed = ibuprofen.copy(stockAvailable = 480, price = BigDecimal("19.90"))
        val (id, drugName, price) = changed      // 第 1 个是 id，第 2 个是 name, 第三个是price

        assertEquals(1L, id)
        assertEquals("布洛芬缓释胶囊", drugName)   // copy 不改原对象
        assertEquals(480, changed.stockAvailable)
        assertEquals(0, BigDecimal("19.90").compareTo(price))
        assertEquals(500, ibuprofen.stockAvailable)    // 原对象未被污染
    }

    // ------------------------------------------------------------
    // 第 2 题 · 空安全：?. ?: let，禁止用 !!
    // ------------------------------------------------------------
    // 要求：实现 cover()，imageUrl 为 null 或空白时返回 "默认占位图"，否则返回 url 本身。
    //       必须用 ?. 和 ?:，不许出现双感叹号。
    // 面试点：!! 只在"逻辑上绝不可能为空"时用，编码时用 ?: 兜底。
    private fun cover(m: Medicine): String = m.imageUrl?.takeIf { it.isNotBlank() } ?: "默认占位图"

    @Test
    fun `第2题 空安全兜底`() {
        assertEquals("默认占位图", cover(ibuprofen))
        assertEquals("默认占位图", cover(ibuprofen.copy(imageUrl = "   ")))
        assertEquals("https://a.com/1.png", cover(ibuprofen.copy(imageUrl = "https://a.com/1.png")))
    }

    // ------------------------------------------------------------
    // 第 3 题 · lambda 与高阶函数：购物车合计
    // ------------------------------------------------------------
    // 要求：items 里只算 OTC（drugType == 0），返回它们的小计之和。
    //       用 filter + map + fold（或 sumOf），不要写 for 循环。
    // 面试点：Kotlin 1.4 起 sumOf 有 BigDecimal 重载，可用；但如果你先 toDouble 再求和，
    //         精度会掉——金额链条上任何一步变成 Double 都等于前功尽弃。
    private fun otcTotal(items: List<CartItem>): BigDecimal =
        items.filter { it.medicine.drugType == 0 }
            .map { it.medicine.price.multiply(BigDecimal(it.quantity)) }
            .fold(BigDecimal.ZERO) { acc, v -> acc + v }

    @Test
    fun `第3题 lambda 合计`() {
        val items = listOf(
            CartItem(ibuprofen, 2),      // 18.50 × 2 = 37.00，OTC
            CartItem(amoxicillin, 3),    // 处方药，不计入
            CartItem(ibuprofen.copy(id = 7L, price = BigDecimal("28.00")), 1)  // 28.00，OTC
        )
        assertEquals(0, BigDecimal("65.00").compareTo(otcTotal(items)))
    }

    // ------------------------------------------------------------
    // 第 4 题 · 扩展函数：价格格式化
    // ------------------------------------------------------------
    // 要求：给 BigDecimal 加扩展函数 toMoney()，返回 "¥18.50"；
    //       必须用 setScale(2, RoundingMode.HALF_UP)，不要用 String.format。
    // 面试点：金额永远 BigDecimal + 指定舍入模式，Double 会丢精度。
    // ------------------------------------------------------------
    // ↓ 在这里写扩展函数（先把 TODO 换成你的实现）
    fun BigDecimal.toMoney(): String = "¥" + setScale(2, RoundingMode.HALF_UP).toPlainString()

    @Test
    fun `第4题 扩展函数`() {
        assertEquals("¥18.50", BigDecimal("18.5").toMoney())
        assertEquals("¥6.00", BigDecimal("6").toMoney())
        assertEquals("¥0.13", BigDecimal("0.125").toMoney())   // HALF_UP 进位
    }

    // ------------------------------------------------------------
    // 第 5 题 · when + sealed：列表页三态
    // ------------------------------------------------------------
    // 要求：实现 text()，用 when 表达式覆盖四种状态，返回对应文案。
    //       sealed 的好处：漏写分支编译器直接报错（穷尽性检查）。
    sealed interface UiState<out T> {
        data object Loading : UiState<Nothing>
        data class Success<T>(val data: T) : UiState<T>
        data object Empty : UiState<Nothing>
        data class Error(val message: String) : UiState<Nothing>
    }

    private fun <T> UiState<T>.text(): String = when (this) {
        UiState.Loading -> "加载中"
        is UiState.Success -> "共 ${data} 条"
        UiState.Empty -> "暂无数据"
        is UiState.Error -> "网络错误：$message"
    }

    @Test
    fun `第5题 sealed 与 when`() {
        assertEquals("加载中", UiState.Loading.text<String>())
        assertEquals("共 8 条", UiState.Success(8).text())
        assertEquals("暂无数据", UiState.Empty.text<String>())
        assertEquals("网络错误：超时", UiState.Error("超时").text())
    }

    // ------------------------------------------------------------
    // 第 6 题 · 协程：切线程
    // ------------------------------------------------------------
    // 要求：loadStock() 是挂起函数，内部用 withContext(Dispatchers.IO) 包住：
    //       delay(100) 后返回 500。
    // 面试点：Android 主线程不能做网络/磁盘，必须 withContext(IO)；suspend 只是标记，
    //         不指定 Dispatcher 仍然跑在调用者线程上。
    private suspend fun loadStock(): Int = withContext(Dispatchers.IO) {
        delay(100)
        500
    }

    @Test
    fun `第6题 协程切线程`() = runBlocking {
        assertEquals(500, loadStock())
    }
}

// ============================================================
// 参考答案在下面，做完再看。看答案只能验证，不能代替手写。
// ============================================================
/*
第 1 题
    val changed = ibuprofen.copy(stockAvailable = 480, price = BigDecimal("19.90"))
    val (id, drugName) = changed      // id = 1L, drugName = "布洛芬缓释胶囊"
    val price = changed.price

第 2 题
    private fun cover(m: Medicine): String =
        m.imageUrl?.takeIf { it.isNotBlank() } ?: "默认占位图"

第 3 题
    private fun otcTotal(items: List<CartItem>): BigDecimal =
        items.filter { it.medicine.drugType == 0 }
            .map { it.medicine.price.multiply(BigDecimal(it.quantity)) }
            .fold(BigDecimal.ZERO) { acc, v -> acc + v }

    // 更短的写法（Kotlin 1.4+ 有 BigDecimal 重载，实测可用）：
    // items.filter { it.medicine.drugType == 0 }
    //     .sumOf { it.medicine.price.multiply(BigDecimal(it.quantity)) }
    //
    // fold 仍然要会：需要自定义累积逻辑（拼字符串、求最大、累加两个字段）时只有 fold 能做。

第 4 题
    fun BigDecimal.toMoney(): String = "¥" + setScale(2, RoundingMode.HALF_UP).toPlainString()

第 5 题
    private fun <T> UiState<T>.text(): String = when (this) {
        UiState.Loading -> "加载中"
        is UiState.Success -> "共 ${data} 条"
        UiState.Empty -> "暂无数据"
        is UiState.Error -> "网络错误：$message"
    }

第 6 题
    private suspend fun loadStock(): Int = withContext(Dispatchers.IO) {
        delay(100)
        500
    }
*/
