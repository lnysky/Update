package com.lnysky.tech.update;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Created by lny on 2017/3/17.
 */
public final class Install {

    private static final int OP_REQUEST_INSTALL_PACKAGES = 66;

    private static int mTargetSdkVersion;

    private Install() {

    }

    private static int getTargetSdkVersion(Context context) {
        if (mTargetSdkVersion < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mTargetSdkVersion = context.getApplicationInfo().targetSdkVersion;
        }
        return mTargetSdkVersion;
    }

    private static PackageManager getPackageManager(Context context) {
        return context.getPackageManager();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static AppOpsManager getAppOpsManager(Context context) {
        return (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
    }

    public static void install(final Context context, final File apkFile) {
        install(context, apkFile, null);
    }

    public static void install(final Context context, final File apkFile, final Callback callback) {
        if (canRequestPackageInstalls(context)) {
            callbackSucceed(callback, apkFile);
            UpdateUtils.install(context, apkFile);
        } else {
            PermissionActivity.requestInstall(context, new PermissionActivity.RequestListener() {
                @Override
                public void onRequestCallback() {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            dispatchCallback(context, apkFile, callback);
                        }
                    });
                }
            });
        }
    }

    private static boolean canRequestPackageInstalls(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (getTargetSdkVersion(context) < Build.VERSION_CODES.O) {
                Class<AppOpsManager> clazz = AppOpsManager.class;
                try {
                    Method method = clazz.getDeclaredMethod("checkOpNoThrow", int.class, int.class, String.class);
                    int result = (int) method.invoke(getAppOpsManager(context), OP_REQUEST_INSTALL_PACKAGES, android.os.Process.myUid(), context.getPackageName());
                    return result == AppOpsManager.MODE_ALLOWED;
                } catch (Exception ignored) {
                    // Android P does not allow reflections.
                    return true;
                }
            }
            return getPackageManager(context).canRequestPackageInstalls();
        }
        return true;
    }

    private static void dispatchCallback(Context context, File apkFile, Callback callback) {
        if (canRequestPackageInstalls(context)) {
            callbackSucceed(callback, apkFile);
            install(context, apkFile);
        } else {
            callbackFailed(callback, apkFile);
        }
    }

    private static void callbackSucceed(Callback callback, File file) {
        if (callback != null) {
            callback.onGranted(file);
        }
    }

    private static void callbackFailed(Callback callback, File file) {
        if (callback != null) {
            callback.onDenied(file);
        }
    }

    public interface Callback {
        void onGranted(File file);

        void onDenied(File file);
    }
}
