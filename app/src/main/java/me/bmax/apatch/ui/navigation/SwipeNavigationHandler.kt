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
import com.ramcosta.composedestinations.generated.NavGraphs
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.BottomBarDestination
import kotlin.math.abs
import kotlin.math.atan2

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
 *
 * 配置：
 * - 距离阈值：30dp（跟手 + 防误触）
 * - 角度阈值：30°（以水平为基准，过滤斜滑）
 * - 顺序切换，到边界停止
 */
@Composable
fun Modifier.swipeToSwitchTab(
    navController: NavHostController,
    onSwipeStart: () -> Unit,
    onSwipeComplete: () -> Unit
): Modifier {
    val destinations = rememberVisibleDestinations()
    val density = LocalDensity.current
    val distanceThreshold = with(density) { 30.dp.toPx() }
    val maxAngleDegrees = 30.0

    return this.pointerInput(destinations, navController, onSwipeStart, onSwipeComplete) {
        var totalX = 0f
        var totalY = 0f
        var triggered = false

        detectHorizontalDragGestures(
            onDragStart = {
                totalX = 0f
                totalY = 0f
                triggered = false
                onSwipeStart()
            },
            onHorizontalDrag = { change, dragAmount ->
                totalX += dragAmount
                totalY += change.positionChange().y
            },
            onDragEnd = {
                if (triggered) return@detectHorizontalDragGestures

                val route = navController.currentBackStackEntry?.destination?.route
                val current = destinations.indexOfFirst { it.direction.route == route }
                if (current == -1) return@detectHorizontalDragGestures

                // ★ 距离检查
                if (abs(totalX) < distanceThreshold) return@detectHorizontalDragGestures

                // ★ 角度检查（以水平为基准，水平 = 0°）
                val angleDegrees = Math.toDegrees(atan2(abs(totalY).toDouble(), abs(totalX).toDouble()))
                if (angleDegrees > maxAngleDegrees) return@detectHorizontalDragGestures

                // ★ 确定方向
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
                    onSwipeComplete()
                    triggered = true
                }
            }
        )
    }
}