package me.bmax.apatch.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ApiMarketplaceScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ThemeStoreScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.folk.FolkSettingsScaffold
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.screen.settings.appearance.AppearanceBackgroundSection
import me.bmax.apatch.ui.screen.settings.appearance.AppearanceSettingsContentTop
import me.bmax.apatch.ui.screen.settings.appearance.AppearanceSettingsContentBottom
import me.bmax.apatch.ui.theme.BackgroundConfig
import me.bmax.apatch.util.ui.LocalSnackbarHost

@Destination<RootGraph>(wrappers = [me.bmax.apatch.ui.navigation.NavContainerTransformWrapper::class])
@Composable
fun AppearanceSettingsScreen(navigator: DestinationsNavigator, highlightKey: String? = null) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE

    val snackBarHost = LocalSnackbarHost.current
    val flat = BackgroundConfig.isCustomBackgroundEnabled || BackgroundConfig.settingsBackgroundUri != null
    val prefs = APApplication.sharedPreferences

    val themeModeKey = "theme_mode"
    var themeMode by remember { mutableStateOf(prefs.getString(themeModeKey, null)) }

    // 背景独立 Item 需要的参数
    val loadingDialog = rememberLoadingDialog()
    val showCustomBadgeTextDialog = remember { mutableStateOf(false) }
    val currentStyle = prefs.getString("home_layout_style", APApplication.HOME_LAYOUT_STYLE_DEFAULT)
    val statsTopLayout = prefs.getString("stats_top_layout", "list") ?: "list"
    val showGridCardSettings = currentStyle == "kernelsu" || (currentStyle == "stats" && statsTopLayout == "grid")
    val badgeTextModes = listOf(
        stringResource(R.string.settings_custom_badge_text_full_half),
        stringResource(R.string.settings_custom_badge_text_lkm),
        stringResource(R.string.settings_custom_badge_text_gki),
        stringResource(R.string.settings_custom_badge_text_n_gki),
        stringResource(R.string.settings_custom_badge_text_oki),
        stringResource(R.string.settings_custom_badge_text_built_in),
    )
    val currentBadgeTextMode = badgeTextModes.getOrElse(BackgroundConfig.customBadgeTextMode) { badgeTextModes[0] }

    FolkSettingsScaffold(
        title = stringResource(R.string.settings_category_appearance),
        onBack = { navigator.popBackStack() },
        snackbarHostState = snackBarHost,
    ) {
        // 1. 顶部：夜览 + 布局
        item(key = "appearance_top") {
            AppearanceSettingsContentTop(
                snackBarHost = snackBarHost,
                kPatchReady = kPatchReady,
                flat = flat,
                highlightKey = highlightKey,
                showCustomBadgeTextDialog = showCustomBadgeTextDialog,
            )
        }

        // 2. 背景（独立 item，不跳顶）
        item(key = "appearance_background") {
            AppearanceBackgroundSection(
                flat = flat,
                highlightKey = highlightKey,
                snackBarHost = snackBarHost,
                loadingDialog = loadingDialog,
                showGridCardSettings = showGridCardSettings,
                currentBadgeTextMode = currentBadgeTextMode,
                showCustomBadgeTextDialog = showCustomBadgeTextDialog,
            )
        }

        // 3. 底部：横幅 + 字体 + 主题
        item(key = "appearance_bottom") {
            AppearanceSettingsContentBottom(
                snackBarHost = snackBarHost,
                flat = flat,
                highlightKey = highlightKey,
                themeStoreMode = themeMode,
                onThemeStoreModeChanged = { newMode -> themeMode = newMode },
                onNavigateToThemeStore = { navigator.navigate(ThemeStoreScreenDestination) },
                onNavigateToApiMarketplace = { navigator.navigate(ApiMarketplaceScreenDestination) },
            )
        }
    }
}