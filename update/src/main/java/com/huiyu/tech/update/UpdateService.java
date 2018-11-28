package com.huiyu.tech.update;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;

public class UpdateService extends Service {

    private static final String TAG = UpdateService.class.getSimpleName();

    public static final String PARAM_UPDATE_URL = "update_url";
    public static final String PARAM_UPDATE_CONFIG = "update_config";

    private long downloadId;
    private DownloadManager manager;
    private CompleteReceiver receiver;
    private Cache cache = null;
    private boolean autoInstall = false;

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
                download = !installApp(id);
            } else if (status == DownloadManager.STATUS_PENDING
                    || status == DownloadManager.STATUS_RUNNING) {
                download = false;
            } else if (status == DownloadManager.STATUS_PAUSED
                    || status == DownloadManager.STATUS_FAILED) {
                manager.remove(id);
            }
        }

        if (download) {
            downloadId = download(url, config);
            setDownloadId(url, downloadId);
        }
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
        autoInstall = config.mAutoInstall;

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
        super.onDestroy();
    }

    class CompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean install = false;
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long downId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                install = downloadId == downId && autoInstall;
            } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
                // 不会走到这里?文件类型未知会不会走呢?
                long[] ids = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
                for (long id : ids) {
                    if (id == downloadId) {
                        install = !autoInstall;
                        break;
                    }
                }
            }
            if (install) {
                if (installApp(downloadId)) {
                    stopSelf();
                } else {
                    showToast(context, getString(R.string.update_toast_install_fail));
                }
            }
        }
    }

    private void showToast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private boolean installApp(long downId) {
        boolean ret = false;
        Context context = getApplicationContext();
        File apkFile = queryFile(downId);
        if (apkFile != null && apkFile.exists()) {
            if (canInstall(context)) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    uri = FileProvider.getUriForFile(context, UpdateFileProvider.getProviderName(context), apkFile);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    uri = Uri.fromFile(apkFile);
                }
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                ret = true;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                showToast(context, getString(R.string.update_toast_allow_install_from_unknown));
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
                ret = true;
            }
        }
        return ret;
    }

    private boolean canInstall(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
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
}