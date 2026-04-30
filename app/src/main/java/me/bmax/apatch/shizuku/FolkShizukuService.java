package me.bmax.apatch.shizuku;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.ipc.RootService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IShizukuServiceConnection;

public class FolkShizukuService extends RootService {

    private static final String TAG = "FolkShizukuService";
    private static final int BINDER_TRANSACTION_transact = 1;
    private static final String BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    private static final int SERVER_VERSION = 13;

    private static volatile IBinder serviceBinder;

    private static String sContextCache;

    private static String getSelinuxContext() {
        if (sContextCache != null) return sContextCache;
        try {
            Class<?> selinux = Class.forName("android.os.SELinux");
            Method getContext = selinux.getDeclaredMethod("getContext");
            sContextCache = (String) getContext.invoke(null);
            return sContextCache;
        } catch (Exception e) {
            return "u:r:su:s0";
        }
    }

    private static String getSystemProp(String name, String defaultValue) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getDeclaredMethod("get", String.class, String.class);
            return (String) get.invoke(null, name, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static void setSystemProp(String name, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method set = sp.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, name, value);
        } catch (Exception ignored) {
        }
    }

    private final IShizukuService.Stub serviceImpl = new IShizukuService.Stub() {

        @Override
        public int getVersion() {
            return SERVER_VERSION;
        }

        @Override
        public int getUid() {
            return Os.getuid();
        }

        @Override
        public int checkPermission(String permission) {
            return android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public IRemoteProcess newProcess(String[] cmd, String[] env, String dir) {
            Log.d(TAG, "newProcess: uid=" + Binder.getCallingUid() + ", cmd=" + Arrays.toString(cmd));
            java.lang.Process process;
            try {
                process = Runtime.getRuntime().exec(cmd, env, dir != null ? new File(dir) : null);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage());
            }
            return new FolkRemoteProcess(process);
        }

        @Override
        public String getSELinuxContext() {
            return getSelinuxContext();
        }

        @Override
        public String getSystemProperty(String name, String defaultValue) {
            return getSystemProp(name, defaultValue);
        }

        @Override
        public void setSystemProperty(String name, String value) {
            setSystemProp(name, value);
        }

        @Override
        public int addUserService(IShizukuServiceConnection conn, Bundle args) {
            return 0;
        }

        @Override
        public int removeUserService(IShizukuServiceConnection conn, Bundle args) {
            return 0;
        }

        @Override
        public void requestPermission(int requestCode) {
        }

        @Override
        public boolean checkSelfPermission() {
            return true;
        }

        @Override
        public boolean shouldShowRequestPermissionRationale() {
            return false;
        }

        @Override
        public void attachApplication(IShizukuApplication application, Bundle args) {
            if (application == null || args == null) return;

            try {
                Bundle reply = new Bundle();
                reply.putInt("shizuku:attach-reply-uid", Os.getuid());
                reply.putInt("shizuku:attach-reply-version", SERVER_VERSION);
                reply.putInt("shizuku:attach-reply-patch-version", 6);
                reply.putString("shizuku:attach-reply-secontext", getSELinuxContext());
                reply.putBoolean("shizuku:attach-reply-permission-granted", true);
                application.bindApplication(reply);
            } catch (RemoteException e) {
                Log.w(TAG, "attachApplication callback failed", e);
            }
        }

        @Override
        public void exit() {
            Log.i(TAG, "exit requested");
            FolkShizukuService.this.stopSelf();
        }

        @Override
        public void attachUserService(IBinder binder, Bundle options) {
        }

        @Override
        public void dispatchPackageChanged(Intent intent) {
        }

        @Override
        public boolean isHidden(int uid) {
            return false;
        }

        @Override
        public void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, Bundle data) {
        }

        @Override
        public int getFlagsForUid(int uid, int mask) {
            return 0;
        }

        @Override
        public void updateFlagsForUid(int uid, int mask, int value) {
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == BINDER_TRANSACTION_transact) {
                data.enforceInterface(BINDER_DESCRIPTOR);
                transactRemote(data, reply, flags);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private void transactRemote(Parcel data, Parcel reply, int flags) {
            IBinder targetBinder = data.readStrongBinder();
            int targetCode = data.readInt();
            int targetFlags;
            try {
                targetFlags = data.readInt();
            } catch (Exception e) {
                targetFlags = flags;
            }

            Parcel newData = Parcel.obtain();
            try {
                newData.appendFrom(data, data.dataPosition(), data.dataAvail());
            } catch (Throwable tr) {
                Log.w(TAG, "appendFrom error", tr);
                newData.recycle();
                return;
            }

            try {
                long id = Binder.clearCallingIdentity();
                targetBinder.transact(targetCode, newData, reply, targetFlags);
                Binder.restoreCallingIdentity(id);
            } catch (RemoteException e) {
                Log.w(TAG, "transactRemote failed", e);
            } finally {
                newData.recycle();
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.i(TAG, "Shizuku service bound, uid=" + Os.getuid());
        serviceBinder = serviceImpl.asBinder();
        return serviceImpl.asBinder();
    }

    public static IBinder getServiceBinder() {
        return serviceBinder;
    }
}
