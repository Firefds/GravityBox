/*
 * Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.oreo.gravitybox;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialerOOS {
    public static final String PACKAGE_NAME_IN_CALL_UI = "com.android.incallui";
    public static final String PACKAGE_NAME_DIALER = "com.android.dialer";
    public static final String CLASS_DIALER_SETTINGS_ACTIVITY = "com.oneplus.dialer.settings.OPDialerSettingsActivity";
    public static final String CLASS_IN_CALL_ACTIVITY = "com.android.incallui.InCallActivity";
    private static final String TAG = "GB:ModDialerOOS";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static XSharedPreferences mPrefs;
    private static long mLastPrefReloadMs;

    public static void initInCallUi(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("initInCallUi");
        mPrefs = prefs;

        XposedHelpers.findAndHookMethod(CLASS_IN_CALL_ACTIVITY, classLoader,
                "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadPrefsIfExpired();
                if (mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_OOS_CALL_RECORDING, false)) {
                    enableCallRecording((Context)param.thisObject);
                }
            }
        });
    }

    public static void initDialer(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("initDialer");
        mPrefs = prefs;

        XposedHelpers.findAndHookMethod(CLASS_DIALER_SETTINGS_ACTIVITY, classLoader,
                "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadPrefsIfExpired();
                if (mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_OOS_CALL_RECORDING, false)) {
                    enableCallRecording((Context)param.thisObject);
                }
            }
        });
    }

    private static void reloadPrefsIfExpired() {
        if (mPrefs != null && SystemClock.uptimeMillis() - mLastPrefReloadMs > 10000) {
            mLastPrefReloadMs = SystemClock.uptimeMillis();
            mPrefs.reload();
            if (DEBUG) log("Expired prefs reloaded");
        }
    }

    private static void enableCallRecording(Context ctx) {
        if (ctx != null) {
            Settings.Global.putInt(ctx.getContentResolver(),
                    "op_voice_recording_supported_by_mcc", 1);
            if (DEBUG) log(ctx.getPackageName() + ": forcing op_voice_recording_supported_by_mcc");
        }
    }
}
