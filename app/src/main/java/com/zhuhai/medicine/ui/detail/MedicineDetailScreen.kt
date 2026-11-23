package com.zhuhai.medicine.ui.detail

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zhuhai.medicine.data.model.CategoryRef
import com.zhuhai.medicine.data.model.MedicineDetail
import com.zhuhai.medicine.data.model.ReviewItem
import com.zhuhai.medicine.ui.common.money
import com.zhuhai.medicine.ui.common.ratingText
import com.zhuhai.medicine.ui.common.resolveImageUrl
import com.zhuhai.medicine.ui.theme.MedicineAppTheme
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineDetailScreen(
    medicineId: Long,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicineDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // medicineId 变化才重新拉取；ViewModel 内部还有 loadedId 做二次去重
    LaunchedEffect(medicineId) { viewModel.load(medicineId) }

    // 一次性提示：展示完立刻复位，避免旋转屏幕后重复弹出
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
                title = {
                    Text(
                        text = state.detail?.name ?: "药品详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 不引 material-icons 依赖：一个字符就够，少 1MB 的包体
                        Text(text = "←", fontSize = 24.sp)
                    }
                }
            )
        },
        bottomBar = {
            val detail = state.detail
            if (detail != null) {
                AddToCartBar(
                    state = state,
                    onMinus = viewModel::decreaseQuantity,
                    onPlus = viewModel::increaseQuantity,
                    onAdd = viewModel::addToCart
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                state.isLoading && state.detail == null -> CenterHint("正在加载详情…")

                state.errorMessage != null && state.detail == null ->
                    ErrorView(message = state.errorMessage!!) {
                        viewModel.load(medicineId, force = true)
                    }

                state.detail != null -> DetailContent(state.detail!!)

                else -> CenterHint("暂无数据")
            }
        }
    }
}

@Composable
private fun DetailContent(detail: MedicineDetail) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { DetailHeader(detail) }
        item { PriceBlock(detail) }
        item { SpecBlock(detail) }
        if (!detail.description.isNullOrBlank()) {
            item {
                SectionTitle("药品说明")
                Text(
                    text = detail.description.orEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }
        item {
            SectionTitle(
                if (detail.reviewsCount > 0) "用户评价（${detail.reviewsCount}）" else "用户评价"
            )
        }
        if (detail.reviews.isEmpty()) {
            item {
                Text(
                    text = "暂无评价",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(detail.reviews) { review -> ReviewRow(review) }
        }
    }
}

@Composable
private fun DetailHeader(detail: MedicineDetail) {
    val imageUrl = resolveImageUrl(detail.imageUrl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl == null) {
            Text(
                text = detail.name.take(1),
                fontSize = 64.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = detail.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun PriceBlock(detail: MedicineDetail) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "¥${detail.price.money()}",
                color = Color(0xFFD32F2F),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            val original = detail.originalPrice
            if (original != null && original > detail.price) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "¥${original.money()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (detail.stockQuantity > 0) "库存 ${detail.stockQuantity}" else "暂时缺货",
                style = MaterialTheme.typography.labelMedium,
                color = if (detail.stockQuantity > 0) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = detail.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        val subtitle = listOfNotNull(detail.genericName, detail.brand, detail.specification)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(
                text = detail.drugTypeLabel ?: if (detail.isPrescription) "处方药" else "OTC",
                highlighted = detail.isPrescription
            )
            detail.category?.name?.takeIf { it.isNotBlank() }?.let { Tag(text = it) }
            detail.avgRating?.let { Tag(text = "评分 ${it.ratingText()}") }
        }

        if (detail.isPrescription) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "处方药下单前需上传处方，审核通过后才能发货。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SpecBlock(detail: MedicineDetail) {
    if (detail.approvalNumber.isNullOrBlank() && detail.category?.name.isNullOrBlank()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))
        detail.approvalNumber?.takeIf { it.isNotBlank() }?.let {
            InfoRow("批准文号", it)
        }
        detail.category?.name?.takeIf { it.isNotBlank() }?.let {
            InfoRow("所属分类", it)
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Tag(text: String, highlighted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (highlighted) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ReviewRow(review: ReviewItem) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = review.userName ?: "匿名用户",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "★".repeat(review.rating.coerceIn(0, 5)),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFA000)
            )
        }
        review.content?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddToCartBar(
    state: MedicineDetailUiState,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinus, enabled = state.quantity > 1) {
                Text(text = "−", fontSize = 22.sp)
            }
            Text(
                text = "${state.quantity}",
                modifier = Modifier.widthIn(min = 32.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = onPlus,
                enabled = state.quantity < state.maxQuantity
            ) {
                Text(text = "+", fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onAdd,
                enabled = state.canAddToCart
            ) {
                if (state.isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.inStock) "加入购物车" else "暂时缺货")
            }
        }
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

@Preview(showBackground = true)
@Composable
private fun MedicineDetailPreview() {
    MedicineAppTheme {
        DetailContent(
            detail = MedicineDetail(
                medicineId = 1,
                name = "布洛芬缓释胶囊",
                genericName = "布洛芬",
                brand = "中美史克",
                specification = "0.3g×20粒",
                approvalNumber = "国药准字H10900089",
                drugType = 0,
                drugTypeLabel = "OTC",
                description = "用于缓解轻至中度疼痛如头痛、关节痛、偏头痛、牙痛、肌肉痛、神经痛、痛经。",
                price = BigDecimal("18.50"),
                originalPrice = BigDecimal("25.00"),
                stockQuantity = 500,
                category = CategoryRef(6L, "解热镇痛"),
                reviews = listOf(
                    ReviewItem("a**", 5, "效果很好，退烧快，家庭常备药"),
                    ReviewItem("b**", 4, "价格实惠，比药店便宜多了")
                ),
                reviewsCount = 2,
                avgRating = 4.5
            )
        )
    }
}
