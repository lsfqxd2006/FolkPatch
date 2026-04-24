package me.bmax.apatch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.util.writePathHideUids
import me.bmax.apatch.util.readPathHideUids

class PackageInstalledReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PkgInstallReceiver"

        // Lock to serialize file writes between concurrent package installs
        private val fileLock = Any()

        /**
         * Auto-exclude a single newly installed package.
         *
         * Safety guarantees:
         * - Single-UID scope: only processes the one package from the intent
         * - UID >= 10000: only user apps, never system processes
         * - Self-exclusion prevention: never excludes FolkPatch itself
         * - Idempotency: skips if UID already in whitelist
         * - Prerequisite checks: only runs when pathhide enabled AND uid mode active
         *
         * Returns the excluded UID, or null if skipped.
         */
        fun handleAutoExclude(context: Context, packageName: String): Int? {
            // Guard 1: SharedPreferences toggle must be ON
            val prefs = APApplication.sharedPreferences
            if (!prefs.getBoolean(APApplication.PREF_PATHHIDE_AUTO_EXCLUDE, false)) {
                return null
            }

            // Guard 2: PathHide must be enabled (flag file exists)
            val pathHideEnabled = try {
                val f = java.io.File(APApplication.PATHHIDE_ENABLE_FILE)
                f.exists()
            } catch (_: Exception) {
                false
            }
            if (!pathHideEnabled) {
                Log.w(TAG, "Auto-exclude skipped: pathhide not enabled")
                return null
            }

            // Guard 3: UID mode must be active
            val uidModeOn = prefs.getBoolean("pathhide_uid_mode", false)
            if (!uidModeOn) {
                Log.w(TAG, "Auto-exclude skipped: UID mode inactive")
                return null
            }

            // Guard 4: Self-exclusion prevention
            if (packageName == context.packageName) {
                Log.i(TAG, "Auto-exclude skipped: self ($packageName)")
                return null
            }

            // Look up UID for the package
            val pm = context.packageManager
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get app info for $packageName", e)
                return null
            }
            val uid = appInfo.uid

            // Guard 5: Only user apps (UID >= 10000)
            if (uid < 10000) {
                Log.i(TAG, "Auto-exclude skipped: system UID $uid ($packageName)")
                return null
            }

            // Guard 6: Idempotency -- skip if already in whitelist
            val currentUids = readPathHideUids()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { it.toIntOrNull() }
                .toSet()
            if (uid in currentUids) {
                Log.i(TAG, "Auto-exclude skipped: UID $uid already in whitelist")
                return null
            }

            // Single-UID kernel operation
            val rc = Natives.pathHideUidAdd(uid)
            if (rc < 0) {
                Log.e(TAG, "Kernel pathHideUidAdd($uid) failed: $rc")
                return null
            }

            // Persist: append to config file (synchronized to prevent race with concurrent installs)
            synchronized(fileLock) {
                val newUids = (currentUids + uid).joinToString("\n")
                writePathHideUids(newUids)
            }

            Log.i(TAG, "Auto-excluded $packageName (UID $uid)")
            return uid
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        // Only handle fresh installs, NOT updates
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        Log.d(TAG, "Package added: $packageName")

        // Run on background thread to avoid blocking main thread
        Thread {
            handleAutoExclude(context, packageName)
        }.start()
    }
}
