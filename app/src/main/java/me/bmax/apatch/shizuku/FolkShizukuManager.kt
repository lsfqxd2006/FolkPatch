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
import com.topjohnwu.superuser.ipc.RootService
import me.bmax.apatch.util.APatchCli
import java.util.concurrent.ConcurrentLinkedQueue

object FolkShizukuManager {

    private const val TAG = "FolkShizuku"
    private var serviceBinder: IBinder? = null
    private var started = false
    private var appContext: Context? = null
    private val pendingApps = ConcurrentLinkedQueue<String>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Shizuku service connected")
            serviceBinder = binder
            if (binder != null) {
                val ctx = appContext
                while (true) {
                    val pkg = pendingApps.poll() ?: break
                    if (ctx != null) deliverBinderToApp(ctx, pkg, binder)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Shizuku service disconnected")
            serviceBinder = null
            started = false
        }
    }

    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext

        Log.i(TAG, "Starting Shizuku service...")
        val intent = Intent(context, FolkShizukuService::class.java)
        val task = RootService.bindOrTask(
            intent,
            Shell.EXECUTOR,
            connection
        )
        if (task != null) {
            APatchCli.SHELL.execTask(task)
        }
    }

    fun sendBinderToApp(context: Context, packageName: String) {
        appContext = context.applicationContext
        val binder = serviceBinder
        if (binder != null) {
            deliverBinderToApp(context, packageName, binder)
        } else {
            pendingApps.add(packageName)
        }
    }

    private fun deliverBinderToApp(context: Context, packageName: String, binder: IBinder) {
        try {
            val uri = Uri.parse("content://$packageName.shizuku")
            val bundle = Bundle()
            bundle.putBinder("moe.shizuku.privileged.api.intent.extra.BINDER", binder)
            context.contentResolver.call(uri, "sendBinder", null, bundle)
            Log.d(TAG, "Delivered binder to $packageName")
        } catch (e: Exception) {
            Log.d(TAG, "No ShizukuProvider in $packageName: ${e.message}")
        }
    }

    fun discoverAndDeliverToAll(context: Context) {
        val pm = context.packageManager
        val intent = Intent("moe.shizuku.api.action.BINDER_RECEIVED")
        val res = pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        for (ri in res) {
            val pkg = ri.activityInfo.packageName
            if (pkg != context.packageName) {
                sendBinderToApp(context, pkg)
            }
        }
    }
}
