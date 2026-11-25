package com.zhuhai.medicine.data.repository

import com.zhuhai.medicine.data.model.MedicineSummary
import com.zhuhai.medicine.data.model.PageData
import com.zhuhai.medicine.data.remote.ApiResult

/**
 * 药品列表数据源的抽象。
 *
 * 单独抽一层接口只有一个目的：**让分页逻辑能在 JVM 单元测试里跑**。
 * [MedicineRepository] 直接依赖 Retrofit 的 ApiService（真发 HTTP），
 * 而 [com.zhuhai.medicine.ui.medicine.MedicineListViewModel] 关心的只是
 * 「给我第 N 页」这一个动作——具体数据从哪来、怎么分页，与它无关。
 *
 * 分页是列表页最容易写错的地方（页码算错、hasMore 判错、重复请求同一页），
 * 这几条分支靠手点很难覆盖，用单测钉死比靠手点可靠。
 */
interface MedicineListSource {

    /**
     * 取第 [page] 页，每页 [pageSize] 条。
     *
     * @param page 从 1 开始
     */
    suspend fun list(page: Int, pageSize: Int): ApiResult<PageData<MedicineSummary>>
}
