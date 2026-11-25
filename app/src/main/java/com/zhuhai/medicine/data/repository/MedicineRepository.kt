package com.zhuhai.medicine.data.repository

import com.zhuhai.medicine.data.model.CategoryNode
import com.zhuhai.medicine.data.model.ListData
import com.zhuhai.medicine.data.model.MedicineDetail
import com.zhuhai.medicine.data.model.MedicineSummary
import com.zhuhai.medicine.data.model.PageData
import com.zhuhai.medicine.data.remote.ApiClient
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.remote.ApiService
import com.zhuhai.medicine.data.remote.apiCall

/**
 * 药品数据源。药品接口在后端 JWT 白名单内，未登录也能调用。
 */
class MedicineRepository(
    private val api: ApiService = ApiClient.api
) : MedicineListSource {

    /**
     * @param sortBy price_asc | price_desc | sales_desc，其他值按创建时间倒序
     * @param drugType 0=OTC 1=处方药，null=不限
     * @param pageSize 后端只认 `page_size`，传 size/limit 会被静默忽略
     */
    override suspend fun list(page: Int, pageSize: Int): ApiResult<PageData<MedicineSummary>> =
        apiCall {
            api.listMedicines(
                keyword = null,
                categoryId = null,
                sortBy = null,
                page = page,
                pageSize = pageSize,
                drugType = null
            )
        }

    suspend fun list(
        keyword: String? = null,
        categoryId: Int? = null,
        sortBy: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
        drugType: Int? = null
    ): ApiResult<PageData<MedicineSummary>> = apiCall {
        api.listMedicines(
            keyword = keyword,
            categoryId = categoryId,
            sortBy = sortBy,
            page = page,
            pageSize = pageSize,
            drugType = drugType
        )
    }

    suspend fun categories(): ApiResult<ListData<CategoryNode>> =
        apiCall { api.listCategories() }

    suspend fun detail(medicineId: Long): ApiResult<MedicineDetail> =
        apiCall { api.medicineDetail(medicineId) }
}
