package me.bmax.apatch.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import me.bmax.apatch.util.APatchCli

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
    }
}
