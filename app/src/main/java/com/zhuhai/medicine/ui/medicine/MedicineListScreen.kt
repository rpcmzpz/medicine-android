package com.zhuhai.medicine.ui.medicine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zhuhai.medicine.data.model.MedicineSummary
import com.zhuhai.medicine.ui.common.money
import com.zhuhai.medicine.ui.common.resolveImageUrl
import com.zhuhai.medicine.ui.permission.rememberLocalNetworkPermission
import com.zhuhai.medicine.ui.theme.MedicineAppTheme
import java.math.BigDecimal

@Composable
fun MedicineListScreen(
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicineListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasLocalNetworkPermission = rememberLocalNetworkPermission()

    // 权限就绪后再发请求；被拒绝时停在这里，避免无意义的超时等待
    LaunchedEffect(hasLocalNetworkPermission) {
        if (hasLocalNetworkPermission) viewModel.loadOnce()
    }

    if (!hasLocalNetworkPermission) {
        PermissionRequiredView(modifier = modifier)
        return
    }

    MedicineListContent(
        state = state,
        onRefresh = viewModel::refresh,
        onItemClick = onItemClick,
        modifier = modifier
    )
}

/**
 * 无状态内容层：只依赖入参，不持有 ViewModel。
 * 好处是 @Preview 可以直接喂假数据，不需要启动 Activity 或网络。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineListContent(
    state: MedicineListUiState,
    onRefresh: () -> Unit,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            state.isLoading -> CenterHint("正在加载药品…", showProgress = true)

            state.errorMessage != null && state.items.isEmpty() ->
                ErrorView(message = state.errorMessage, onRetry = onRefresh)

            state.items.isEmpty() -> CenterHint("暂无药品")

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = state.items, key = { it.medicineId }) { medicine ->
                    MedicineRow(medicine = medicine, onClick = { onItemClick(medicine.medicineId) })
                }
            }
        }
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

@Composable
private fun PermissionRequiredView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要「本地网络」权限",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Android 16 起，访问局域网内的服务需要单独授权。请到 系统设置 → 应用 → 本应用 → 权限 中开启「本地网络」，然后重新打开应用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MedicineRow(medicine: MedicineSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageUrl = resolveImageUrl(medicine.imageUrl)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl == null) {
                    // 后端 image_url 为空时用药品名首字占位，比纯灰块更易识别
                    Text(
                        text = medicine.name.take(1),
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = medicine.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicine.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = listOfNotNull(medicine.specification, medicine.brand)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥${medicine.price.money()}",
                        color = Color(0xFFD32F2F),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已售 ${medicine.salesCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (medicine.isPrescription) {
                        Text(
                            text = "处方药",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!medicine.inStock) {
                        Text(
                            text = "缺货",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MedicineListPreview() {
    MedicineAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MedicineListContent(
                state = MedicineListUiState(
                    items = listOf(
                        MedicineSummary(
                            medicineId = 1,
                            name = "布洛芬缓释胶囊",
                            brand = "芬必得",
                            specification = "0.3g×20粒",
                            price = BigDecimal("18.80"),
                            salesCount = 326,
                            drugType = 0,
                            stockAvailable = 100
                        ),
                        MedicineSummary(
                            medicineId = 2,
                            name = "阿莫西林胶囊",
                            brand = "石药",
                            specification = "0.25g×24粒",
                            price = BigDecimal("12.50"),
                            salesCount = 88,
                            drugType = 1,
                            stockAvailable = 0
                        )
                    )
                ),
                onRefresh = {},
                onItemClick = {}
            )
        }
    }
}
