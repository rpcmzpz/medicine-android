package com.zhuhai.medicine.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhuhai.medicine.data.local.TokenStore
import com.zhuhai.medicine.ui.cart.CartScreen
import com.zhuhai.medicine.ui.checkout.CheckoutScreen
import com.zhuhai.medicine.ui.detail.MedicineDetailScreen
import com.zhuhai.medicine.ui.home.HomeScreen
import com.zhuhai.medicine.ui.login.LoginScreen
import com.zhuhai.medicine.ui.order.OrderDetailScreen
import com.zhuhai.medicine.ui.order.OrderListScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CART = "cart"
    const val CHECKOUT = "checkout"
    const val ORDERS = "orders"

    /** 声明式路由：花括号里的名字必须与 navArgument 的 key 一致 */
    const val MEDICINE_DETAIL = "medicine/{medicineId}"
    const val ORDER_DETAIL = "order/{orderId}"

    /** 跳转时用这个函数拼真实路径，避免手写字符串拼错 */
    fun medicineDetail(medicineId: Long) = "medicine/$medicineId"

    fun orderDetail(orderId: Long) = "order/$orderId"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // 进程启动时按本地是否有 token 决定入口页面
    val startDestination = remember {
        if (TokenStore.isLoggedIn) Routes.HOME else Routes.LOGIN
    }

    /** token 失效：清空整个返回栈回登录页，避免用户按返回又回到需要登录的页面 */
    val backToLogin: () -> Unit = {
        navController.navigate(Routes.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        // 登录页出栈，避免按返回键回到登录页
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenDetail = { medicineId ->
                    navController.navigate(Routes.medicineDetail(medicineId))
                },
                onOpenCart = { navController.navigate(Routes.CART) },
                onOpenOrders = { navController.navigate(Routes.ORDERS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ORDERS) {
            OrderListScreen(
                onBack = { navController.popBackStack() },
                onOpenOrder = { orderId ->
                    navController.navigate(Routes.orderDetail(orderId))
                },
                onSessionExpired = backToLogin
            )
        }

        composable(
            route = Routes.ORDER_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) { entry ->
            val orderId = entry.arguments?.getLong("orderId") ?: 0L
            OrderDetailScreen(
                orderId = orderId,
                onBack = { navController.popBackStack() },
                onSessionExpired = backToLogin
            )
        }

        composable(Routes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onCheckout = { navController.navigate(Routes.CHECKOUT) },
                onSessionExpired = backToLogin
            )
        }

        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                onBack = { navController.popBackStack() },
                // 下单成功后购物车已被后端清空，直接回到首页而不是退回结算/购物车
                onBackHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onOpenOrder = { orderId ->
                    // 把结算页和购物车一起弹掉：详情页返回时应该回到首页，而不是「下单成功」页
                    navController.navigate(Routes.orderDetail(orderId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onSessionExpired = backToLogin
            )
        }

        composable(
            route = Routes.MEDICINE_DETAIL,
            arguments = listOf(navArgument("medicineId") { type = NavType.LongType })
        ) { entry ->
            // 参数是 Long，用 getLong 取；取不到说明路由定义与跳转路径不一致
            val medicineId = entry.arguments?.getLong("medicineId") ?: 0L
            MedicineDetailScreen(
                medicineId = medicineId,
                onBack = { navController.popBackStack() },
                onSessionExpired = backToLogin
            )
        }
    }
}
