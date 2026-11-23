package com.zhuhai.medicine.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.ui.medicine.MedicineListScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenCart: () -> Unit,
    onOpenOrders: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("药品列表") },
                actions = {
                    TextButton(onClick = onOpenOrders) {
                        Text("订单")
                    }
                    TextButton(onClick = onOpenCart) {
                        Text("购物车")
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
            modifier = Modifier.padding(innerPadding)
        )
    }
}
