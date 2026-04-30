package me.bmax.apatch.shizuku;

import android.content.Intent;
import android.os.IBinder;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.ipc.RootService;

public class FolkShizukuRootService extends RootService {

    private static final String TAG = "FolkShizukuRoot";
    private static volatile IBinder serviceBinder;
    private final FolkShizukuServiceImpl impl = new FolkShizukuServiceImpl(0);

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.i(TAG, "Root Shizuku server bound, uid=" + Os.getuid());
        serviceBinder = impl.asBinder();
        return impl.asBinder();
    }

    public static IBinder getBinder() {
        return serviceBinder;
    }
}
