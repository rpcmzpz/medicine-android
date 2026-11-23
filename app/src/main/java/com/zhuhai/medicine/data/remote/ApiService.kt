package com.zhuhai.medicine.data.remote

import com.zhuhai.medicine.data.model.*
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 购药系统后端接口声明（MVP 范围）。
 *
 * 设计约定：
 * 1. 全部用 `suspend`，Retrofit 2.11 原生支持协程，不写 Call<T> + enqueue。
 * 2. 返回统一是 [ApiResponse]<T>；HTTP 状态码几乎恒为 200，成败看 body 里的 code。
 * 3. 路径**不以 / 开头**，交给 Retrofit 按 baseUrl 拼接（baseUrl 必须以 / 结尾）。
 * 4. 查询参数名一律蛇形（后端 @RequestParam 写死），传 size 会被静默忽略。
 *
 * 未纳入 MVP 的三组接口（管理端 admin/、药师端 pharmacist/、配送端 delivery/），
 * 属于角色后台，客户端 App 不需要；咨询（consultations）与用药提醒（reminders）
 * 留到 MVP 之后再接。
 */
interface ApiService {

    // ==================== 认证 /api/v1/auth ====================

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<RegisterResponse>

    /** access_token 过期后用 refresh_token 换新的 */
    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiResponse<RefreshResponse>

    // ==================== 药品 /api/v1/medicines ====================

    /**
     * 药品列表。
     * @param sortBy price_asc | price_desc | sales_desc，其他值后端按创建时间倒序
     * @param drugType 0=OTC 1=处方药，null 表示不限
     */
    @GET("api/v1/medicines")
    suspend fun listMedicines(
        @Query("keyword") keyword: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("drug_type") drugType: Int? = null
    ): ApiResponse<PageData<MedicineSummary>>

    @GET("api/v1/medicines/categories")
    suspend fun listCategories(): ApiResponse<ListData<CategoryNode>>

    @GET("api/v1/medicines/{medicineId}")
    suspend fun medicineDetail(
        @Path("medicineId") medicineId: Long
    ): ApiResponse<MedicineDetail>

    // ==================== 购物车 /api/v1/cart ====================

    @POST("api/v1/cart/items")
    suspend fun addCartItem(@Body body: AddCartItemRequest): ApiResponse<Unit>

    @GET("api/v1/cart/items")
    suspend fun listCartItems(): ApiResponse<CartData>

    @PATCH("api/v1/cart/items/{cartId}")
    suspend fun updateCartQuantity(
        @Path("cartId") cartId: Long,
        @Body body: UpdateCartQuantityRequest
    ): ApiResponse<Unit>

    @DELETE("api/v1/cart/items/{cartId}")
    suspend fun removeCartItem(@Path("cartId") cartId: Long): ApiResponse<Unit>

    // ==================== 订单 /api/v1/orders ====================

    /** 入参只需勾选的购物车项 id 列表 + 地址 + 可选处方，后端自己算金额 */
    @POST("api/v1/orders")
    suspend fun createOrder(@Body body: CreateOrderRequest): ApiResponse<CreateOrderResult>

    @GET("api/v1/orders")
    suspend fun listOrders(
        @Query("status") status: Int? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): ApiResponse<PageData<OrderSummary>>

    @GET("api/v1/orders/{orderId}")
    suspend fun orderDetail(@Path("orderId") orderId: Long): ApiResponse<OrderDetail>

    @PATCH("api/v1/orders/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: Long): ApiResponse<Unit>

    /** 支付。返回支付流水号与新订单状态，data 是对象 → 用 apiCall */
    @POST("api/v1/orders/{orderId}/pay")
    suspend fun payOrder(
        @Path("orderId") orderId: Long,
        @Body body: PayOrderRequest
    ): ApiResponse<PayResult>

    @PATCH("api/v1/orders/{orderId}/confirm")
    suspend fun confirmOrder(@Path("orderId") orderId: Long): ApiResponse<Unit>

    // ==================== 用户 /api/v1/user ====================

    @GET("api/v1/user/profile")
    suspend fun getProfile(): ApiResponse<UserProfile>

    @PUT("api/v1/user/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiResponse<UserProfile>

    @GET("api/v1/user/addresses")
    suspend fun listAddresses(): ApiResponse<ListData<Address>>

    @POST("api/v1/user/addresses")
    suspend fun addAddress(@Body body: AddressRequest): ApiResponse<AddressIdResult>

    @PATCH("api/v1/user/addresses/{addressId}/default")
    suspend fun setDefaultAddress(
        @Path("addressId") addressId: Long
    ): ApiResponse<AddressIdResult>

    @DELETE("api/v1/user/addresses/{addressId}")
    suspend fun deleteAddress(@Path("addressId") addressId: Long): ApiResponse<AddressIdResult>

    // ==================== 处方 /api/v1/prescriptions ====================

    /** 处方药下单前上传处方图片；字段名固定为 files（后端 @RequestParam("files")） */
    @Multipart
    @POST("api/v1/prescriptions/upload")
    suspend fun uploadPrescriptions(
        @Part files: List<MultipartBody.Part>
    ): ApiResponse<PrescriptionUploadResult>

    @GET("api/v1/prescriptions/{prescriptionId}")
    suspend fun prescriptionStatus(
        @Path("prescriptionId") prescriptionId: Long
    ): ApiResponse<PrescriptionStatus>
}
