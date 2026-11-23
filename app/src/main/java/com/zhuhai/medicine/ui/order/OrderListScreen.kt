package com.zhuhai.medicine.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhuhai.medicine.data.model.OrderSummary
import com.zhuhai.medicine.ui.common.dateTimeText
import com.zhuhai.medicine.ui.common.money

/**
 * 订单列表。
 *
 * 这里演示两个常用套路：
 * 1. `LifecycleEventEffect(ON_RESUME)`：从详情页支付/取消完返回时静默刷新，
 *    否则列表上还显示「待支付」，用户会以为操作没生效。
 * 2. 列表底部放一个「加载更多」item，它被组合出来就意味着用户滚到底了，
 *    在 item 里写 `LaunchedEffect(page)` 触发下一页——不用自己去算可见下标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }

    // ON_RESUME 在「首次进入」和「从详情页返回」时都会触发，
    // VM 内部用 loadedOnce 区分：首次交给上面的 load()，这里只做静默刷新。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("我的订单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←", fontSize = 24.sp)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            FilterBar(
                selected = state.filter,
                onSelect = viewModel::selectFilter
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.orders.isEmpty() -> CenterHint("正在加载订单…")

                    state.errorMessage != null && state.orders.isEmpty() ->
                        ErrorView(state.errorMessage!!) { viewModel.load() }

                    state.orders.isEmpty() -> EmptyOrders(state.filter)

                    else -> PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = state.orders, key = { it.orderId }) { order ->
                                OrderCard(
                                    order = order,
                                    onClick = { onOpenOrder(order.orderId) }
                                )
                            }

                            if (state.hasMore) {
                                item(key = "load_more") {
                                    // page 变化才重新触发，避免每帧重组都请求一次
                                    LaunchedEffect(state.page) { viewModel.loadMore() }
                                    LoadMoreFooter()
                                }
                            } else {
                                item(key = "no_more") { NoMoreHint() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    selected: OrderFilter,
    onSelect: (OrderFilter) -> Unit
) {
    // FilterChip 一行放不下 6 个，套一个横向滚动；间距用 spacedBy 保持均匀
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // entries 是 Kotlin 1.9 起的枚举集合，等价于原来的 values()，但不会每次调用都新建数组
        OrderFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: OrderSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = order.orderNo ?: "订单 #${order.orderId}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = order.statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor(order.orderStatus)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = order.createdAt.dateTimeText(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (order.hasPrescription == 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "处方药",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = "实付 ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "¥${order.actualAmount.money()}",
                    color = Color(0xFFD32F2F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (order.canPay) "去支付 ›" else "查看详情 ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (order.canPay) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 0待支付/1待审核 用红色提醒，其余一律次要色，避免整屏都在喊 */
private fun statusColor(status: Int): Color = when (status) {
    0, 1 -> Color(0xFFD32F2F)
    5 -> Color(0xFF2E7D32)
    else -> Color(0xFF757575)
}

@Composable
private fun LoadMoreFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "正在加载更多…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoMoreHint() {
    Text(
        text = "没有更多订单了",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun EmptyOrders(filter: OrderFilter) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (filter == OrderFilter.ALL) "还没有订单" else "没有${filter.label}的订单",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "去药品列表挑几件吧",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}
