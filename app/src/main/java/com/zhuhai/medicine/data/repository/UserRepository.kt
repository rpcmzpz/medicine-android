package com.zhuhai.medicine.data.repository

import com.zhuhai.medicine.data.model.Address
import com.zhuhai.medicine.data.model.AddressIdResult
import com.zhuhai.medicine.data.model.AddressRequest
import com.zhuhai.medicine.data.model.ListData
import com.zhuhai.medicine.data.model.UserProfile
import com.zhuhai.medicine.data.remote.ApiClient
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.remote.ApiService
import com.zhuhai.medicine.data.remote.apiCall

/**
 * 用户与收货地址数据源。全部接口都需要登录。
 */
class UserRepository(
    private val api: ApiService = ApiClient.api
) {

    suspend fun profile(): ApiResult<UserProfile> = apiCall { api.getProfile() }

    suspend fun addresses(): ApiResult<ListData<Address>> = apiCall { api.listAddresses() }

    suspend fun addAddress(request: AddressRequest): ApiResult<AddressIdResult> =
        apiCall { api.addAddress(request) }

    suspend fun setDefaultAddress(addressId: Long): ApiResult<AddressIdResult> =
        apiCall { api.setDefaultAddress(addressId) }

    suspend fun deleteAddress(addressId: Long): ApiResult<AddressIdResult> =
        apiCall { api.deleteAddress(addressId) }
}
