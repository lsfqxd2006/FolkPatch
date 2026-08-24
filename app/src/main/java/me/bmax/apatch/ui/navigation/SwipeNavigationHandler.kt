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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.generated.NavGraphs
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.BottomBarDestination
import kotlin.math.abs
import kotlin.math.atan2

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
        var lastPosition: Offset? = null
        var triggered = false

        detectHorizontalDragGestures(
            onDragStart = { change ->
                totalX = 0f
                totalY = 0f
                lastPosition = change.position
                triggered = false
                onSwipeStart()
            },
            onHorizontalDrag = { change, dragAmount ->
                totalX += dragAmount
                // ★ 通过 position 差值计算垂直分量
                val currentY = change.position.y
                lastPosition?.let { last ->
                    totalY += currentY - last.y
                }
                lastPosition = change.position
            },
            onDragEnd = {
                if (triggered) return@detectHorizontalDragGestures

                val route = navController.currentBackStackEntry?.destination?.route
                val current = destinations.indexOfFirst { it.direction.route == route }
                if (current == -1) return@detectHorizontalDragGestures

                if (abs(totalX) < distanceThreshold) return@detectHorizontalDragGestures

                val angleDegrees = Math.toDegrees(atan2(abs(totalY).toDouble(), abs(totalX).toDouble()))
                if (angleDegrees > maxAngleDegrees) return@detectHorizontalDragGestures

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