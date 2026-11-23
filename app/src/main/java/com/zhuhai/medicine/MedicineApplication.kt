package com.zhuhai.medicine

import android.app.Application
import com.zhuhai.medicine.data.local.TokenStore

/**
 * 全局入口。只做进程级的初始化，不放业务逻辑。
 *
 * 必须在 AndroidManifest 的 <application android:name=".MedicineApplication"> 注册，
 * 否则系统不会调用 onCreate，TokenStore 会在首次使用时抛
 * UninitializedPropertyAccessException。
 */
class MedicineApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
    }
}
