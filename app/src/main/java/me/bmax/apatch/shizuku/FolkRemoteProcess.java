package me.bmax.apatch.shizuku;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;

import moe.shizuku.server.IRemoteProcess;

class FolkRemoteProcess extends IRemoteProcess.Stub {

    private final java.lang.Process process;

    FolkRemoteProcess(java.lang.Process process) {
        this.process = process;
    }

    private static FileDescriptor getFd(java.io.InputStream stream) {
        try {
            Field f = stream.getClass().getDeclaredField("fd");
            f.setAccessible(true);
            return (FileDescriptor) f.get(stream);
        } catch (Exception e) {
            return null;
        }
    }

    private static FileDescriptor getFd(java.io.OutputStream stream) {
        try {
            Field f = stream.getClass().getDeclaredField("fd");
            f.setAccessible(true);
            return (FileDescriptor) f.get(stream);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor getInputStream() {
        FileDescriptor fd = getFd(process.getInputStream());
        if (fd != null && fd.valid()) {
            try {
                return ParcelFileDescriptor.dup(fd);
            } catch (IOException ignored) {}
        }
        return null;
    }

    @Override
    public ParcelFileDescriptor getOutputStream() {
        FileDescriptor fd = getFd(process.getOutputStream());
        if (fd != null && fd.valid()) {
            try {
                return ParcelFileDescriptor.dup(fd);
            } catch (IOException ignored) {}
        }
        return null;
    }

    @Override
    public ParcelFileDescriptor getErrorStream() {
        FileDescriptor fd = getFd(process.getErrorStream());
        if (fd != null && fd.valid()) {
            try {
                return ParcelFileDescriptor.dup(fd);
            } catch (IOException ignored) {}
        }
        return null;
    }

    @Override
    public int waitFor() throws RemoteException {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void destroy() {
        process.destroy();
    }

    @Override
    public boolean alive() {
        return process.isAlive();
    }

    @Override
    public boolean waitForTimeout(long timeout, String unit) {
        long timeoutMs;
        if ("s".equals(unit)) {
            timeoutMs = timeout * 1000;
        } else if ("m".equals(unit)) {
            timeoutMs = timeout * 60 * 1000;
        } else if ("h".equals(unit)) {
            timeoutMs = timeout * 60 * 60 * 1000;
        } else {
            timeoutMs = timeout;
        }
        try {
            process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
