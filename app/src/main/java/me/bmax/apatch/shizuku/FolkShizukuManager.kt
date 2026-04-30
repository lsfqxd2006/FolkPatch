package me.bmax.apatch.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import me.bmax.apatch.util.APatchCli
import kotlin.concurrent.thread

object FolkShizukuManager {

    private const val TAG = "FolkShizukuMgr"
    private var started = false

    private val rootConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Root server connected")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Root server disconnected")
        }
    }

    private val shellConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Shell server connected")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Shell server disconnected")
        }
    }

    fun start(context: Context) {
        if (started) return
        started = true

        Log.i(TAG, "Starting root Shizuku server...")
        var task = RootService.bindOrTask(
            Intent(context, FolkShizukuRootService::class.java),
            Shell.EXECUTOR,
            rootConnection
        )
        if (task != null) APatchCli.SHELL.execTask(task)

        Log.i(TAG, "Starting shell Shizuku server...")
        task = RootService.bindOrTask(
            Intent(context, FolkShizukuShellService::class.java),
            Shell.EXECUTOR,
            shellConnection
        )
        if (task != null) APatchCli.SHELL.execTask(task)

        // Wait for servers to start, then deliver binders to all Shizuku apps
        thread {
            Thread.sleep(3000)
            deliverToAllShizukuApps(context)
        }
    }

    /**
     * Find all installed apps that have ShizukuProvider and deliver
     * the appropriate server binder to each.
     */
    private fun deliverToAllShizukuApps(context: Context) {
        Log.i(TAG, "Discovering Shizuku apps...")
        val rootBinder = FolkShizukuRootService.getBinder()
        val shellBinder = FolkShizukuShellService.getBinder()

        if (rootBinder == null && shellBinder == null) {
            Log.w(TAG, "No server binders available, skipping delivery")
            return
        }

        val pm = context.packageManager
        // Query for providers with the Shizuku authority pattern
        val intent = Intent("moe.shizuku.api.action.BINDER_RECEIVED")
        val receivers = pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)

        for (ri in receivers) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName) continue

            // Deliver root binder for now (per-app routing can be added later)
            val binder = rootBinder
            if (binder == null) continue

            try {
                val uri = Uri.parse("content://$pkg.shizuku")
                val bundle = Bundle()
                bundle.putBinder("moe.shizuku.privileged.api.intent.extra.BINDER", binder)
                context.contentResolver.call(uri, "sendBinder", null, bundle)
                Log.d(TAG, "Delivered Shizuku binder to $pkg")
            } catch (e: SecurityException) {
                Log.d(TAG, "No permission to deliver to $pkg")
            } catch (e: Exception) {
                Log.d(TAG, "No ShizukuProvider in $pkg")
            }
        }
        Log.i(TAG, "Shizuku delivery complete")
    }
}
