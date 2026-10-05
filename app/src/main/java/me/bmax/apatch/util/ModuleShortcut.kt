package me.bmax.apatch.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import me.bmax.apatch.util.ui.showToast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import me.bmax.apatch.R
import me.bmax.apatch.ui.MainActivity
import me.bmax.apatch.ui.WebUIActivity
import java.util.Locale

object ModuleShortcut {
    private const val TAG = "ModuleShortcut"

    /**
     * 标记：该快捷方式使用的是"随图标风格切换的兜底图标"，
     * 而非用户在添加时选的自定义图标。
     *
     * 用途：切换 App 图标风格时，只刷新带此标记的快捷方式；
     * 用户主动选过自定义图标的不动，尊重用户选择。
     */
    private const val EXTRA_FALLBACK_ICON = "shortcut_uses_fallback_icon"

    // =====================================================================
    // 公开的创建 / 查询 / 删除接口
    // =====================================================================

    fun createModuleWebUiShortcut(
        context: Context,
        moduleId: String,
        name: String,
        iconUri: String?
    ) {
        val shortcutId = "module_webui_$moduleId"
        val shortcutIntent = Intent(context, WebUIActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "apatch://webui/$moduleId".toUri()
            putExtra("id", moduleId)
            putExtra("name", name)
            putExtra("from_webui_shortcut", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        createModuleShortcut(
            context = context,
            moduleId = moduleId,
            name = name,
            iconUri = iconUri,
            shortcutId = shortcutId,
            shortcutIntent = shortcutIntent,
            logPrefix = "createModuleWebUiShortcut"
        )
    }

    fun hasModuleWebUiShortcut(context: Context, moduleId: String): Boolean {
        return hasPinnedShortcut(context, "module_webui_$moduleId")
    }

    fun deleteModuleWebUiShortcut(context: Context, moduleId: String) {
        deleteShortcut(context, "module_webui_$moduleId")
    }

    /**
     * 快捷方式固定的启动目标。
     * ⚠️ 必须是真正的 MainActivity，不能是 activity-alias：
     * alias 会在切换图标风格时被 setComponentEnabledSetting 禁用，
     * 而 pinned 快捷方式的 intent 一旦固定就冻结、无法修改，
     * 会直接导致点击失效。MainActivity 本身从不禁用，是唯一稳定的锚点。
     */
    private fun getLauncherComponent(context: Context): ComponentName {
        return ComponentName(context.packageName, MainActivity::class.java.name)
    }

    fun createModuleActionShortcut(
        context: Context,
        moduleId: String,
        name: String,
        iconUri: String?
    ) {
        val shortcutId = "module_action_$moduleId"
        val shortcutIntent = Intent().apply {
            component = getLauncherComponent(context)
            action = Intent.ACTION_VIEW
            putExtra("apm_action_module_id", moduleId)
            putExtra("from_action_shortcut", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        createModuleShortcut(
            context = context,
            moduleId = moduleId,
            name = name,
            iconUri = iconUri,
            shortcutId = shortcutId,
            shortcutIntent = shortcutIntent,
            logPrefix = "createModuleActionShortcut"
        )
    }

    fun hasModuleActionShortcut(context: Context, moduleId: String): Boolean {
        return hasPinnedShortcut(context, "module_action_$moduleId")
    }

    fun deleteModuleActionShortcut(context: Context, moduleId: String) {
        deleteShortcut(context, "module_action_$moduleId")
    }

    fun createScriptShortcut(
        context: Context,
        scriptId: String,
        name: String,
        iconUri: String?
    ) {
        val shortcutId = "script_$scriptId"
        val shortcutIntent = Intent().apply {
            component = getLauncherComponent(context)
            action = Intent.ACTION_VIEW
            putExtra("script_id", scriptId)
            putExtra("from_script_shortcut", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        createModuleShortcut(
            context = context,
            moduleId = scriptId,
            name = name,
            iconUri = iconUri,
            shortcutId = shortcutId,
            shortcutIntent = shortcutIntent,
            logPrefix = "createScriptShortcut"
        )
    }

    fun hasScriptShortcut(context: Context, scriptId: String): Boolean {
        return hasPinnedShortcut(context, "script_$scriptId")
    }

    fun deleteScriptShortcut(context: Context, scriptId: String) {
        deleteShortcut(context, "script_$scriptId")
    }

    // =====================================================================
    // 创建快捷方式的核心
    // =====================================================================

    private fun createModuleShortcut(
        context: Context,
        moduleId: String,
        name: String,
        iconUri: String?,
        shortcutId: String,
        shortcutIntent: Intent,
        logPrefix: String
    ) {
        val hasPinned = hasPinnedShortcut(context, shortcutId)
        Log.d(TAG, "$logPrefix: shortcutId=$shortcutId, hasPinned=$hasPinned")

        // 用户自定义图标 or 兜底图标
        val customIcon = createShortcutIcon(context, iconUri)
        val usedFallback = customIcon == null
        val finalIcon = customIcon ?: IconCompat.createWithResource(
            context,
            LauncherIconUtils.currentLauncherIconRes(context)
        )

        // 把"是否使用兜底图标"写进 intent，供切换风格时筛选
        val intentWithFlag = Intent(shortcutIntent).apply {
            putExtra(EXTRA_FALLBACK_ICON, usedFallback)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(name)
            .setIntent(intentWithFlag)
            .setIcon(finalIcon)
            .build()

        try {
            Log.d(TAG, "$logPrefix: pushDynamicShortcut() called for moduleId=$moduleId")
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (t: Throwable) {
            Log.w(TAG, "$logPrefix: pushDynamicShortcut() threw exception for moduleId=$moduleId: ${t.message}", t)
        }

        if (hasPinned) {
            Log.d(TAG, "$logPrefix: detected existing pinned shortcut, updating only")
            showToast(context, context.getString(R.string.module_shortcut_updated))
            return
        }

        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val initialState = getShortcutPermissionState(context)
        Log.d(TAG, "$logPrefix: initial permission state=$initialState")
        if (manufacturer.contains("xiaomi") && (initialState == ShortcutPermissionState.Denied || initialState == ShortcutPermissionState.Ask)) {
            Log.d(TAG, "$logPrefix: device is Xiaomi (MIUI/HyperOS), trying to grant via root shell")
            val rootSuccess = tryGrantMiuiShortcutPermissionByRoot(context)
            Log.d(TAG, "$logPrefix: root grant attempt success=$rootSuccess")
            val afterState = getShortcutPermissionState(context)
            Log.d(TAG, "$logPrefix: state after root attempt=$afterState")
            if (afterState != ShortcutPermissionState.Granted) {
                Log.d(TAG, "$logPrefix: still not Granted after root, showing hint")
                showShortcutPermissionHint(context)
                return
            }
        } else if (initialState == ShortcutPermissionState.Denied || initialState == ShortcutPermissionState.Ask) {
            Log.d(TAG, "$logPrefix: permission not granted (state=$initialState), showing hint first")
            showShortcutPermissionHint(context)
            return
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Log.w(TAG, "$logPrefix: requestPinShortcut not supported on this launcher")
            showToast(context, context.getString(R.string.module_shortcut_not_supported))
            return
        }

        val pinned = try {
            Log.d(TAG, "$logPrefix: requestPinShortcut() called for moduleId=$moduleId")
            val result = ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            Log.d(TAG, "$logPrefix: requestPinShortcut() result=$result")
            result
        } catch (t: Throwable) {
            Log.w(TAG, "$logPrefix: requestPinShortcut() threw exception for moduleId=$moduleId: ${t.message}", t)
            false
        }

        if (pinned) {
            Log.d(TAG, "$logPrefix: pinned shortcut created successfully for moduleId=$moduleId")
            showToast(context, context.getString(R.string.module_shortcut_created))
        } else {
            Log.w(TAG, "$logPrefix: pinned shortcut not created, showing permission hint for moduleId=$moduleId")
            showShortcutPermissionHint(context)
        }
    }

    // =====================================================================
    // 图标风格切换后的刷新逻辑
    // =====================================================================

    /** 权威判断：该快捷方式是否使用兜底图标（老快捷方式无标记 → 视为自定义，跳过）。 */
    private fun usesFallbackIcon(s: ShortcutInfoCompat): Boolean =
        s.intent?.getBooleanExtra(EXTRA_FALLBACK_ICON, false) == true

    /**
     * 统一入口：切换图标风格后调用。
     *
     * 按厂商分流：
     *   - MIUI / HyperOS 家族：桌面缓存 pinned 图标，updateShortcuts 刷不动，
     *     必须走 disable → repin 强制重建。
     *   - 其它厂商：桌面不缓存，走温和 updateShortcuts，避免 disable 带来的
     *     灰图标 / 弹框 / 位置漂移等副作用。
     */
    fun onLauncherIconStyleChanged(context: Context) {
        val mfr = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val isMiuiFamily = mfr.contains("xiaomi") ||
            mfr.contains("redmi") ||
            mfr.contains("poco")
        if (isMiuiFamily) {
            rebuildFallbackShortcutIcons(context)
        } else {
            updateFallbackShortcutIcons(context)
        }
    }

    /**
     * 温和路线：只调 updateShortcuts 换 icon / label，不 disable、不 repin。
     * 适用于原生 / Pixel / 三星 / OPPO 等不缓存 pinned 图标的桌面。
     *
     * 关键：intent 必须原样传回（component 不变），否则系统会认为你要改
     * pinned 的 intent，直接跳过本次更新 —— 连图标都不会换。
     */
    fun updateFallbackShortcutIcons(context: Context) {
        try {
            val newIcon = IconCompat.createWithResource(
                context,
                LauncherIconUtils.currentLauncherIconRes(context)
            )
            val updates = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_PINNED or ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
            ).distinctBy { it.id }
                .filter { usesFallbackIcon(it) }
                .map { s ->
                    ShortcutInfoCompat.Builder(context, s.id)
                        .setShortLabel(s.shortLabel?.toString() ?: "")
                        .setLongLabel(s.longLabel?.toString() ?: "")
                        .setIntent(s.intent!!)   // 原样保留
                        .setIcon(newIcon)
                        .build()
                }

            if (updates.isEmpty()) {
                Log.d(TAG, "updateFallbackShortcutIcons: nothing to update")
                return
            }
            ShortcutManagerCompat.updateShortcuts(context, updates)
            Log.d(TAG, "updateFallbackShortcutIcons: updated ${updates.size}")
        } catch (t: Throwable) {
            Log.w(TAG, "updateFallbackShortcutIcons failed: ${t.message}", t)
        }
    }

    /**
     * 强制路线（MIUI / HyperOS）：
     * 桌面缓存 pinned 图标，updateShortcuts 刷不动，必须：
     *   disable → removeDynamic → pushDynamic → requestPin。
     * 只处理带 fallback 标记的快捷方式，用户自定义图标不动。
     * 任一步失败 → enableShortcuts + 重新 push 原 shortcut 回滚，避免留灰图标。
     */
    fun rebuildFallbackShortcutIcons(context: Context) {
        val shortcuts = try {
            ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_PINNED or ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
            ).distinctBy { it.id }
        } catch (t: Throwable) {
            Log.w(TAG, "rebuild: getShortcuts failed", t)
            return
        }

        val newIcon = IconCompat.createWithResource(
            context,
            LauncherIconUtils.currentLauncherIconRes(context)
        )

        shortcuts.filter { usesFallbackIcon(it) }.forEach { s ->
            val id = s.id
            val rebuilt = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(s.shortLabel?.toString() ?: "")
                .setLongLabel(s.longLabel?.toString() ?: "")
                .setIntent(s.intent!!)          // 关键：intent 原样保留
                .setIcon(newIcon)
                .build()

            var disabled = false
            try {
                // 1) 禁用：MIUI 的 Launcher 会丢弃该图标缓存
                ShortcutManagerCompat.disableShortcuts(context, listOf(id), null)
                disabled = true

                // 2) 清掉动态副本，避免后续 push/update 冲突
                ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(id))

                // 3) 用新图标重新注册动态快捷方式（长按 App 图标可见）
                ShortcutManagerCompat.pushDynamicShortcut(context, rebuilt)

                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    // 4) 同 id 已 pinned → 不弹框，只把最新 icon 写回桌面
                    val ok = ShortcutManagerCompat.requestPinShortcut(context, rebuilt, null)
                    if (!ok) throw IllegalStateException("requestPinShortcut returned false for $id")
                } else {
                    // 4') 桌面不支持 pin：退回温和更新
                    ShortcutManagerCompat.updateShortcuts(context, listOf(rebuilt))
                }

                Log.d(TAG, "rebuild: id=$id ok")
            } catch (t: Throwable) {
                Log.w(TAG, "rebuild: id=$id failed, rolling back", t)
                if (disabled) {
                    runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, s) }
                }
            }
        }
    }

    // =====================================================================
    // 以下为内部工具方法，逻辑保持原有不变
    // =====================================================================

    private fun createShortcutIcon(context: Context, iconUri: String?): IconCompat? {
        val bitmap = loadShortcutBitmap(context, iconUri) ?: return null
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun hasPinnedShortcut(context: Context, id: String): Boolean {
        return try {
            val shortcuts = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_PINNED
            )
            val exists = shortcuts.any { it.id == id && it.isEnabled }
            Log.d(TAG, "hasPinnedShortcut: id=$id, exists=$exists")
            exists
        } catch (t: Throwable) {
            Log.w(TAG, "hasPinnedShortcut: exception for id=$id: ${t.message}", t)
            false
        }
    }

    private fun deleteShortcut(context: Context, id: String) {
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(id))
            Log.d(TAG, "deleteShortcut: removed dynamic shortcut id=$id")
        } catch (t: Throwable) {
            Log.w(TAG, "deleteShortcut: removeDynamicShortcuts exception for id=$id: ${t.message}", t)
        }
        try {
            ShortcutManagerCompat.disableShortcuts(context, listOf(id), "")
            Log.d(TAG, "deleteShortcut: disabled shortcut id=$id")
        } catch (t: Throwable) {
            Log.w(TAG, "deleteShortcut: disableShortcuts exception for id=$id: ${t.message}", t)
        }
    }

    fun loadShortcutBitmap(context: Context, iconUri: String?): Bitmap? {
        if (iconUri.isNullOrBlank()) {
            return null
        }
        return try {
            val uri = iconUri.toUri()
            Log.d(TAG, "loadShortcutBitmap: loading bitmap from uri=$uri")
            val imageBytes = if (uri.scheme.equals("su", ignoreCase = true)) {
                val path = uri.path ?: ""
                if (path.isNotBlank()) {
                    val shell = getRootShell(true)
                    val suFile = SuFile(path)
                    suFile.shell = shell
                    SuFileInputStream.open(suFile).use { it.readBytes() }
                } else null
            } else {
                SafeUriResolver.openInputStream(context, uri)?.use { it.readBytes() }
            }
            if (imageBytes == null || imageBytes.isEmpty()) {
                Log.w(TAG, "loadShortcutBitmap: failed to read image bytes from uri=$iconUri")
                return null
            }
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOpts)
            val targetSize = 512
            val scale = maxOf(boundsOpts.outWidth, boundsOpts.outHeight) / targetSize
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = if (scale > 1) scale else 1
            }
            Log.d(TAG, "loadShortcutBitmap: outWidth=${boundsOpts.outWidth}, outHeight=${boundsOpts.outHeight}, inSampleSize=${decodeOpts.inSampleSize}")
            val rawBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts)
            if (rawBitmap != null) {
                Log.d(TAG, "loadShortcutBitmap: decoded bitmap successfully")
                val w = rawBitmap.width
                val h = rawBitmap.height
                val side = minOf(w, h)
                val x = (w - side) / 2
                val y = (h - side) / 2
                val square = try {
                    Bitmap.createBitmap(rawBitmap, x, y, side, side)
                } catch (_: Throwable) {
                    rawBitmap
                }
                if (square !== rawBitmap && !rawBitmap.isRecycled) {
                    rawBitmap.recycle()
                }
                if (side > 512) {
                    try {
                        val scaled = square.scale(512, 512)
                        if (scaled !== square && !square.isRecycled) {
                            square.recycle()
                        }
                        scaled
                    } catch (_: Throwable) {
                        square
                    }
                } else {
                    square
                }
            } else {
                Log.w(TAG, "loadShortcutBitmap: bitmap decode returned null")
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadShortcutBitmap: exception when loading icon from uri=$iconUri: ${t.message}", t)
            null
        }
    }

    private enum class ShortcutPermissionState {
        Granted,
        Denied,
        Ask,
        Unknown
    }

    private fun checkMiuiShortcutPermission(context: Context): ShortcutPermissionState {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return ShortcutPermissionState.Unknown
            val pkg = context.applicationContext.packageName
            val uid = context.applicationInfo.uid
            Log.d(TAG, "checkMiuiShortcutPermission: pkg=$pkg, uid=$uid")
            val appOpsClass = Class.forName(AppOpsManager::class.java.name)
            val method = appOpsClass.getDeclaredMethod(
                "checkOpNoThrow",
                Integer.TYPE,
                Integer.TYPE,
                String::class.java
            )
            val result = method.invoke(appOps, 10017, uid, pkg)?.toString()
            if (result == null) {
                Log.w(TAG, "checkMiuiShortcutPermission: checkOpNoThrow returned null")
                return ShortcutPermissionState.Unknown
            }
            Log.d(TAG, "checkMiuiShortcutPermission: raw result=$result")
            val state = when (result) {
                "0" -> ShortcutPermissionState.Granted
                "1" -> ShortcutPermissionState.Denied
                "5" -> ShortcutPermissionState.Ask
                else -> ShortcutPermissionState.Unknown
            }
            Log.d(TAG, "checkMiuiShortcutPermission: mapped state=$state")
            state
        } catch (t: Throwable) {
            Log.w(TAG, "checkMiuiShortcutPermission: exception=${t.message}", t)
            ShortcutPermissionState.Unknown
        }
    }

    private fun checkOppoShortcutPermission(context: Context): ShortcutPermissionState {
        val resolver = context.contentResolver ?: run {
            Log.w(TAG, "checkOppoShortcutPermission: contentResolver is null")
            return ShortcutPermissionState.Unknown
        }
        val uri = "content://settings/secure/launcher_shortcut_permission_settings".toUri()
        val cursor = resolver.query(uri, null, null, null, null) ?: run {
            Log.w(TAG, "checkOppoShortcutPermission: query returned null cursor, uri=$uri")
            return ShortcutPermissionState.Unknown
        }
        cursor.use { c ->
            val pkg = context.applicationContext.packageName
            val index = c.getColumnIndex("value")
            if (index == -1) {
                Log.w(TAG, "checkOppoShortcutPermission: 'value' column not found")
                return ShortcutPermissionState.Unknown
            }
            Log.d(TAG, "checkOppoShortcutPermission: pkg=$pkg")
            while (c.moveToNext()) {
                val value = c.getString(index)
                if (!value.isNullOrEmpty()) {
                    Log.d(TAG, "checkOppoShortcutPermission: row value=$value")
                    if (value.contains("$pkg, 1")) {
                        Log.d(TAG, "checkOppoShortcutPermission: detected Granted")
                        return ShortcutPermissionState.Granted
                    }
                    if (value.contains("$pkg, 0")) {
                        Log.d(TAG, "checkOppoShortcutPermission: detected Denied")
                        return ShortcutPermissionState.Denied
                    }
                }
            }
        }
        return ShortcutPermissionState.Unknown
    }

    private fun tryGrantMiuiShortcutPermissionByRoot(context: Context): Boolean {
        val pkg = context.applicationContext.packageName
        val cmd = "appops set $pkg 10017 allow"
        return try {
            val shell = getRootShell()
            val result = shell.newJob().add(cmd).exec()
            Log.d(TAG, "tryGrantMiuiShortcutPermissionByRoot: cmd=$cmd, code=${result.code}, isSuccess=${result.isSuccess}")
            result.isSuccess
        } catch (t: Throwable) {
            Log.w(TAG, "tryGrantMiuiShortcutPermissionByRoot: exception=${t.message}", t)
            false
        }
    }

    private fun getShortcutPermissionState(context: Context): ShortcutPermissionState {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return when {
            manufacturer.contains("xiaomi") -> checkMiuiShortcutPermission(context)
            manufacturer.contains("oppo") -> checkOppoShortcutPermission(context)
            else -> ShortcutPermissionState.Unknown
        }
    }

    private fun showShortcutPermissionHint(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        Log.d(TAG, "showShortcutPermissionHint: manufacturer=$manufacturer")
        val state = getShortcutPermissionState(context)
        val messageRes = when {
            manufacturer.contains("xiaomi") -> R.string.module_shortcut_permission_tip_xiaomi
            manufacturer.contains("oppo") -> R.string.module_shortcut_permission_tip_oppo
            else -> R.string.module_shortcut_permission_tip_default
        }
        Log.d(TAG, "showShortcutPermissionHint: state=$state, messageRes=$messageRes")
        showToast(context, context.getString(messageRes))
        if (state != ShortcutPermissionState.Granted) {
            Log.d(TAG, "showShortcutPermissionHint: state is not Granted, opening app details settings")
            openAppDetailsSettings(context)
        }
    }

    private fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            Log.d(TAG, "openAppDetailsSettings: launching settings for package=${context.packageName}")
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "openAppDetailsSettings: failed to launch settings: ${t.message}", t)
        }
    }
}
