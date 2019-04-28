/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.pie.gravitybox;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialerOOS {
    public static final String PACKAGE_NAME_DIALER = "com.android.dialer";
    public static final String CLASS_DIALER_SETTINGS_ACTIVITY = "com.android.dialer.oneplus.activity.OPDialerSettingsActivity";
    public static final String CLASS_IN_CALL_ACTIVITY = "com.android.incallui.InCallActivity";
    private static final String TAG = "GB:ModDialerOOS";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initDialer(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("initDialer");

        XposedHelpers.findAndHookMethod(CLASS_DIALER_SETTINGS_ACTIVITY, classLoader,
                "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_OOS_CALL_RECORDING, false)) {
                    enableCallRecording((Context)param.thisObject);
                }
            }
        });

        XposedHelpers.findAndHookMethod(CLASS_IN_CALL_ACTIVITY, classLoader,
        "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_OOS_CALL_RECORDING, false)) {
                    enableCallRecording((Context)param.thisObject);
                }
            }
        });
    }

    private static void enableCallRecording(Context ctx) {
        if (ctx != null) {
            Settings.Global.putInt(ctx.getContentResolver(),
                    "op_voice_recording_supported_by_mcc", 1);
            if (DEBUG) log(ctx.getPackageName() + ": forcing op_voice_recording_supported_by_mcc");
        }
    }
}
