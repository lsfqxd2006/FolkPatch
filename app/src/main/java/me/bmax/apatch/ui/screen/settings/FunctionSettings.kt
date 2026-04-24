package me.bmax.apatch.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ExpressiveCard
import me.bmax.apatch.ui.component.ExpressiveSwitch
import me.bmax.apatch.ui.component.SplicedColumnGroup
import me.bmax.apatch.ui.component.ToggleSettingCard
import me.bmax.apatch.util.setHideServiceEnabled
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun FunctionSettingsContent(
    kPatchReady: Boolean,
    aPatchReady: Boolean,
    isHideServiceEnabled: Boolean,
    onHideServiceChange: (Boolean) -> Unit,
    isKernelSpoofEnabled: Boolean,
    onKernelSpoofChange: (Boolean) -> Unit,
    kernelSpoofVersion: String,
    onKernelSpoofVersionChange: (String) -> Unit,
    kernelSpoofBuildTime: String,
    onKernelSpoofBuildTimeChange: (String) -> Unit,
    onKernelSpoofSave: () -> Unit,
    onKernelSpoofRestore: () -> Unit,
    snackBarHost: SnackbarHostState,
    isPathHideEnabled: Boolean,
    onPathHideChange: (Boolean) -> Unit,
    pathHidePaths: String,
    onPathHidePathsChange: (String) -> Unit,
    onPathHideSave: () -> Unit,
    isPathHideUidMode: Boolean,
    onPathHideUidModeChange: (Boolean) -> Unit,
    pathHideUids: String,
    onPathHideUidsChange: (String) -> Unit,
    onPathHideUidSave: () -> Unit,
    isUmountEnabled: Boolean,
    onUmountEnabledChange: (Boolean) -> Unit,
    umountPaths: String,
    onUmountPathsChange: (String) -> Unit,
    onUmountSave: () -> Unit,
    flat: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = APApplication.sharedPreferences

    val hideServiceTitle = stringResource(id = R.string.settings_hide_service)
    val hideServiceSummary = stringResource(id = R.string.settings_hide_service_summary)
    val umountServiceTitle = stringResource(id = R.string.settings_umount_service)
    val umountServiceSummary = stringResource(id = R.string.settings_umount_service_summary)

    SplicedColumnGroup(flat = flat) {
        item(visible = kPatchReady && aPatchReady) {
            ToggleSettingCard(
                flat = flat,
                icon = Icons.Filled.VisibilityOff,
                title = hideServiceTitle,
                description = hideServiceSummary,
                checked = isHideServiceEnabled,
                onCheckedChange = {
                    setHideServiceEnabled(it)
                    onHideServiceChange(it)
                }
            )
        }

        item(visible = kPatchReady && aPatchReady) {
            val umountPathsLabel = stringResource(id = R.string.umount_config_paths_label)
            val umountPathsPlaceholder = stringResource(id = R.string.umount_config_paths_placeholder)
            val umountPathsHelper = stringResource(id = R.string.umount_config_paths_helper)

            ExpressiveCard(flat = flat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = umountServiceTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = umountServiceSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ExpressiveSwitch(
                            checked = isUmountEnabled,
                            onCheckedChange = onUmountEnabledChange,
                        )
                    }

                    AnimatedVisibility(visible = isUmountEnabled) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = umountPaths,
                                onValueChange = onUmountPathsChange,
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                label = { Text(umountPathsLabel) },
                                placeholder = { Text(umountPathsPlaceholder) },
                                supportingText = { Text(umountPathsHelper) },
                                minLines = 4,
                                maxLines = Int.MAX_VALUE,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = onUmountSave,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.umount_config_save))
                            }
                        }
                    }
                }
            }
        }

        item(visible = kPatchReady && aPatchReady) {
            val kernelSpoofTitle = stringResource(id = R.string.settings_kernel_spoof)
            val kernelSpoofSummary = stringResource(id = R.string.settings_kernel_spoof_summary)
            val versionLabel = stringResource(id = R.string.settings_kernel_spoof_version)
            val buildTimeLabel = stringResource(id = R.string.settings_kernel_spoof_build_time)

            ExpressiveCard(flat = flat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = kernelSpoofTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = kernelSpoofSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ExpressiveSwitch(
                            checked = isKernelSpoofEnabled,
                            onCheckedChange = onKernelSpoofChange,
                        )
                    }

                    AnimatedVisibility(visible = isKernelSpoofEnabled) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = kernelSpoofVersion,
                                onValueChange = onKernelSpoofVersionChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(versionLabel) },
                                singleLine = true,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = kernelSpoofBuildTime,
                                onValueChange = onKernelSpoofBuildTimeChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(buildTimeLabel) },
                                singleLine = true,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = onKernelSpoofSave,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.save))
                                }
                                OutlinedButton(
                                    onClick = onKernelSpoofRestore,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.settings_kernel_spoof_restore))
                                }
                            }
                        }
                    }
                }
            }
        }

        item(visible = kPatchReady && aPatchReady) {
            val pathHideTitle = stringResource(id = R.string.settings_path_hide)
            val pathHideSummary = stringResource(id = R.string.settings_path_hide_summary)
            val pathsLabel = stringResource(id = R.string.path_hide_paths_label)
            val pathsPlaceholder = stringResource(id = R.string.path_hide_paths_placeholder)
            val pathsHelper = stringResource(id = R.string.path_hide_paths_helper)

            ExpressiveCard(flat = flat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HideSource,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = pathHideTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pathHideSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ExpressiveSwitch(
                            checked = isPathHideEnabled,
                            onCheckedChange = onPathHideChange,
                        )
                    }

                    AnimatedVisibility(visible = isPathHideEnabled) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = pathHidePaths,
                                onValueChange = onPathHidePathsChange,
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                label = { Text(pathsLabel) },
                                placeholder = { Text(pathsPlaceholder) },
                                supportingText = { Text(pathsHelper) },
                                minLines = 4,
                                maxLines = Int.MAX_VALUE,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = onPathHideSave,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.path_hide_save))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // UID Execution Mode
                            val uidModeTitle = stringResource(id = R.string.path_hide_uid_mode)
                            val uidModeSummary = stringResource(id = R.string.path_hide_uid_mode_summary)
                            val uidsLabel = stringResource(id = R.string.path_hide_uids_label)
                            val uidsHelper = stringResource(id = R.string.path_hide_uids_helper)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonPin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = uidModeTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = uidModeSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                ExpressiveSwitch(
                                    checked = isPathHideUidMode,
                                    onCheckedChange = onPathHideUidModeChange,
                                )
                            }

                            AnimatedVisibility(visible = isPathHideUidMode) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    OutlinedTextField(
                                        value = pathHideUids,
                                        onValueChange = onPathHideUidsChange,
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        label = { Text(uidsLabel) },
                                        placeholder = { Text("10134\n10087") },
                                        supportingText = { Text(uidsHelper) },
                                        minLines = 3,
                                        maxLines = Int.MAX_VALUE,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = onPathHideUidSave,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.path_hide_uid_save))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
