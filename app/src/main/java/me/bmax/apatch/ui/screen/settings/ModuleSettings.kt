package me.bmax.apatch.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ToggleSettingCard

@Composable
fun ModuleSettingsContent(
    aPatchReady: Boolean,
    flat: Boolean = false,
) {
    val prefs = APApplication.sharedPreferences

    val disableModuleUpdateCheckTitle = stringResource(id = R.string.settings_disable_module_update_check)
    val disableModuleUpdateCheckSummary = stringResource(id = R.string.settings_disable_module_update_check_summary)

    val moreInfoTitle = stringResource(id = R.string.settings_show_more_module_info)
    val moreInfoSummary = stringResource(id = R.string.settings_show_more_module_info_summary)

    val moduleSortOptimizationTitle = stringResource(id = R.string.settings_module_sort_optimization)
    val moduleSortOptimizationSummary = stringResource(id = R.string.settings_module_sort_optimization_summary)

    val foldSystemModuleTitle = stringResource(id = R.string.settings_fold_system_module)
    val foldSystemModuleSummary = stringResource(id = R.string.settings_fold_system_module_summary)

    val apmBatchInstallFullProcessTitle = stringResource(id = R.string.apm_batch_install_full_process)
    val apmBatchInstallFullProcessSummary = stringResource(id = R.string.apm_batch_install_full_process_summary)

    val simpleListBottomBarTitle = stringResource(id = R.string.settings_simple_list_bottom_bar)
    val simpleListBottomBarSummary = stringResource(id = R.string.settings_simple_list_bottom_bar_summary)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var disableModuleUpdateCheck by remember { mutableStateOf(prefs.getBoolean("disable_module_update_check", false)) }
        ToggleSettingCard(
            flat = flat,
            title = disableModuleUpdateCheckTitle,
            description = disableModuleUpdateCheckSummary,
            checked = disableModuleUpdateCheck,
            onCheckedChange = {
                disableModuleUpdateCheck = it
                prefs.edit().putBoolean("disable_module_update_check", it).apply()
            }
        )

        var showMoreModuleInfo by remember { mutableStateOf(prefs.getBoolean("show_more_module_info", true)) }
        ToggleSettingCard(
            flat = flat,
            title = moreInfoTitle,
            description = moreInfoSummary,
            checked = showMoreModuleInfo,
            onCheckedChange = {
                showMoreModuleInfo = it
                prefs.edit().putBoolean("show_more_module_info", it).apply()
            }
        )

        var moduleSortOptimization by remember { mutableStateOf(prefs.getBoolean("module_sort_optimization", true)) }
        ToggleSettingCard(
            flat = flat,
            title = moduleSortOptimizationTitle,
            description = moduleSortOptimizationSummary,
            checked = moduleSortOptimization,
            onCheckedChange = {
                moduleSortOptimization = it
                prefs.edit().putBoolean("module_sort_optimization", it).apply()
            }
        )

        var foldSystemModule by remember { mutableStateOf(prefs.getBoolean("fold_system_module", true)) }
        ToggleSettingCard(
            flat = flat,
            title = foldSystemModuleTitle,
            description = foldSystemModuleSummary,
            checked = foldSystemModule,
            onCheckedChange = {
                foldSystemModule = it
                prefs.edit().putBoolean("fold_system_module", it).apply()
            }
        )

        var apmBatchInstallFullProcess by remember { mutableStateOf(prefs.getBoolean("apm_batch_install_full_process", false)) }
        ToggleSettingCard(
            flat = flat,
            title = apmBatchInstallFullProcessTitle,
            description = apmBatchInstallFullProcessSummary,
            checked = apmBatchInstallFullProcess,
            onCheckedChange = {
                apmBatchInstallFullProcess = it
                prefs.edit().putBoolean("apm_batch_install_full_process", it).apply()
            }
        )

        var simpleListBottomBar by remember { mutableStateOf(prefs.getBoolean("simple_list_bottom_bar", false)) }
        ToggleSettingCard(
            flat = flat,
            title = simpleListBottomBarTitle,
            description = simpleListBottomBarSummary,
            checked = simpleListBottomBar,
            onCheckedChange = {
                simpleListBottomBar = it
                prefs.edit().putBoolean("simple_list_bottom_bar", it).apply()
            }
        )
    }
}
