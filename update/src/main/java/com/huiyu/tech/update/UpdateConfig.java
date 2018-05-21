package com.huiyu.tech.update;

import java.io.Serializable;

/**
 * Created by lny on 2018/5/12.
 */
class UpdateConfig implements Serializable {
    String mTitle;
    String mFileName;
    boolean mShowNotify = false;
    boolean mAutoInstall = false;
    boolean mOnlyWifi = false;
}
