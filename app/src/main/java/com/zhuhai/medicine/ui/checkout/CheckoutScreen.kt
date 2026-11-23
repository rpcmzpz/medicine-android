package com.zhuhai.medicine.ui.checkout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zhuhai.medicine.data.model.Address
import com.zhuhai.medicine.data.model.CartItem
import com.zhuhai.medicine.data.model.CreateOrderResult
import com.zhuhai.medicine.data.model.ORDER_STATUS_LABELS
import com.zhuhai.medicine.ui.common.money
import com.zhuhai.medicine.ui.common.toPrescriptionPart
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onBackHome: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    /**
     * 系统相册选择器（Photo Picker）。
     *
     * 用 PickMultipleVisualMedia 而不是「申请 READ_MEDIA_IMAGES + 自己查 MediaStore」：
     * - Android 13+ 走系统 Photo Picker，**完全不需要任何存储权限**；
     * - 13 以下自动回退到 ACTION_OPEN_DOCUMENT，同样不需要权限；
     * - 用户只授予这次选中的几张图，App 看不到整个相册，隐私边界更干净。
     * 回调里的 uris 是 content:// 授权 Uri，进程活着期间可直接读。
     */
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // 坏 Uri（被删除/无权限）会在 toPrescriptionPart 里返回 null，这里直接过滤掉
        val parts = uris.mapIndexedNotNull { index, uri -> context.toPrescriptionPart(uri, index) }
        viewModel.uploadPrescription(parts, uris.map { it.toString() })
    }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    val orderResult = state.orderResult

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (orderResult == null) "确认订单" else "下单成功") },
                navigationIcon = {
                    // 成功后不允许返回到结算页（购物车已被后端清空，回去只会看到空车）
                    if (orderResult == null) {
                        IconButton(onClick = onBack) {
                            Text(text = "←", fontSize = 24.sp)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (orderResult == null && state.items.isNotEmpty()) {
                SubmitBar(state = state, onSubmit = viewModel::submit)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                orderResult != null -> SuccessContent(
                    result = orderResult,
                    onBackHome = onBackHome,
                    onOpenOrder = { onOpenOrder(orderResult.orderId) }
                )

                state.isLoading -> CenterHint()

                state.errorMessage != null -> ErrorView(state.errorMessage!!) { viewModel.load() }

                else -> CheckoutContent(
                    state = state,
                    onOpenPicker = viewModel::openAddressPicker,
                    onOpenForm = viewModel::openAddressForm,
                    onRemarkChange = viewModel::updateRemark,
                    onPickPrescription = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
        }
    }

    if (state.showAddressPicker) {
        AddressPickerDialog(
            addresses = state.addresses,
            selectedId = state.selectedAddressId,
            onSelect = viewModel::selectAddress,
            onAdd = viewModel::openAddressForm,
            onDismiss = viewModel::closeAddressPicker
        )
    }

    if (state.showAddressForm) {
        AddressFormDialog(
            isSaving = state.isSavingAddress,
            error = state.addressError,
            onSave = viewModel::saveAddress,
            onDismiss = viewModel::closeAddressForm
        )
    }
}

@Composable
private fun CheckoutContent(
    state: CheckoutUiState,
    onOpenPicker: () -> Unit,
    onOpenForm: () -> Unit,
    onRemarkChange: (String) -> Unit,
    onPickPrescription: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AddressSection(
                address = state.selectedAddress,
                hasAddresses = state.addresses.isNotEmpty(),
                onOpenPicker = onOpenPicker,
                onOpenForm = onOpenForm
            )
        }
        item { GoodsSection(items = state.items) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("订单备注", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.remark,
                        onValueChange = onRemarkChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("选填，如：请尽快发货") },
                        maxLines = 3
                    )
                }
            }
        }
        if (state.hasPrescription) {
            item {
                PrescriptionSection(state = state, onPickImages = onPickPrescription)
            }
        }
        item { AmountSection(state) }
    }
}

@Composable
private fun AddressSection(
    address: Address?,
    hasAddresses: Boolean,
    onOpenPicker: () -> Unit,
    onOpenForm: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (address == null) {
                Text("收货地址", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "还没有收货地址，先添加一个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onOpenForm, enabled = !hasAddresses) { Text("添加收货地址") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = address.receiverName.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
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
                        Text(
                            text = address.fullAddress,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onOpenPicker) { Text("更换") }
                }
            }
        }
    }
}

@Composable
private fun GoodsSection(items: List<CartItem>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("商品清单", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.medicineName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "¥${item.price.money()} × ${item.quantity}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "¥${item.subtotal.money()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PrescriptionSection(state: CheckoutUiState, onPickImages: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("处方信息", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                when {
                    state.isUploadingPrescription -> Text(
                        text = "上传中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    state.prescriptionId != null -> Text(
                        text = "已上传",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )

                    else -> Text(
                        text = "未上传",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "购物车含处方药，需上传处方照片（最多 9 张）。提交订单后由药师审核，审核通过才会配药。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.prescriptionImageUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.prescriptionImageUris.forEach { uri ->
                        AsyncImage(
                            // 本地 content:// Uri，Coil 直接能读，不需要先转成 File
                            model = uri,
                            contentDescription = "处方照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onPickImages,
                enabled = !state.isUploadingPrescription
            ) {
                if (state.isUploadingPrescription) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在上传…")
                } else {
                    Text(if (state.prescriptionId != null) "重新上传处方" else "选择处方照片")
                }
            }

            if (state.prescriptionId == null && !state.isUploadingPrescription) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "未上传处方时无法提交订单",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AmountSection(state: CheckoutUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AmountRow("商品金额（${state.totalQuantity} 件）", "¥${state.totalAmount.money()}")
            AmountRow("运费", "¥0.00")
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                Text("实付", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "¥${state.totalAmount.money()}",
                    color = Color(0xFFD32F2F),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "金额以服务端计算为准，提交后返回实际应付。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun SubmitBar(state: CheckoutUiState, onSubmit: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "实付",
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
            Button(onClick = onSubmit, enabled = state.canSubmit) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("提交订单")
            }
        }
    }
}

@Composable
private fun AddressPickerDialog(
    addresses: List<Address>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择收货地址") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                addresses.forEach { address ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = address.addressId == selectedId,
                            onClick = { onSelect(address.addressId) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${address.receiverName.orEmpty()}  ${address.phone.orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = address.fullAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) { Text("新增地址") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AddressFormDialog(
    isSaving: Boolean,
    error: String?,
    onSave: (String, String, String, String, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf("") }
    var isDefault by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增收货地址") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("收货人") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        // 只留数字且最多 11 位，避免后面还要处理非法输入
                        phone = input.filter { it.isDigit() }.take(11)
                    },
                    label = { Text("手机号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = province,
                    onValueChange = { province = it },
                    label = { Text("省") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("市") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = district,
                    onValueChange = { district = it },
                    label = { Text("区/县") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("详细地址") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设为默认地址", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, phone, province, city, district, detail, isDefault) },
                enabled = !isSaving
            ) {
                Text(if (isSaving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") }
        }
    )
}

@Composable
private fun SuccessContent(
    result: CreateOrderResult,
    onBackHome: () -> Unit,
    onOpenOrder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFF2E7D32), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✓", color = Color.White, fontSize = 32.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "订单提交成功", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "订单号：${result.orderNo ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "状态：${ORDER_STATUS_LABELS.getOrElse(result.orderStatus) { "未知" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "实付 ¥${result.actualAmount.money()}（${result.items} 种商品）",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFD32F2F),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "接下来可以去订单详情完成支付。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBackHome) { Text("返回首页") }
            Button(onClick = onOpenOrder) { Text("查看订单并支付") }
        }
    }
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
        Text("正在准备订单…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
