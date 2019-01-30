package com.lnysky.tech.update;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class UpdateService extends Service {

    private static final String TAG = UpdateService.class.getSimpleName();

    public static final String PARAM_UPDATE_URL = "update_url";
    public static final String PARAM_UPDATE_CONFIG = "update_config";
    public static final String ACTION_UPDATE_PROGRESS = "com.lnysky.tech.update.progress";
    public static final String ACTION_UPDATE_DOWNLOAD_COMPLETE = "com.lnysky.tech.update.download.complete";
    public static final String EXTRA_UPDATE_PROGRESS = "update_progress";
    public static final String EXTRA_UPDATE_APK_FILE_PATH = "update_apk_file_path";

    private long downloadId;
    private DownloadManager manager;
    private CompleteReceiver receiver;
    private Cache cache = null;
    private boolean autoInstall = false;
    private boolean downloadComplete = false;

    private void ensureCache(Context context) {
        if (cache == null) {
            cache = new PrefsCache(context);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        receiver = new CompleteReceiver();
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = intent.getStringExtra(PARAM_UPDATE_URL);
        UpdateConfig config = (UpdateConfig) intent.getSerializableExtra(PARAM_UPDATE_CONFIG);

        boolean download = true;
        long id = getDownloadId(url);
        if (id > 0) {
            int status = getDownloadStatus(id);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                if (config.mUseCache) {
                    download = !installApp(queryFile(downloadId), null);
                } else {
                    manager.remove(id);
                }
            } else if (status == DownloadManager.STATUS_RUNNING) {
                download = false;
                downloadId = id;
            } else if (status == DownloadManager.STATUS_PENDING
                    || status == DownloadManager.STATUS_PAUSED
                    || status == DownloadManager.STATUS_FAILED) {
                manager.remove(id);
            } else {
                manager.remove(id);
            }
        }

        autoInstall = config.mAutoInstall;

        if (download) {
            downloadId = download(url, config);
            setDownloadId(url, downloadId);
        }
        onDownloadStart();
        return Service.START_NOT_STICKY;
    }

    private long getDownloadId(String url) {
        ensureCache(this);
        return cache.getDownloadId(url);
    }

    private void setDownloadId(String url, long id) {
        ensureCache(this);
        cache.setDownloadId(url, id);
    }

    private int getDownloadStatus(long id) {
        int status = 0;
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        Cursor cursor = manager.query(query);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                status = cursor.getInt(columnIndex);
                Log.d(TAG, "id=" + id + ", status=" + status);
            }
            cursor.close();
        }
        return status;
    }

    private long download(String url, UpdateConfig config) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(config.mTitle);
        int networkTypes = DownloadManager.Request.NETWORK_WIFI;
        if (!config.mOnlyWifi) {
            networkTypes |= DownloadManager.Request.NETWORK_MOBILE;
        } else if (!UpdateUtils.isConnectedWifi(this)) {
            showToast(this, getString(R.string.update_toast_open_wifi));
        }
        request.setAllowedNetworkTypes(networkTypes);
        if (config.mShowNotify && config.mAutoInstall) {
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        } else if (config.mShowNotify) {
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        } else if (config.mAutoInstall) {
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        } else {
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
        }
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, config.mFileName);
        request.setVisibleInDownloadsUi(true);
        request.setAllowedOverRoaming(false);
        request.allowScanningByMediaScanner();

        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String mimeString = mimeTypeMap.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url));
        request.setMimeType(mimeString);
        return manager.enqueue(request);
    }

    @Override
    public void onDestroy() {
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        onDownloadStop();
        if (!downloadComplete) {
            manager.remove(downloadId);
        }
        super.onDestroy();
    }

    class CompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            boolean install = false;
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long downId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == downId) {
                    downloadComplete = true;
                    onDownloadStop();
                }
                install = downloadId == downId && autoInstall;
            } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
                long[] ids = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
                for (long id : ids) {
                    if (id == downloadId) {
                        install = !autoInstall;
                        break;
                    }
                }
            }
            File apkFile = queryFile(downloadId);
            notifyDownloadComplete(apkFile);
            if (install && installApp(apkFile, new Install.Callback() {
                @Override
                public void onGranted(File file) {
                    Log.i(TAG, "允许安装");
                    stopSelf();
                }

                @Override
                public void onDenied(File file) {
                    Log.i(TAG, "不允许安装");
                    stopSelf();
                }
            })) {
                // do noting
            } else {
                showToast(context, getString(R.string.update_toast_install_fail));
                stopSelf();
            }
        }
    }

    private void showToast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private boolean installApp(File apkFile, Install.Callback callback) {
        Context context = getApplicationContext();
        if (apkFile != null && apkFile.exists()) {
            Install.install(context, apkFile, callback);
            return true;
        }
        return false;
    }

    private void notifyDownloadComplete(File apkFile) {
        Log.d(TAG, "notifyDownloadComplete");
        Intent intent = new Intent(ACTION_UPDATE_DOWNLOAD_COMPLETE);
        intent.putExtra(UpdateService.EXTRA_UPDATE_APK_FILE_PATH, apkFile.getPath());
        sendBroadcast(intent);
    }

    private File queryFile(long id) {
        File apk = null;
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
        Cursor cur = manager.query(query);
        if (cur != null) {
            if (cur.moveToFirst()) {
                String localUri = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                try {
                    Uri uri = Uri.parse(localUri);
                    if (uri != null && uri.getPath() != null) {
                        apk = new File(uri.getPath());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            cur.close();
        }
        return apk;
    }

    private Handler downLoadHandler = new Handler();

    private Timer timer;
    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            updateProgress();
        }
    };

    private void updateProgress() {
        final int[] bytesAndStatus = getBytesAndStatus(downloadId);
        downLoadHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "downloadId=" + downloadId + " so far = " + bytesAndStatus[0] + " total=" + bytesAndStatus[1] + " state=" + bytesAndStatus[2]);
                if (bytesAndStatus[0] > 0
                        && bytesAndStatus[1] > 0
                        && bytesAndStatus[2] != DownloadManager.STATUS_SUCCESSFUL) {
                    Intent intent = new Intent(ACTION_UPDATE_PROGRESS);
                    intent.putExtra(UpdateService.EXTRA_UPDATE_PROGRESS, bytesAndStatus);
                    sendBroadcast(intent);
                }
            }
        });
    }

    private void onDownloadStart() {
        Log.d(TAG, "onDownloadStart");
        if (timer == null) {
            timer = new Timer();
        }
        if (timerTask != null) {
            timerTask.cancel();
        }
        timerTask = new TimerTask() {
            @Override
            public void run() {
                updateProgress();
            }
        };
        timer.schedule(timerTask, 0, 1000);
    }

    private void onDownloadStop() {
        Log.d(TAG, "onDownloadStop");
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private int[] getBytesAndStatus(long downloadId) {
        int[] bytesAndStatus = new int[]{-1, -1, 0};

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = null;

        try {
            cursor = manager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                bytesAndStatus[0] = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                bytesAndStatus[1] = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                bytesAndStatus[2] = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return bytesAndStatus;
    }

}