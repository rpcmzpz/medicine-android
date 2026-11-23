package com.zhuhai.medicine.ui.common

import com.zhuhai.medicine.data.remote.ApiClient
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * UI 层的两个纯函数。放在 common 里供列表页和详情页共用，
 * 避免同一个格式化逻辑写两遍后改一处漏一处。
 */

/** 价格统一两位小数，避免 12.5 / 12.500000 这类不整齐的显示 */
fun BigDecimal.money(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()

/**
 * 后端 image_url 可能是绝对地址，也可能是 `/uploads/xxx.jpg` 这类相对路径。
 * 相对路径必须补上站点根地址，否则 Coil 拿到的字符串无法解析成 URL。
 */
fun resolveImageUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> ApiClient.ORIGIN + value
        else -> "${ApiClient.ORIGIN}/$value"
    }
}

/** 评分保留一位小数，后端给的是 4.5000 */
fun Double.ratingText(): String = String.format("%.1f", this)

/**
 * 订单时间。后端 LocalDateTime 序列化成 `2026-11-23T17:04:05`，
 * 直接把 T 换成空格、再截掉秒——列表上一行放得下，也不会显示毫秒噪声。
 *
 * 这里用 `take(16)` 而不是 substring：字符串比 16 短时 substring 会抛异常，
 * take 则是「有多少拿多少」，对脏数据更宽容。
 */
fun String?.dateTimeText(): String {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return "-"
    return raw.replace('T', ' ').take(16)
}
