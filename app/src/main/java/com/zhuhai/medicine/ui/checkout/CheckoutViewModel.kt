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
import com.zhuhai.medicine.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val addressError: String? = null
) {
    val selectedAddress: Address?
        get() = addresses.firstOrNull { it.addressId == selectedAddressId }

    val totalAmount: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.price.multiply(BigDecimal.valueOf(item.quantity.toLong()))
        }

    val totalQuantity: Int get() = items.sumOf { it.quantity }

    /** 处方药依赖 b13 的处方上传，先明确挡住，不做"先下单再说" */
    val hasPrescription: Boolean get() = items.any { it.isPrescription }

    val canSubmit: Boolean
        get() = items.isNotEmpty() && selectedAddressId != null && !isSubmitting && !hasPrescription
}

class CheckoutViewModel @JvmOverloads constructor(
    private val cartRepository: CartRepository = CartRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val orderRepository: OrderRepository = OrderRepository()
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
        if (state.hasPrescription) {
            showMessage("含处方药，处方上传在下一步支持")
            return
        }

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val request = CreateOrderRequest(
                cartItemIds = state.items.map { it.cartId },
                addressId = addressId,
                remark = state.remark.trim().takeIf { it.isNotEmpty() }
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
