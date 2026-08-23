package me.bmax.apatch.ui.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.navigate
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.navigation.navigate
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.BottomBarDestination

/**
 * 计算当前可见的主页面列表（与底部导航栏显示完全一致）
 * 自动响应：KP/AP状态变化、用户隐藏设置变化
 */
@Composable
internal fun rememberVisibleDestinations(): List<BottomBarDestination> {
    val kpState by APApplication.kpStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    val prefs = APApplication.sharedPreferences
    var showNavApm by remember { mutableStateOf(prefs.getBoolean("show_nav_apm", true)) }
    var showNavKpm by remember { mutableStateOf(prefs.getBoolean("show_nav_kpm", true)) }
    var showNavSuperUser by remember { mutableStateOf(prefs.getBoolean("show_nav_superuser", true)) }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "show_nav_apm" -> showNavApm = prefs.getBoolean(key, true)
                "show_nav_kpm" -> showNavKpm = prefs.getBoolean(key, true)
                "show_nav_superuser" -> showNavSuperUser = prefs.getBoolean(key, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return remember(kpState, apState, showNavApm, showNavKpm, showNavSuperUser) {
        BottomBarDestination.entries.filter { destination ->
            when {
                destination == BottomBarDestination.AModule && !showNavApm -> false
                destination == BottomBarDestination.KModule && !showNavKpm -> false
                destination == BottomBarDestination.SuperUser && !showNavSuperUser -> false
                destination.kPatchRequired && kpState == APApplication.State.UNKNOWN_STATE -> false
                destination.aPatchRequired && apState != APApplication.State.ANDROIDPATCH_INSTALLED -> false
                else -> true
            }
        }
    }
}

/**
 * 左右滑动切换主页面手势
 *
 * @param navController 导航控制器
 * @param onSwipeStart 滑动开始回调（用于显示悬浮导航栏）
 * @param onSwipeComplete 切换完成回调（用于重置自动隐藏计时器）
 */
@Composable
fun Modifier.swipeToSwitchTab(
    navController: NavHostController,
    onSwipeStart: () -> Unit,
    onSwipeComplete: () -> Unit
): Modifier {
    val destinations = rememberVisibleDestinations()
    val thresholdPx = with(LocalDensity.current) { 55.dp.toPx() }

    return this.pointerInput(destinations, navController, onSwipeStart, onSwipeComplete, thresholdPx) {
        var totalX = 0f
        var triggered = false

        detectHorizontalDragGestures(
            onDragStart = {
                totalX = 0f
                triggered = false
                onSwipeStart()
            },
            onHorizontalDrag = { _, dragAmount ->
                totalX += dragAmount.x
            },
            onDragEnd = {
                if (triggered) return@detectHorizontalDragGestures
                val route = navController.currentBackStackEntry?.destination?.route
                val current = destinations.indexOfFirst { it.direction.route == route }
                if (current != -1) {
                    val target = when {
                        totalX < -thresholdPx -> (current + 1).coerceAtMost(destinations.lastIndex)
                        totalX > thresholdPx -> (current - 1).coerceAtLeast(0)
                        else -> -1
                    }
                    if (target != -1 && target != current) {
                        navController.navigate(destinations[target].direction.route) {
                            popUpTo(NavGraphs.root.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        onSwipeComplete()
                        triggered = true
                    }
                }
            }
        )
    }
}