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

package com.ceco.nougat.gravitybox;

import android.content.Intent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModActivityManager {
    private static final String TAG = "GB:ModActivityManager";
    public static final String CLASS_AM_SERVICE = "com.android.server.am.ActivityManagerService";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initAndroid(final ClassLoader classLoader) {
        if (DEBUG) log("init");

        try {
            final Class<?> classAms = XposedHelpers.findClass(CLASS_AM_SERVICE, classLoader);
            XposedBridge.hookAllMethods(classAms, "checkBroadcastFromSystem", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    final Intent intent = getIntentFromParamArgs(param.args);
                    if (intent != null && intent.getAction() != null && 
                            intent.getAction().startsWith("gravitybox.")) {
                        if (DEBUG) log("Muting yelling about non-protected broadcast for: " +
                                intent.getAction());
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static Intent getIntentFromParamArgs(Object[] args) {
        for (Object o : args) {
            if (o instanceof Intent)
                return (Intent) o;
        }
        return null;
    }
}