package com.huiyu.tech.update;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.text.TextUtils;

/**
 * Created by lny on 2017/3/17.
 * 版本升级
 */
public class Update {

    public static final int SUCCESS = 0;
    public static final int ERROR_INVALID_URL = -1;
    public static final int ERROR_PERMISSION_DENIED = -2;

    private final Context mContext;
    private final Builder mBuilder;

    private Update(Context context, Builder builder) {
        this.mContext = context;
        this.mBuilder = builder;
    }

    private int start() {
        if (!UpdateUtils.hasPermission(mContext)) {
            return ERROR_PERMISSION_DENIED;
        }

        if (!UpdateUtils.isValidUrl(mBuilder.mUrl)) {
            return ERROR_INVALID_URL;
        }

        if (TextUtils.isEmpty(mBuilder.mConfig.mFileName)) {
            mBuilder.mConfig.mFileName = UpdateUtils.getFileNameByUrl(mBuilder.mUrl);
        }

        Intent intent = new Intent();
        intent.putExtra(UpdateService.PARAM_UPDATE_URL, mBuilder.mUrl);
        intent.putExtra(UpdateService.PARAM_UPDATE_CONFIG, mBuilder.mConfig);
        intent.setClass(mContext, UpdateService.class);
        mContext.startService(intent);
        return SUCCESS;
    }

    public void update() {
        int ret = start();
        mBuilder.mCallback.onResult(ret);
    }

    public static Builder with(Context context) {
        if (context == null) {
            throw new NullPointerException("context must be not null");
        }
        return new Builder(context.getApplicationContext());
    }

    public static class Builder {

        private final Context mContext;
        private final UpdateConfig mConfig;
        private String mUrl;
        private Callback mCallback;

        Builder(Context context) {
            this.mContext = context;
            this.mConfig = new UpdateConfig();
        }

        public Builder url(String url) {
            mUrl = url;
            return this;
        }

        public Builder title(String title) {
            mConfig.mTitle = title;
            return this;
        }

        public Builder notification() {
            mConfig.mShowNotify = true;
            return this;
        }

        public Builder autoInstall() {
            mConfig.mAutoInstall = true;
            return this;
        }

        public Builder wifi() {
            mConfig.mOnlyWifi = true;
            return this;
        }

        public Builder callback(Callback callback) {
            mCallback = callback;
            return this;
        }

        @NonNull
        public Update build() {
            if (mCallback == null) {
                mCallback = new SampleCallback();
            }
            return new Update(mContext, this);
        }

        public void update() {
            Update update = build();
            int ret = update.start();
            mCallback.onResult(ret);
        }
    }

    public interface Callback {

        void onResult(int error);
    }

    private static class SampleCallback implements Callback {

        @Override
        public void onResult(int result) {

        }
    }
}
