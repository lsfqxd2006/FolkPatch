package me.bmax.apatch.shizuku;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.system.Os;
import android.util.Log;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.ipc.RootService;

public class FolkShizukuRootService extends RootService {

    private static final String TAG = "FolkShizukuRoot";
    private static volatile IBinder serviceBinder;
    private final FolkShizukuServiceImpl impl = new FolkShizukuServiceImpl(0);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Root server starting, uid=" + Os.getuid());
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.i(TAG, "Root Shizuku server bound, uid=" + Os.getuid());
        serviceBinder = impl.asBinder();
        // Deliver binder to all Shizuku apps after server is ready
        deliverToAllShizukuApps();
        return impl.asBinder();
    }

    private void deliverToAllShizukuApps() {
        new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }

            IBinder binder = serviceBinder;
            if (binder == null) return;

            PackageManager pm = getPackageManager();
            if (pm == null) return;

            String selfPkg = getPackageName();
            if (selfPkg == null) return;

            try {
                List<PackageInfo> apps = pm.getInstalledPackages(PackageManager.GET_META_DATA);

                for (PackageInfo app : apps) {
                    String pkg = app.packageName;
                    if (pkg.equals(selfPkg)) continue;

                    try {
                        Uri uri = Uri.parse("content://" + pkg + ".shizuku");
                        Bundle bundle = new Bundle();
                        bundle.putBinder("moe.shizuku.privileged.api.intent.extra.BINDER", binder);
                        getContentResolver().call(uri, "sendBinder", null, bundle);
                        Log.d(TAG, "Delivered to " + pkg);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "delivery error", e);
            }
            Log.i(TAG, "Shizuku binder delivery complete");
        }).start();
    }

    public static IBinder getBinder() {
        return serviceBinder;
    }
}
