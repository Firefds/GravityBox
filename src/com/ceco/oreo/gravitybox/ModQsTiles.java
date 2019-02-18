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

import com.ceco.oreo.gravitybox.quicksettings.QsDetailItems;
import com.ceco.oreo.gravitybox.quicksettings.QsPanel;
import com.ceco.oreo.gravitybox.quicksettings.QsPanelQuick;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModQsTiles {
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String TAG = "GB:ModQsTile";
    public static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    @SuppressWarnings("unused")
    private static QsPanel mQsPanel;
    private static QsPanelQuick mQsPanelQuick;
    @SuppressWarnings("unused")
    private static QsDetailItems mQsDetailItems;

    public static void initResources(final InitPackageResourcesParam resparam) {
        if (Utils.isXperiaDevice()) {
            resparam.res.setReplacement(PACKAGE_NAME, "integer", "config_maxToolItems", 60);
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("init");

        try {
            mQsPanelQuick = new QsPanelQuick(prefs, classLoader);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            mQsPanel = new QsPanel(prefs, classLoader, mQsPanelQuick);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        if (Utils.isOxygenOsRom()) {
            try {
                mQsDetailItems = new QsDetailItems(classLoader);
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    }
}
