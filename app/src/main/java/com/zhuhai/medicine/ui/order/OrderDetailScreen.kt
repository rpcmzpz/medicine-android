package com.zhuhai.medicine.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhuhai.medicine.data.model.OrderDetail
import com.zhuhai.medicine.data.model.OrderLine
import com.zhuhai.medicine.data.model.PaymentMethod
import com.zhuhai.medicine.ui.common.dateTimeText
import com.zhuhai.medicine.ui.common.money

/**
 * 订单详情。
 *
 * 详情页承载了三个写操作：支付 / 取消 / 确认收货。
 * 每个操作成功后都重新拉详情，因为接口只回一个状态码，
 * 真正的「支付方式」「支付时间」要详情接口才会拼出来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: Long,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    // 有参 ViewModel 必须自己给 initializer，否则默认工厂找不到无参构造会崩
    viewModel: OrderDetailViewModel = viewModel { OrderDetailViewModel(orderId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }

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
                title = { Text("订单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←", fontSize = 24.sp)
                    }
                }
            )
        },
        bottomBar = {
            val detail = state.detail
            if (detail != null && state.hasAction) {
                ActionBar(
                    detail = detail,
                    isRunning = state.isActionRunning,
                    onPay = viewModel::openPaySheet,
                    onCancel = viewModel::cancel,
                    onConfirm = viewModel::confirmReceipt
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                state.isLoading -> CenterHint()

                state.errorMessage != null && state.detail == null ->
                    ErrorView(state.errorMessage!!) { viewModel.load() }

                state.detail != null -> DetailContent(state.detail!!)

                else -> CenterHint()
            }
        }
    }

    if (state.showPaySheet) {
        val amount = state.detail?.actualAmount
        PayMethodDialog(
            amountText = amount?.money() ?: "0.00",
            onConfirm = viewModel::pay,
            onDismiss = viewModel::closePaySheet
        )
    }
}

@Composable
private fun DetailContent(detail: OrderDetail) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusCard(detail)
        if (detail.address != null) AddressCard(detail)
        ItemsCard(detail.items)
        AmountCard(detail)
        if (detail.payment != null) PaymentCard(detail)
        if (detail.delivery != null) DeliveryCard(detail)
        TimeCard(detail)
    }
}

@Composable
private fun StatusCard(detail: OrderDetail) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = detail.orderStatusLabel
                    ?: com.zhuhai.medicine.data.model.ORDER_STATUS_LABELS
                        .getOrElse(detail.orderStatus) { "未知" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor(detail.orderStatus)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "订单号：${detail.orderNo ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (detail.remark != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "备注：${detail.remark}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddressCard(detail: OrderDetail) {
    val address = detail.address ?: return

    SectionCard(title = "收货信息") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = address.receiverName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = address.phone.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = address.detail.orEmpty(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ItemsCard(items: List<OrderLine>) {
    SectionCard(title = "商品清单") {
        if (items.isEmpty()) {
            Text(
                text = "暂无商品明细",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items.forEach { line ->
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.medicineName ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "¥${line.price.money()} × ${line.quantity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = "¥${line.subtotal.money()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AmountCard(detail: OrderDetail) {
    SectionCard(title = "金额") {
        AmountRow("商品金额", "¥${detail.totalAmount.money()}")
        AmountRow("优惠", "−¥${detail.discountAmount.money()}")
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("实付", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "¥${detail.actualAmount.money()}",
                color = Color(0xFFD32F2F),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PaymentCard(detail: OrderDetail) {
    val payment = detail.payment ?: return

    SectionCard(title = "支付信息") {
        AmountRow("支付方式", payment.paymentMethodLabel ?: "-")
        AmountRow("支付状态", payment.paymentStatusLabel ?: "-")
    }
}

@Composable
private fun DeliveryCard(detail: OrderDetail) {
    val delivery = detail.delivery ?: return

    SectionCard(title = "配送信息") {
        AmountRow("配送状态", delivery.deliveryStatusLabel ?: "-")
    }
}

@Composable
private fun TimeCard(detail: OrderDetail) {
    SectionCard(title = "时间") {
        AmountRow("下单时间", detail.createdAt.dateTimeText())
        if (detail.paidAt != null) AmountRow("支付时间", detail.paidAt.dateTimeText())
        if (detail.completedAt != null) AmountRow("完成时间", detail.completedAt.dateTimeText())
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActionBar(
    detail: OrderDetail,
    isRunning: Boolean,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }

    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "实付 ¥${detail.actualAmount.money()}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))

            if (detail.orderStatus == 0) {
                OutlinedButton(
                    onClick = { showCancelConfirm = true },
                    enabled = !isRunning
                ) { Text("取消订单") }
            }

            if (detail.orderStatus == 0 || detail.orderStatus == 1) {
                Button(onClick = onPay, enabled = !isRunning) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("立即支付")
                }
            }

            if (detail.orderStatus == 3 || detail.orderStatus == 4) {
                Button(onClick = onConfirm, enabled = !isRunning) { Text("确认收货") }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("取消订单") },
            text = { Text("取消后库存会立即释放，订单不可恢复。确定取消吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onCancel()
                    }
                ) { Text("确定取消") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("再想想") }
            }
        )
    }
}

@Composable
private fun PayMethodDialog(
    amountText: String,
    onConfirm: (PaymentMethod) -> Unit,
    onDismiss: () -> Unit
) {
    // 记住用户选了哪个：rememberSaveable 在转屏/进程重建后仍能恢复
    var selected by rememberSaveable { mutableStateOf(PaymentMethod.WECHAT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择支付方式") },
        text = {
            Column {
                PaymentMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == method,
                            onClick = { selected = method }
                        )
                        Text(text = method.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "这是模拟支付，不会真实扣款。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("确认支付 ¥$amountText")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun statusColor(status: Int): Color = when (status) {
    0, 1 -> Color(0xFFD32F2F)
    5 -> Color(0xFF2E7D32)
    6, 7 -> Color(0xFF757575)
    else -> Color(0xFF1565C0)
}

@Composable
private fun CenterHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("正在加载订单…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
