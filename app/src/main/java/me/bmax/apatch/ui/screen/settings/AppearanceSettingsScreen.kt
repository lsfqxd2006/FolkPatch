package me.bmax.apatch.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ApiMarketplaceScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ThemeStoreScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.folk.FolkChromeEdge
import me.bmax.apatch.ui.component.folk.FolkWallpaperChromeZone
import me.bmax.apatch.ui.component.folk.TopBarBackButton
import me.bmax.apatch.ui.component.folk.folkTopAppBarColors
import me.bmax.apatch.ui.theme.BackgroundConfig
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.bmax.apatch.util.ui.NavigationBarsSpacer

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(wrappers = [me.bmax.apatch.ui.navigation.NavContainerTransformWrapper::class])
@Composable
fun AppearanceSettingsScreen(navigator: DestinationsNavigator, highlightKey: String? = null) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE

    val snackBarHost = LocalSnackbarHost.current
    val flat = BackgroundConfig.isCustomBackgroundEnabled || BackgroundConfig.settingsBackgroundUri != null
    val prefs = APApplication.sharedPreferences
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", null)) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FolkWallpaperChromeZone(edge = FolkChromeEdge.Top) {
                LargeFlexibleTopAppBar(
                    title = { Text(text = stringResource(R.string.settings_category_appearance), fontWeight = FontWeight.Bold) },
                    colors = folkTopAppBarColors(),
                    navigationIcon = {
                        TopBarBackButton(onClick = { navigator.popBackStack() }, contentDescription = stringResource(R.string.core_action_back))
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackBarHost) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState()) // 👈 核心：用普通 Column 滚动
        ) {
            AppearanceSettingsContent(
                snackBarHost = snackBarHost,
                kPatchReady = kPatchReady,
                onNavigateToThemeStore = { navigator.navigate(ThemeStoreScreenDestination) },
                onNavigateToApiMarketplace = { navigator.navigate(ApiMarketplaceScreenDestination) },
                flat = flat,
                highlightKey = highlightKey,
                themeStoreMode = themeMode,
                onThemeStoreModeChanged = { newMode -> themeMode = newMode },
            )
            NavigationBarsSpacer()
        }
    }
}