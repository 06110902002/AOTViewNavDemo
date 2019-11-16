/*
 * Level11Application
 * Last modified on: 25/11/2015
 *
 * Creatd by Pointr.
 * Copyright (c) 2015 Pointr. All rights reserved.
 */

package com.pointrlabs.sample;

import android.app.Application;
import android.os.Build;

import com.pointrlabs.core.license.LicenseKey;
import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.nativecore.wrappers.Plog;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            String string = getApplicationContext().getResources().getString(R.string.pointr_licence);
            Pointr.with(getApplicationContext(), new LicenseKey(string), Plog.LogLevel.VERBOSE);
        }
    }
}