package me.bmax.apatch.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import me.bmax.apatch.APApplication
import me.bmax.apatch.R

object LauncherIconUtils {
    private const val MAIN_ACTIVITY = ".ui.MainActivityDefault"
    private const val ALIAS_ACTIVITY = ".ui.MainActivityAlias"
    private const val ALIAS_ACTIVITY_SU = ".ui.MainActivityAliasSu"
    private const val ALIAS_ACTIVITY_ALT_SU = ".ui.MainActivityAliasAltSu"

    const val PREF_ICON_STYLE = "launcher_icon_style"
    const val ICON_STYLE_GEOMETRY = "geometry"
    const val ICON_STYLE_APATCH = "apatch"

    /**
     * 解析当前图标风格。为从双图标版本升级的老用户保留 use_alt_icon 兼容，
     * 新装默认 geometry。
     */
    fun currentStyle(context: Context): String {
        val prefs = APApplication.sharedPreferences
        prefs.getString(PREF_ICON_STYLE, null)?.let { return it }
        val style = if (prefs.getBoolean("use_alt_icon", false)) ICON_STYLE_APATCH else ICON_STYLE_GEOMETRY
        prefs.edit().putString(PREF_ICON_STYLE, style).apply()
        return style
    }

    /**
     * 切换图标风格。
     * 顺序：
     *   1. 记录偏好
     *   2. 启停 alias → 桌面 App 图标切换
     *   3. 通知 ModuleShortcut 刷新已 pinned 的模块/脚本快捷方式图标
     */
    fun setStyle(context: Context, style: String) {
        APApplication.sharedPreferences.edit().putString(PREF_ICON_STYLE, style).apply()
        updateLauncherState(context)
        ModuleShortcut.onLauncherIconStyleChanged(context)
    }

    /**
     * 当前风格对应的快捷方式兜底图标资源。
     * 与 AndroidManifest 中各 alias 的 android:icon 保持一致：
     *   geometry / SU+geometry → ic_launcher
     *   apatch  / SU+apatch    → ic_launcher_alt
     * SU 变体只改 label，不改图标，故此处只按 style 判断。
     */
    fun currentLauncherIconRes(context: Context): Int =
        if (currentStyle(context) == ICON_STYLE_APATCH) R.mipmap.ic_launcher_alt
        else R.mipmap.ic_launcher

    /** 当前启用的 launcher alias。仅用于桌面 App 图标切换，不作为快捷方式目标。 */
    fun enabledLauncherComponent(context: Context): ComponentName {
        val prefs = APApplication.sharedPreferences
        val style = currentStyle(context)
        val appName = prefs.getString("desktop_app_name", "FolkPatch")
        val isSu = appName == "FPatch"
        return componentFor(style, isSu, context)
    }

    private fun componentFor(style: String, isSu: Boolean, context: Context): ComponentName {
        val basePackage = APApplication::class.java.`package`?.name ?: "me.bmax.apatch"
        fun alias(name: String) = ComponentName(context.packageName, basePackage + name)
        return when {
            style == ICON_STYLE_APATCH && isSu -> alias(ALIAS_ACTIVITY_ALT_SU)
            style == ICON_STYLE_APATCH -> alias(ALIAS_ACTIVITY)
            isSu -> alias(ALIAS_ACTIVITY_SU)
            else -> alias(MAIN_ACTIVITY)
        }
    }

    fun updateLauncherState(context: Context) {
        val pm = context.packageManager
        val basePackage = APApplication::class.java.`package`?.name ?: "me.bmax.apatch"

        val allComponents = listOf(
            MAIN_ACTIVITY, ALIAS_ACTIVITY, ALIAS_ACTIVITY_SU, ALIAS_ACTIVITY_ALT_SU
        ).map { name -> ComponentName(context.packageName, basePackage + name) }

        val targetComponent = enabledLauncherComponent(context)

        try {
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            allComponents.filter { it != targetComponent }.forEach {
                pm.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applySaved(context: Context) {
        updateLauncherState(context)
    }
}
