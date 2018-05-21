package com.huiyu.tech.update;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.util.Patterns;

/**
 * Created by lny on 2017/3/17.
 */
class UpdateUtils {

    static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN
                || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isValidUrl(String url) {
        return Patterns.WEB_URL.matcher(url).matches();
    }

    static boolean isConnectedWifi(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        NetworkInfo ni = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return ni.isConnected();
    }

    static String getFileNameByUrl(String url) {
        String name = url.substring(url.lastIndexOf("/") + 1);
        if (!name.endsWith(".apk")) {
            name += ".apk";
        }
        return name;
    }

}
