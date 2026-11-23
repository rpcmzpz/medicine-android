package com.zhuhai.medicine.ui.cart

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhuhai.medicine.data.model.CartItem
import com.zhuhai.medicine.ui.common.money
import com.zhuhai.medicine.ui.theme.MedicineAppTheme
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onCheckout: () -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = viewModel()
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
                title = { Text("购物车") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←", fontSize = 24.sp)
                    }
                }
            )
        },
        bottomBar = {
            // 空车时不显示合计栏，否则「合计（0 件）¥0.00 + 灰色按钮」是纯噪音
            if (state.items.isNotEmpty()) {
                CheckoutBar(
                    state = state,
                    onCheckout = onCheckout
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                state.isLoading && state.items.isEmpty() -> CenterHint("正在加载购物车…", true)

                state.errorMessage != null && state.items.isEmpty() ->
                    ErrorView(state.errorMessage!!) { viewModel.load() }

                state.items.isEmpty() -> EmptyCart(onGoShopping = onBack)

                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = state.items, key = { it.cartId }) { item ->
                            CartRow(
                                item = item,
                                onMinus = { viewModel.changeQuantity(item.cartId, -1) },
                                onPlus = { viewModel.changeQuantity(item.cartId, 1) },
                                onDelete = { viewModel.remove(item.cartId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartRow(
    item: CartItem,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                // 后端购物车 image_url 目前恒为 null，统一用首字占位
                Text(
                    text = item.medicineName.take(1),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = item.medicineName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onDelete) {
                        Text("删除", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "¥${item.price.money()}",
                        color = Color(0xFFD32F2F),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.isPrescription) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "处方药",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMinus,
                        enabled = item.quantity > 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("−", fontSize = 18.sp)
                    }
                    Text(
                        text = "${item.quantity}",
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = onPlus,
                        enabled = item.quantity < item.maxQuantity,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "小计 ¥${item.subtotal.money()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckoutBar(state: CartUiState, onCheckout: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (state.hasPrescription) {
                Text(
                    text = "含处方药，下单前需上传处方",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = "合计（${state.totalQuantity} 件）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "¥${state.totalAmount.money()}",
                        color = Color(0xFFD32F2F),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = onCheckout, enabled = state.canCheckout) {
                    Text("去结算")
                }
            }
        }
    }
}

@Composable
private fun EmptyCart(onGoShopping: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "购物车还是空的", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "去药品列表挑几件吧",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGoShopping) { Text("去逛逛") }
    }
}

@Composable
private fun CenterHint(text: String, showProgress: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(12.dp))
        }
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

@Preview(showBackground = true)
@Composable
private fun CartPreview() {
    MedicineAppTheme {
        Surface {
            Column(modifier = Modifier.padding(12.dp)) {
                CartRow(
                    item = CartItem(
                        cartId = 1,
                        medicineId = 1,
                        medicineName = "布洛芬缓释胶囊",
                        price = BigDecimal("18.50"),
                        quantity = 2,
                        subtotal = BigDecimal("37.00"),
                        drugType = 0,
                        stockAvailable = 500,
                        maxQuantity = 500
                    ),
                    onMinus = {},
                    onPlus = {},
                    onDelete = {}
                )
                Spacer(modifier = Modifier.height(10.dp))
                CartRow(
                    item = CartItem(
                        cartId = 2,
                        medicineId = 3,
                        medicineName = "硝苯地平控释片",
                        price = BigDecimal("32.00"),
                        quantity = 1,
                        subtotal = BigDecimal("32.00"),
                        drugType = 1,
                        stockAvailable = 200,
                        maxQuantity = 200
                    ),
                    onMinus = {},
                    onPlus = {},
                    onDelete = {}
                )
            }
        }
    }
}
