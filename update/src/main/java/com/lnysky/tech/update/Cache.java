package com.lnysky.tech.update;

/**
 * Created by lny on 2017/3/30.
 */
interface Cache {

    void setDownloadId(String url, long id);

    long getDownloadId(String url);
}
