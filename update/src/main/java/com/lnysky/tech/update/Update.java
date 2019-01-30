package com.lnysky.tech.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.io.File;

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

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdateService.ACTION_UPDATE_PROGRESS.equals(intent.getAction())) {
                int[] bytesAndStatus = intent.getIntArrayExtra(UpdateService.EXTRA_UPDATE_PROGRESS);
                if (mBuilder.mOnProgressListener != null) {
                    mBuilder.mOnProgressListener.onProgress((int) ((float) bytesAndStatus[0] / bytesAndStatus[1] * 100));
                }
            } else if (UpdateService.ACTION_UPDATE_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                if (mBuilder.mOnResultListener != null) {
                    String filePath = intent.getStringExtra(UpdateService.EXTRA_UPDATE_APK_FILE_PATH);
                    mBuilder.mOnResultListener.onResult(new File(filePath));
                }
            }
        }
    };

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

        if (mBuilder.mOnProgressListener != null) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(UpdateService.ACTION_UPDATE_PROGRESS);
            intentFilter.addAction(UpdateService.ACTION_UPDATE_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(broadcastReceiver, intentFilter);
        }
        return SUCCESS;
    }

    public void stop() {
        mContext.stopService(new Intent(mContext, UpdateService.class));
        if (mBuilder.mOnProgressListener != null) {
            mContext.unregisterReceiver(broadcastReceiver);
        }
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
        private OnProgressListener mOnProgressListener;
        private OnResultListener mOnResultListener;

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

        public Builder useCache() {
            mConfig.mUseCache = true;
            return this;
        }

        public Builder progressListener(OnProgressListener onProgressListener) {
            this.mOnProgressListener = onProgressListener;
            return this;
        }

        public Builder resultListener(OnResultListener onResultListener) {
            this.mOnResultListener = onResultListener;
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

    public interface OnProgressListener {
        void onProgress(int progress);
    }

    public interface OnResultListener {
        void onResult(File file);
    }

    private static class SampleCallback implements Callback {

        @Override
        public void onResult(int result) {

        }
    }
}
