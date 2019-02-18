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
package com.ceco.oreo.gravitybox.quicksettings;

import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.GravityBoxSettings;

import android.content.Context;
import android.content.Intent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class QsPanelQuick {
    private static final String TAG = "GB:QsPanelQuick";
    private static final boolean DEBUG = false;

    private static final String CLASS_QS_PANEL_QUICK = "com.android.systemui.qs.QuickQSPanel";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private int mNumTiles;
    private Object mPanel;

    public QsPanelQuick(XSharedPreferences prefs, ClassLoader classLoader) {
        initPreferences(prefs);
        createHooks(classLoader);

        if (DEBUG) log("QsPanelQuick wrapper created");
    }

    private void initPreferences(XSharedPreferences prefs) {
        mNumTiles = Integer.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_TILES_PER_HEADER, "0"));
    }

    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_COLS_HEADER)) {
            mNumTiles = intent.getIntExtra(GravityBoxSettings.EXTRA_QS_COLS_HEADER, 0);
            updateMaxTiles();
        }
    }

    private void createHooks(ClassLoader cl) {
        try {
            XposedBridge.hookAllConstructors(XposedHelpers.findClass(
                    CLASS_QS_PANEL_QUICK, cl), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mPanel = param.thisObject;
                    if (DEBUG) log("QuickQSPanel constructed");
                }
            });
    
            XposedHelpers.findAndHookMethod(CLASS_QS_PANEL_QUICK, cl,
                    "getNumQuickTiles", Context.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mNumTiles > 0) {
                        param.setResult(mNumTiles);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void updateMaxTiles() {
        try {
            if (mPanel != null) {
                Object tunable = XposedHelpers.getObjectField(mPanel, "mNumTiles");
                XposedHelpers.callMethod(tunable, "onTuningChanged",
                        "sysui_qqs_count", String.valueOf(mNumTiles));
                if (DEBUG) log("Number of header tiles updated");
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }
}
