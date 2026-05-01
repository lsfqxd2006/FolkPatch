package me.bmax.apatch.ui.screen
import androidx.compose.material.icons.filled.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.Natives.FullProfile
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.SegmentedControl
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.PkgConfig
import me.bmax.apatch.util.SuAuditLog
import me.bmax.apatch.util.ui.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AppProfileScreen(
    navigator: DestinationsNavigator,
    packageName: String,
    uid: Int
) {
    val viewModel = viewModel<SuperUserViewModel>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val appInfoState = remember(packageName, uid) {
        derivedStateOf {
            SuperUserViewModel.apps.find { it.packageName == packageName && it.uid == uid }
        }
    }
    val appInfo = appInfoState.value
    if (appInfo == null) {
        navigator.popBackStack()
        return
    }

    val config = appInfo.config

    // Mode selection: 0=ROOT, 1=NO ROOT, 2=Exclude
    var selectedIndex by remember(config) {
        mutableIntStateOf(
            when {
                config.allow == 1 && config.profile.toUid == 2000 -> 1
                config.allow == 1 -> 0
                config.exclude == 1 -> 3
                else -> 2
            }
        )
    }

    // Full profile state (advanced settings)
    var showAdvanced by remember { mutableStateOf(false) }
    var selinuxDomain by remember { mutableStateOf(config.profile.scontext) }
    var namespaceMode by remember { mutableIntStateOf(0) }
    var rootUid by remember { mutableStateOf("0") }
    var rootGid by remember { mutableStateOf("0") }
    var capEff by remember { mutableStateOf("ffffffffffffffff") }

    // Load full profile once
    var fullProfileLoaded by remember { mutableStateOf(false) }
    if (!fullProfileLoaded) {
        scope.launch(Dispatchers.IO) {
            val fp = Natives.getFullProfile(packageName)
            if (fp != null) {
                selinuxDomain = fp.selinuxDomain
                namespaceMode = fp.namespace
                rootUid = fp.rootUid.toString()
                rootGid = fp.rootGid.toString()
                capEff = java.lang.Long.toHexString(fp.capEffective)
            }
        }
        fullProfileLoaded = true
    }

    fun applyCurrentMode(index: Int) {
        when (index) {
            0 -> { // ROOT
                config.allow = 1
                config.exclude = 0
                config.profile.scontext = selinuxDomain
                Natives.grantSu(appInfo.uid, 0, selinuxDomain)
                Natives.setUidExclude(appInfo.uid, 0)
                Natives.setSimpleProfileMode(packageName, uid, "allow", selinuxDomain)
                SuAuditLog.logGrant(appInfo.packageName, appInfo.uid)
            }
            1 -> { // NO ROOT
                config.allow = 0
                config.exclude = 0
                Natives.revokeSu(appInfo.uid)
                Natives.setUidExclude(appInfo.uid, 0)
                Natives.setSimpleProfileMode(packageName, uid, "deny")
                SuAuditLog.logRevoke(appInfo.packageName, appInfo.uid)
            }
            2 -> { // Exclude
                config.allow = 0
                config.exclude = 1
                config.profile.scontext = APApplication.DEFAULT_SCONTEXT
                Natives.revokeSu(appInfo.uid)
                Natives.setUidExclude(appInfo.uid, 1)
                Natives.setSimpleProfileMode(packageName, uid, "exclude")
                SuAuditLog.logExclude(appInfo.packageName, appInfo.uid)
            }
        }
        config.profile.uid = appInfo.uid
        PkgConfig.changeConfig(config)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.su_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val success = viewModel.launchApp(context, appInfo.packageName)
                        scope.launch {
                            showToast(
                                context,
                                if (success) {
                                    context.getString(R.string.su_app_action_launch_success, appInfo.label)
                                } else {
                                    context.getString(R.string.su_app_action_failed, appInfo.label)
                                }
                            )
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.su_app_action_launch))
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // App info header
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(appInfo.label) },
                supportingContent = {
                    Column {
                        Text(appInfo.packageName)
                        Text("UID: ${appInfo.uid}", color = MaterialTheme.colorScheme.outline)
                    }
                },
                leadingContent = {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(appInfo.packageInfo)
                            .crossfade(true).build(),
                        contentDescription = appInfo.label,
                        modifier = Modifier.size(48.dp)
                    )
                }
            )

            // Mode selector
            SegmentedControl(
                items = listOf("ROOT", "SHELL", "NO ROOT", "Exclude"),
                selectedIndex = selectedIndex,
                onItemSelection = { index ->
                    selectedIndex = index
                    applyCurrentMode(index)
                }
            )

            // Description cards
            AnimatedVisibility(visible = selectedIndex == 0) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(id = R.string.su_pkg_root_setting_title)) },
                    supportingContent = { Text(stringResource(id = R.string.su_pkg_root_setting_summary)) },
                    leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) }
                )
            }
            AnimatedVisibility(visible = selectedIndex == 1) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(id = R.string.su_pkg_shell_setting_title)) },
                    supportingContent = { Text(stringResource(id = R.string.su_pkg_shell_setting_summary)) },
                    leadingContent = { Icon(Icons.Filled.Build, contentDescription = null) }
                )
            }

            AnimatedVisibility(visible = selectedIndex == 2) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(id = R.string.su_pkg_normal_setting_title)) },
                    supportingContent = { Text(stringResource(id = R.string.su_pkg_normal_setting_summary)) },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) }
                )
            }
            AnimatedVisibility(visible = selectedIndex == 2) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(id = R.string.su_pkg_excluded_setting_title)) },
                    supportingContent = { Text(stringResource(id = R.string.su_pkg_excluded_setting_summary)) },
                    leadingContent = { Icon(Icons.Filled.RemoveCircle, contentDescription = null) }
                )
            }

            // ── Full Profile (MD3 expandable card) ──
            if (selectedIndex == 0) {
                Spacer(Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                var sctx by remember { mutableStateOf(config.profile.scontext) }

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("SELinux context") },
                    supportingContent = { Text(sctx) },
                    leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "toggle"
                            )
                        }
                    }
                )
                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = sctx,
                            onValueChange = { sctx = it },
                            label = { Text("SELinux domain") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    // 1. Save to apd profile JSON
                                    val ok = Natives.setProfile(packageName, uid, "allow", sctx)
                                    if (ok) {
                                        config.allow = 1
                                        config.exclude = 0
                                        config.profile.scontext = sctx
                                        PkgConfig.changeConfig(config)
                                    }
                                    withContext(Dispatchers.Main) {
                                        showToast(context, if (ok) "Saved" else "Failed")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) { Text("Apply") }
                    }
                }
            }
        }
    }
}

