package me.bmax.apatch.shizuku;

import android.content.Intent;
import android.os.IBinder;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.ipc.RootService;

import java.io.FileDescriptor;

public class FolkShizukuShellService extends RootService {

    private static final String TAG = "FolkShizukuShell";
    private static volatile IBinder serviceBinder;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Os.setuid(2000);
            Os.setgid(2000);
            Log.i(TAG, "Dropped privileges to shell uid=" + Os.getuid());
        } catch (Exception e) {
            Log.e(TAG, "Failed to drop to shell uid", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.i(TAG, "Shell Shizuku server bound, uid=" + Os.getuid());
        FolkShizukuServiceImpl impl = new FolkShizukuServiceImpl(2000);
        serviceBinder = impl.asBinder();
        return impl.asBinder();
    }

    public static IBinder getBinder() {
        return serviceBinder;
    }
}
