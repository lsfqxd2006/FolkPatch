package me.bmax.apatch.ui.screen.settings

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ExpressiveCard
import me.bmax.apatch.ui.component.SplicedColumnGroup
import me.bmax.apatch.ui.component.ToggleSettingCard
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.util.APatchKeyHelper

@Composable
fun SecuritySettingsContent(
    snackBarHost: SnackbarHostState,
    kPatchReady: Boolean,
    flat: Boolean = false,
) {
    val prefs = APApplication.sharedPreferences
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var biometricLogin by remember { mutableStateOf(prefs.getBoolean("biometric_login", false)) }

    val biometricManager = androidx.biometric.BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    SplicedColumnGroup(flat = flat) {
        item(visible = canAuthenticate) {
            ToggleSettingCard(
                flat = flat,
                icon = Icons.Filled.Fingerprint,
                title = stringResource(id = R.string.settings_biometric_login),
                description = stringResource(id = R.string.settings_biometric_login_summary),
                checked = biometricLogin,
                onCheckedChange = { checked ->
                    if (!checked) {
                        if (activity != null) {
                            val executor = ContextCompat.getMainExecutor(context)
                            val biometricPrompt = BiometricPrompt(activity, executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        biometricLogin = false
                                        prefs.edit().putBoolean("biometric_login", false).apply()
                                    }

                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        super.onAuthenticationError(errorCode, errString)
                                    }
                                })

                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle(context.getString(R.string.action_biometric))
                                .setSubtitle(context.getString(R.string.msg_biometric))
                                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build()

                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            biometricLogin = false
                            prefs.edit().putBoolean("biometric_login", false).apply()
                        }
                    } else {
                        biometricLogin = true
                        prefs.edit().putBoolean("biometric_login", true).apply()
                    }
                }
            )
        }

        item(visible = biometricLogin && canAuthenticate) {
            var strongBiometric by remember { mutableStateOf(prefs.getBoolean("strong_biometric", false)) }
            ToggleSettingCard(
                flat = flat,
                icon = Icons.Filled.Shield,
                title = stringResource(id = R.string.settings_strong_biometric),
                description = stringResource(id = R.string.settings_strong_biometric_summary),
                checked = strongBiometric,
                onCheckedChange = {
                    strongBiometric = it
                    prefs.edit().putBoolean("strong_biometric", it).apply()
                }
            )
        }

        item(visible = kPatchReady) {
            val clearSuperKeyTitle = stringResource(id = R.string.clear_super_key)
            val clearSuperKeyDialog = rememberConfirmDialog(
                onConfirm = {
                    APatchKeyHelper.clearConfigKey()
                    APApplication.setSuperKeyAndRefresh("")
                }
            )
            ExpressiveCard(
                flat = flat,
                onClick = {
                    clearSuperKeyDialog.showConfirm(
                        title = clearSuperKeyTitle,
                        content = context.getString(R.string.settings_clear_super_key_dialog)
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(text = clearSuperKeyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item(visible = kPatchReady) {
            var noStoreKey by remember { mutableStateOf(APatchKeyHelper.shouldSkipStoreSuperKey()) }
            ToggleSettingCard(
                flat = flat,
                icon = Icons.Filled.Key,
                title = stringResource(id = R.string.settings_donot_store_superkey),
                description = stringResource(id = R.string.settings_donot_store_superkey_summary),
                checked = noStoreKey,
                onCheckedChange = {
                    noStoreKey = it
                    APatchKeyHelper.setShouldSkipStoreSuperKey(it)
                }
            )
        }
    }
}
