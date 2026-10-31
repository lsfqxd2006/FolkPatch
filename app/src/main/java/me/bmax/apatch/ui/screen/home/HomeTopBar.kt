package me.bmax.apatch.ui.screen.home

import me.bmax.apatch.ui.theme.tokens.FolkDimens
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import me.bmax.apatch.ui.theme.MusicConfig
import me.bmax.apatch.util.MusicManager
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RestartAlt
import me.bmax.apatch.ui.theme.BackgroundConfig
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import me.bmax.apatch.ui.component.folk.navOrigin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.WallpaperAwareDropdownMenu
import me.bmax.apatch.ui.component.WallpaperAwareDropdownMenuItem
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.util.reboot
import me.bmax.apatch.ui.component.folk.folkDefaultAppBarColors

// ===== 以下为高级重启弹窗(RebootDialog)新增 import，上游无 =====

// 动画
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

// 布局
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

// 形状 & 背景
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

// 弹窗 & Surface
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation

// 对齐
import androidx.compose.ui.Alignment

// 壁纸感知材质（非壁纸模式下这些 Local 为 null，自动回退到 M3 语义色）
import me.bmax.apatch.ui.theme.LocalWallpaperContentColor
import me.bmax.apatch.ui.theme.tokens.LocalFolkWallpaperTokens

// ===== 新增 import 结束 =====

data class RebootOption(
    @param:StringRes val titleRes: Int,
    val reason: String,
    val icon: ImageVector
)

@Composable
fun getRebootOptions(): List<RebootOption> = listOf(
    RebootOption(R.string.reboot, "", Icons.Outlined.Refresh),
    RebootOption(R.string.reboot_recovery, "recovery", Icons.Outlined.SystemUpdate),
    RebootOption(R.string.reboot_bootloader, "bootloader", Icons.Outlined.Memory),
    RebootOption(R.string.reboot_download, "download", Icons.Outlined.Download),
    RebootOption(R.string.reboot_edl, "edl", Icons.Outlined.DeveloperMode),
    RebootOption(R.string.reboot_fastbootd, "fastboot", Icons.Outlined.RestartAlt),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RebootDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onReboot: (String) -> Unit
) {
    // 不用 if (!show) return，让 BasicAlertDialog 的退出动画能完整播完
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(180)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(120)) +
               scaleOut(targetScale = 0.92f, animationSpec = tween(120))
    ) {
        val options = getRebootOptions()

        // 壁纸模式：用半透明 chrome/overlay 材质做弹窗底，让壁纸透出，
        // 文字用随壁纸明暗取反的中性色（LocalWallpaperContentColor）。
        // 非壁纸模式：tokens 与 contentColor 都为 null，回退到原 M3 语义色。
        val wallpaperTokens = LocalFolkWallpaperTokens.current
        val dialogSurface = wallpaperTokens?.let { it.chrome.fill }
            ?: MaterialTheme.colorScheme.surfaceContainerHigh
        val itemSurface = wallpaperTokens?.let { it.overlay.fill }
            ?: MaterialTheme.colorScheme.surfaceColorAtElevation(FolkDimens.Size1)
        val contentColor = LocalWallpaperContentColor.current
            ?: MaterialTheme.colorScheme.onSurface

        BasicAlertDialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(FolkDimens.Size28),
                color = dialogSurface
            ) {
                Column(modifier = Modifier.padding(FolkDimens.Size24)) {
                    Column(
                        modifier = Modifier.padding(top = FolkDimens.Size4),
                        verticalArrangement = Arrangement.spacedBy(FolkDimens.Size2)
                    ) {
                        options.forEach { option ->
                            Surface(
                                shape = RoundedCornerShape(FolkDimens.Size16),
                                color = itemSurface,
                                border = BorderStroke(
                                    FolkDimens.Size1,
                                    MaterialTheme.colorScheme.outlineVariant
                                ),
                                onClick = {
                                    onReboot(option.reason)
                                    onDismiss()
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = FolkDimens.Size16,
                                            vertical = FolkDimens.Size12
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(FolkDimens.Size40)
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(FolkDimens.Size20),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(FolkDimens.Size16))
                                    Text(
                                        text = stringResource(option.titleRes),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    onInstallClick: () -> Unit, navigator: DestinationsNavigator, kpState: APApplication.State
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var showDropdownMoreOptions by remember { mutableStateOf(false) }
    val prefs = APApplication.sharedPreferences
    val darkThemeFollowSys = prefs.getBoolean("night_mode_follow_sys", false)
    val nightModeEnabled = prefs.getBoolean("night_mode_enabled", true)
    val isDarkTheme = if (darkThemeFollowSys) {
        isSystemInDarkTheme()
    } else {
        nightModeEnabled
    }

    val currentTitle = prefs.getString("app_title", "folkpatch") ?: "folkpatch"
    val customAppTitle = prefs.getString("custom_app_title", "FolkPatch") ?: "FolkPatch"
    val isCustomTitle = currentTitle == "custom"
    val titleResId = when (currentTitle) {
        "custom" -> null
        "fpatch" -> R.string.app_title_fpatch
        "apatch_folk" -> R.string.app_title_apatch_folk
        "apatchx" -> R.string.app_title_apatchx
        "apatch" -> R.string.app_title_apatch
        "kernelpatch" -> R.string.app_title_kernelpatch
        "kernelsu" -> R.string.app_title_kernelsu
        "supersu" -> R.string.app_title_supersu
        "folksu" -> R.string.app_title_fpatch
        "superuser" -> R.string.app_title_superuser
        "superpatch" -> R.string.app_title_superpatch
        "magicpatch" -> R.string.app_title_magicpatch
        else -> R.string.app_title_folkpatch
    }

    val useAdvancedTitleStyle = BackgroundConfig.isAdvancedTitleStyleEnabled &&
                                !BackgroundConfig.titleImageUri.isNullOrEmpty()
    val titleOpacity = if (useAdvancedTitleStyle) {
        BackgroundConfig.getEffectiveTitleImageOpacity(isDarkTheme)
    } else 1f
    val titleDim = if (useAdvancedTitleStyle) {
        BackgroundConfig.getEffectiveTitleImageDim(isDarkTheme)
    } else 0f
    val titleOffsetX = if (useAdvancedTitleStyle) {
        BackgroundConfig.titleImageOffsetX * 100f
    } else 0f

    TopAppBar(
        colors = folkDefaultAppBarColors(),
        title = {
        if (useAdvancedTitleStyle) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(BackgroundConfig.titleImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = titleResId?.let { stringResource(it) } ?: customAppTitle,
                modifier = Modifier
                    .height(FolkDimens.Size40)
                    .offset(x = titleOffsetX.dp)
                    .alpha(titleOpacity)
                    .graphicsLayer {
                        if (titleDim > 0f) {
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix().apply {
                                    setToScale(
                                        1f - titleDim,
                                        1f - titleDim,
                                        1f - titleDim,
                                        1f
                                    )
                                }
                            )
                        }
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            Text(if (isCustomTitle) customAppTitle else stringResource(titleResId!!))
        }
    }, actions = {
        IconButton(
            onClick = onInstallClick,
            modifier = Modifier.navOrigin(captureBitmap = true),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoFixHigh,
                contentDescription = stringResource(id = R.string.mode_select_page_title)
            )
        }

        if (kpState != APApplication.State.UNKNOWN_STATE) {
            // Download/EDL drop the device into flashing modes that look dead to
            // a normal user, so they get a confirmation step first.
            val downloadTitle = stringResource(id = R.string.reboot_download)
            val downloadConfirmText = stringResource(id = R.string.reboot_download_confirm)
            val edlTitle = stringResource(id = R.string.reboot_edl)
            val edlConfirmText = stringResource(id = R.string.reboot_edl_confirm)
            var pendingRebootReason by remember { mutableStateOf<String?>(null) }
            var showRebootDialog by remember { mutableStateOf(false) }
            val rebootConfirmDialog = rememberConfirmDialog(
                onConfirm = {
                    pendingRebootReason?.let { reboot(it) }
                    pendingRebootReason = null
                },
                onDismiss = {
                    pendingRebootReason = null
                }
            )

            IconButton(onClick = { showRebootDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = stringResource(id = R.string.reboot)
                )
            }

            RebootDialog(
                show = showRebootDialog,
                onDismiss = { showRebootDialog = false },
                onReboot = { reason ->
                    when (reason) {
                        "download" -> {
                            pendingRebootReason = "download"
                            rebootConfirmDialog.showConfirm(
                                title = downloadTitle, content = downloadConfirmText
                            )
                        }
                        "edl" -> {
                            pendingRebootReason = "edl"
                            rebootConfirmDialog.showConfirm(
                                title = edlTitle, content = edlConfirmText
                            )
                        }
                        else -> reboot(reason)
                    }
                }
            )
        }

        Box {
            IconButton(onClick = { showDropdownMoreOptions = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(id = R.string.settings)
                )
                WallpaperAwareDropdownMenu(
                    expanded = showDropdownMoreOptions,
                    onDismissRequest = { showDropdownMoreOptions = false }
                ) {
                    if (MusicConfig.isMusicEnabled) {
                        val isPlaying by MusicManager.isPlaying.collectAsStateWithLifecycle()
                        WallpaperAwareDropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isPlaying) R.string.home_more_menu_music_pause
                                        else R.string.home_more_menu_music_play
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showDropdownMoreOptions = false
                                MusicManager.toggle()
                            }
                        )
                    }
                    WallpaperAwareDropdownMenuItem(
                        text = { Text(stringResource(R.string.home_more_menu_feedback_or_suggestion)) },
                        onClick = {
                            showDropdownMoreOptions = false
                            uriHandler.openUri("https://github.com/LyraVoid/FolkPatch/issues/new/choose")
                        }
                    )
                    WallpaperAwareDropdownMenuItem(
                        text = { Text(stringResource(R.string.home_more_menu_about)) },
                        onClick = {
                            navigator.navigate(AboutScreenDestination)
                            showDropdownMoreOptions = false
                        }
                    )
                }
            }
        }
    })
}