package com.zhuhai.medicine.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.data.model.Address
import com.zhuhai.medicine.data.model.AddressRequest
import com.zhuhai.medicine.data.model.CartItem
import com.zhuhai.medicine.data.model.CreateOrderRequest
import com.zhuhai.medicine.data.model.CreateOrderResult
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.CartRepository
import com.zhuhai.medicine.data.repository.OrderRepository
import com.zhuhai.medicine.data.repository.PrescriptionRepository
import com.zhuhai.medicine.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.math.BigDecimal

data class CheckoutUiState(
    val isLoading: Boolean = true,
    val items: List<CartItem> = emptyList(),
    val addresses: List<Address> = emptyList(),
    val selectedAddressId: Long? = null,
    val remark: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    /** 下单成功后非空，UI 切换到成功页 */
    val orderResult: CreateOrderResult? = null,
    val showAddressPicker: Boolean = false,
    val showAddressForm: Boolean = false,
    val isSavingAddress: Boolean = false,
    val addressError: String? = null,
    /** 本地相册 Uri（content://...），只用于上传前后给用户看缩略图 */
    val prescriptionImageUris: List<String> = emptyList(),
    /** 上传成功后拿到的处方 id，下单时必须带给后端 */
    val prescriptionId: Long? = null,
    val isUploadingPrescription: Boolean = false
) {
    val selectedAddress: Address?
        get() = addresses.firstOrNull { it.addressId == selectedAddressId }

    val totalAmount: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.price.multiply(BigDecimal.valueOf(item.quantity.toLong()))
        }

    val totalQuantity: Int get() = items.sumOf { it.quantity }

    /** 购物车里是否含处方药（drug_type=1） */
    val hasPrescription: Boolean get() = items.any { it.isPrescription }

    /**
     * 含处方药时**必须先上传处方**才能提交。
     * 后端 createOrder 只负责把 prescription_id 关联到订单上，不会校验「处方传了没」，
     * 所以这道门禁必须由客户端把住——否则会造出一张永远等不到审核的处方单。
     */
    val canSubmit: Boolean
        get() = items.isNotEmpty() &&
            selectedAddressId != null &&
            !isSubmitting &&
            !isUploadingPrescription &&
            (!hasPrescription || prescriptionId != null)
}

class CheckoutViewModel @JvmOverloads constructor(
    private val cartRepository: CartRepository = CartRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val orderRepository: OrderRepository = OrderRepository(),
    private val prescriptionRepository: PrescriptionRepository = PrescriptionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val cartResult = cartRepository.list()) {
                is ApiResult.Success -> _uiState.update { it.copy(items = cartResult.data.list) }

                is ApiResult.BusinessError -> {
                    fail(cartResult.message)
                    return@launch
                }

                is ApiResult.NetworkError -> {
                    fail(cartResult.message)
                    return@launch
                }

                ApiResult.Unauthorized -> {
                    unauthorized()
                    return@launch
                }
            }

            when (val addressResult = userRepository.addresses()) {
                is ApiResult.Success -> {
                    val list = addressResult.data.list
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            addresses = list,
                            // 已选过就保留，否则优先默认地址，再否则第一条
                            selectedAddressId = state.selectedAddressId
                                ?: list.firstOrNull { it.isDefault == 1 }?.addressId
                                ?: list.firstOrNull()?.addressId
                        )
                    }
                }

                is ApiResult.BusinessError -> fail(addressResult.message)
                is ApiResult.NetworkError -> fail(addressResult.message)
                ApiResult.Unauthorized -> unauthorized()
            }
        }
    }

    fun selectAddress(addressId: Long) {
        _uiState.update { it.copy(selectedAddressId = addressId, showAddressPicker = false) }
    }

    fun updateRemark(text: String) {
        _uiState.update { it.copy(remark = text) }
    }

    /**
     * 上传处方。
     *
     * `parts` 由 UI 层用 ContentResolver 转好再传进来 —— ViewModel 不持有 Context，
     * 这样它仍然可以在纯 JVM 单测里 new 出来。
     * 重新选图时旧 prescriptionId 会被覆盖：一张订单只会关联最后一次上传的处方。
     */
    fun uploadPrescription(parts: List<MultipartBody.Part>, previewUris: List<String>) {
        if (_uiState.value.isUploadingPrescription) return
        if (parts.isEmpty()) {
            showMessage("没有读取到有效图片，请重新选择")
            return
        }

        _uiState.update {
            it.copy(
                isUploadingPrescription = true,
                prescriptionImageUris = previewUris,
                // 重新上传期间先作废上一次的 id，避免用户拿旧处方下单
                prescriptionId = null
            )
        }

        viewModelScope.launch {
            when (val result = prescriptionRepository.upload(parts)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUploadingPrescription = false,
                            prescriptionId = result.data.prescriptionId
                        )
                    }
                    showMessage("处方已上传，提交后由药师审核")
                }

                is ApiResult.BusinessError -> {
                    _uiState.update { it.copy(isUploadingPrescription = false) }
                    showMessage(result.message)
                }

                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isUploadingPrescription = false) }
                    showMessage(result.message)
                }

                ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isUploadingPrescription = false) }
                    unauthorized()
                }
            }
        }
    }

    fun openAddressPicker() {
        _uiState.update { it.copy(showAddressPicker = true) }
    }

    fun closeAddressPicker() {
        _uiState.update { it.copy(showAddressPicker = false) }
    }

    fun openAddressForm() {
        _uiState.update {
            it.copy(showAddressPicker = false, showAddressForm = true, addressError = null)
        }
    }

    fun closeAddressForm() {
        _uiState.update { it.copy(showAddressForm = false, addressError = null) }
    }

    fun saveAddress(
        receiverName: String,
        phone: String,
        province: String,
        city: String,
        district: String,
        detail: String,
        isDefault: Boolean
    ) {
        val error = when {
            receiverName.isBlank() -> "请填写收货人"
            phone.length != 11 || phone.any { !it.isDigit() } -> "手机号应为 11 位数字"
            province.isBlank() -> "请填写省份"
            city.isBlank() -> "请填写城市"
            district.isBlank() -> "请填写区/县"
            detail.isBlank() -> "请填写详细地址"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(addressError = error) }
            return
        }
        if (_uiState.value.isSavingAddress) return

        _uiState.update { it.copy(isSavingAddress = true, addressError = null) }

        viewModelScope.launch {
            val request = AddressRequest(
                receiverName = receiverName.trim(),
                phone = phone.trim(),
                province = province.trim(),
                city = city.trim(),
                district = district.trim(),
                detail = detail.trim(),
                isDefault = isDefault
            )
            when (val result = userRepository.addAddress(request)) {
                is ApiResult.Success -> {
                    val newId = result.data.addressId
                    _uiState.update {
                        it.copy(
                            isSavingAddress = false,
                            showAddressForm = false,
                            selectedAddressId = newId
                        )
                    }
                    refreshAddresses()
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isSavingAddress = false, addressError = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isSavingAddress = false, addressError = result.message)
                }

                ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isSavingAddress = false) }
                    unauthorized()
                }
            }
        }
    }

    /** 新增成功后重新拉一次，保证「默认地址」等后端计算字段是最新的 */
    private suspend fun refreshAddresses() {
        when (val result = userRepository.addresses()) {
            is ApiResult.Success -> {
                val list = result.data.list
                _uiState.update { state ->
                    state.copy(
                        addresses = list,
                        selectedAddressId = state.selectedAddressId
                            ?: list.firstOrNull()?.addressId
                    )
                }
            }
            else -> Unit
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val addressId = state.selectedAddressId
        if (addressId == null) {
            showMessage("请先添加收货地址")
            return
        }
        if (state.items.isEmpty()) {
            showMessage("购物车为空，无法下单")
            return
        }
        if (state.hasPrescription && state.prescriptionId == null) {
            showMessage("请先上传处方照片")
            return
        }

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val request = CreateOrderRequest(
                cartItemIds = state.items.map { it.cartId },
                addressId = addressId,
                remark = state.remark.trim().takeIf { it.isNotEmpty() },
                // null 与空集合语义不同：null = 这单没有处方药，不必传
                prescriptionIds = listOfNotNull(state.prescriptionId).takeIf { it.isNotEmpty() }
            )
            when (val result = orderRepository.create(request)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSubmitting = false, orderResult = result.data)
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isSubmitting = false, message = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isSubmitting = false, message = result.message)
                }

                ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    unauthorized()
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun fail(text: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = text) }
    }

    private fun unauthorized() {
        TokenStore.clear()
        _uiState.update { it.copy(isLoading = false, isSubmitting = false, sessionExpired = true) }
    }

    private fun showMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }
}
