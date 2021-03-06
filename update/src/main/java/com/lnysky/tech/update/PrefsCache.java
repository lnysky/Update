package com.lnysky.tech.update;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by lny on 2017/3/30.
 */
class PrefsCache implements Cache {

    private final SharedPreferences prefs;

    PrefsCache(Context context) {
        prefs = context.getSharedPreferences(context.getPackageName() + "_update", Context.MODE_PRIVATE);
    }

    @Override
    public void setDownloadId(String url, long id) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(url, id);
        editor.apply();
    }

    @Override
    public long getDownloadId(String url) {
        return prefs.getLong(url, 0);
    }
}
