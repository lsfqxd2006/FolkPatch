package me.bmax.apatch.ui.navigation

import androidx.compose.foundation.gestures.detectDragGestures
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
import com.ramcosta.composedestinations.generated.NavGraphs
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.BottomBarDestination
import kotlin.math.abs

/**
 * 计算当前可见的主页面列表（与底部导航栏显示完全一致）
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
 */
@Composable
fun Modifier.swipeToSwitchTab(
    navController: NavHostController,
    onSwipeStart: () -> Unit,
    onSwipeComplete: () -> Unit
): Modifier {
    val destinations = rememberVisibleDestinations()
    val density = LocalDensity.current
    val distanceThreshold = with(density) { 35.dp.toPx() }      // ★ 35dp（防误触优化）
    val velocityThreshold = with(density) { 400.dp.toPx() }     // ★ 400dp/s（原生标准）

    return this.pointerInput(destinations, navController, onSwipeStart, onSwipeComplete) {
        var totalX = 0f
        var triggered = false

        detectDragGestures(
            onDragStart = {
                totalX = 0f
                triggered = false
                onSwipeStart()
            },
            onDrag = { _, dragAmount ->
                totalX += dragAmount.x
            },
            onDragEnd = { velocity ->
                if (triggered) return@detectDragGestures

                val route = navController.currentBackStackEntry?.destination?.route
                val current = destinations.indexOfFirst { it.direction.route == route }
                if (current == -1) return@detectDragGestures

                // 触发条件：距离达标 或 速度达标
                val shouldTrigger = abs(totalX) >= distanceThreshold || abs(velocity.x) >= velocityThreshold
                if (!shouldTrigger) return@detectDragGestures

                // 顺序切换，到边界停止（不循环）
                val target = if (totalX < 0) {
                    if (current + 1 < destinations.size) current + 1 else current
                } else {
                    if (current - 1 >= 0) current - 1 else current
                }

                if (target != current) {
                    navController.navigate(destinations[target].direction.route) {
                        popUpTo(NavGraphs.root.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    // 切换完成 → 重置悬浮导航栏计时器
                    onSwipeComplete()
                    triggered = true
                }
            }
        )
    }
}