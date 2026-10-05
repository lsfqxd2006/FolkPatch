package me.bmax.apatch.ui.screen.settings.appearance

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.folk.FolkPreference
import me.bmax.apatch.ui.component.folk.FolkSettingsSectionGroup
import me.bmax.apatch.ui.component.folk.FolkValuePreference
import me.bmax.apatch.ui.theme.FontConfig
import me.bmax.apatch.ui.theme.FontMode
import me.bmax.apatch.ui.theme.refreshTheme
import me.bmax.apatch.util.ui.showToast

@Composable
fun AppearanceFontSection(
    flat: Boolean,
    highlightKey: String?,
    fontMode: FontMode,
    onFontModeChange: (FontMode) -> Unit,
    // OpenDocument takes an array of MIME types, so the launcher type is
    // Array<String> rather than String. The caller in AppearanceSettingsContent
    // must create it with ActivityResultContracts.OpenDocument() to match.
    pickFontLauncher: ActivityResultLauncher<Array<String>>,
    snackBarHost: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    FolkSettingsSectionGroup(title = stringResource(R.string.settings_appearance_font), flat = flat, highlightKey = highlightKey) {
        item(key = "appearance_font_mode_system") {
            FontModePreference(
                title = stringResource(R.string.settings_font_mode_system),
                selected = fontMode == FontMode.SYSTEM_DEFAULT,
                onSelect = { onFontModeChange(FontMode.SYSTEM_DEFAULT) },
            )
        }

        item(key = "appearance_font_mode_custom") {
            FontModePreference(
                title = stringResource(R.string.settings_font_mode_custom),
                selected = fontMode == FontMode.CUSTOM,
                onSelect = { onFontModeChange(FontMode.CUSTOM) },
            )
        }

        if (fontMode == FontMode.CUSTOM) {
            item(key = "appearance_select_font") {
                FolkValuePreference(
                    icon = Icons.Outlined.FontDownload,
                    title = stringResource(id = R.string.settings_select_font_file),
                    onClick = {
                        try {
                            // "*/*" is intentionally broad because many ROM file
                            // managers do not register font-specific MIME types;
                            // a narrower filter would show an empty list.
                            pickFontLauncher.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            // Some devices can still fail (no file picker, restricted
                            // profile). Catch broadly so the app shows feedback instead
                            // of silently doing nothing or crashing.
                            showToast(
                                context,
                                e.message ?: context.getString(R.string.settings_custom_font_error)
                            )
                        }
                    },
                )
            }

            if (FontConfig.customFontFilename != null) {
                item(key = "appearance_clear_font") {
                    val clearFontDialog = rememberConfirmDialog(
                        onConfirm = {
                            FontConfig.clearFont(context)
                            refreshTheme.value = true
                            scope.launch {
                                snackBarHost.showSnackbar(message = context.getString(R.string.settings_font_cleared))
                            }
                        }
                    )
                    val clearFontTitle = stringResource(id = R.string.settings_clear_font)
                    val clearFontConfirm = context.getString(R.string.settings_clear_font_confirm)
                    FolkValuePreference(
                        icon = Icons.Outlined.Delete,
                        title = clearFontTitle,
                        onClick = {
                            clearFontDialog.showConfirm(
                                title = clearFontTitle,
                                content = clearFontConfirm,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** A radio-style settings row for picking one of the two font modes. */
@Composable
private fun FontModePreference(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    FolkPreference(
        title = title,
        selected = selected,
        onClick = onSelect,
        trailing = {
            RadioButton(selected = selected, onClick = null)
        },
    )
}
