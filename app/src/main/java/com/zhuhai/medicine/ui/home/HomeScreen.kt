package com.zhuhai.medicine.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhuhai.medicine.R
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.ui.medicine.MedicineListScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenCart: () -> Unit,
    onOpenOrders: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val sessionExpired by viewModel.sessionExpired.collectAsStateWithLifecycle()

    /*
     * ON_RESUME 而不是 LaunchedEffect(Unit)：
     * 从购物车 / 详情 / 订单返回首页时，Composable 一直活着、不会重新进入组合，
     * 只有生命周期会重新走到 RESUMED。在这里刷一次，角标才能反映刚才的加购或删除。
     */
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCartCount()
    }

    LaunchedEffect(sessionExpired) {
        if (sessionExpired) onLogout()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("药到家") },
                actions = {
                    TextButton(onClick = onOpenOrders) {
                        Text("订单")
                    }

                    /*
                     * BadgedBox 把角标叠在图标右上角；count 为 0 时传空 lambda，
                     * 角标整体不绘制（不是画一个「0」）。
                     */
                    IconButton(onClick = onOpenCart) {
                        BadgedBox(
                            badge = {
                                if (cartCount > 0) {
                                    Badge {
                                        Text(if (cartCount > 99) "99+" else "$cartCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cart),
                                contentDescription = "购物车，共 $cartCount 件"
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            TokenStore.clear()
                            onLogout()
                        }
                    ) {
                        Text("退出")
                    }
                }
            )
        }
    ) { innerPadding ->
        MedicineListScreen(
            onItemClick = onOpenDetail,
            // 药品列表内部也会遇到 401，统一交给首页的退出登录通道处理
            onSessionExpired = onLogout,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
