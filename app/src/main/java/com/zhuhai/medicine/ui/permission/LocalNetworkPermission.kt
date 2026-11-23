package com.zhuhai.medicine.ui.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android 16（API 36）起，应用访问私有网段（10.x / 192.168.x / 172.16-31.x）需要
 * ACCESS_LOCAL_NETWORK 权限。未授予时 TCP 连接会被系统静默丢弃，
 * 客户端只能看到 SocketTimeoutException，非常难排查。
 *
 * 用字符串常量而不是 Manifest.permission.ACCESS_LOCAL_NETWORK，
 * 是为了在低版本 SDK 的编译环境下也不会缺失符号。
 */
const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** 该限制从 Android 16 / API 36 开始生效 */
private const val MIN_SDK_LOCAL_NETWORK = 36

fun isLocalNetworkPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < MIN_SDK_LOCAL_NETWORK) return true
    return ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED
}

/**
 * 首次进入时自动申请一次本地网络权限，返回当前是否已授予。
 * 被用户拒绝后不会反复弹窗（系统也不允许），此时页面应引导去设置页开启。
 */
@Composable
fun rememberLocalNetworkPermission(): Boolean {
    if (Build.VERSION.SDK_INT < MIN_SDK_LOCAL_NETWORK) return true

    val context = LocalContext.current
    var granted by remember { mutableStateOf(isLocalNetworkPermissionGranted(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    LaunchedEffect(granted) {
        if (!granted) launcher.launch(LOCAL_NETWORK_PERMISSION)
    }

    return granted
}
