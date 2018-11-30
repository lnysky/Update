package com.lnysky.tech.update;

import android.content.Context;
import android.support.v4.content.FileProvider;


public class UpdateFileProvider extends FileProvider {

    public static String getProviderName(Context context) {
        return context.getPackageName() + ".update.provider";
    }

}
