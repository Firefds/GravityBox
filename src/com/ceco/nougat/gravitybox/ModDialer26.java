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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import com.ceco.nougat.gravitybox.ledcontrol.QuietHours;

import android.app.Fragment;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialer26 {
    private static final String TAG = "GB:ModDialer26";
    public static final List<String> PACKAGE_NAMES = new ArrayList<String>(Arrays.asList(
            "com.google.android.dialer", "com.android.dialer"));

    private static final String CLASS_DIALTACTS_ACTIVITY = "com.android.dialer.app.DialtactsActivity";
    private static final String CLASS_DIALTACTS_ACTIVITY_GOOGLE = 
            "com.google.android.apps.dialer.extensions.GoogleDialtactsActivity";
    private static final boolean DEBUG = false;

    private static QuietHours mQuietHours;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    static class ClassInfo {
        Class<?> clazz;
        Map<String,String> methods;
        Object extra;
        ClassInfo(Class<?> cls) {
            clazz = cls;
            methods = new HashMap<>();
        }
    }

    private static ClassInfo resolveDialpadFragment(ClassLoader cl) {
        ClassInfo info = null;
        String[] CLASS_NAMES = new String[] {
                "com.android.dialer.app.dialpad.DialpadFragment",
                "com.android.dialer.dialpadview.DialpadFragment"
        };
        String[] METHOD_NAMES = new String[] { "onResume", "playTone" };
        for (String className : CLASS_NAMES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null || !Fragment.class.isAssignableFrom(clazz))
                continue;
            info = new ClassInfo(clazz);
            for (String methodName : METHOD_NAMES) {
                Method m = null;
                if (methodName.equals("onResume")) {
                    m = XposedHelpers.findMethodExactIfExists(clazz, methodName);
                } else if (methodName.equals("playTone")) {
                    for (String realMethodName : new String[] { methodName, "a" }) {
                        m = XposedHelpers.findMethodExactIfExists(clazz, realMethodName,
                            int.class, int.class);
                        if (m != null) break;
                    }
                }
                if (m != null) {
                    info.methods.put(methodName, m.getName());
                }
            }
        }
        return info;
    }

    public static void init(final XSharedPreferences prefs, final XSharedPreferences qhPrefs,
            final ClassLoader classLoader, final String packageName) {
        try {
            XposedHelpers.findAndHookMethod(CLASS_DIALTACTS_ACTIVITY, classLoader, 
                    "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    prefs.reload();
                    if (!prefs.getBoolean(GravityBoxSettings.PREF_KEY_DIALER_SHOW_DIALPAD, false)) return;

                    final String realClassName = param.thisObject.getClass().getName();
                    Method m = null;
                    for (String mn : new String[] { "showDialpadFragment", "e" }) {
                        if (realClassName.equals(CLASS_DIALTACTS_ACTIVITY)) {
                            m = XposedHelpers.findMethodExactIfExists(
                                    param.thisObject.getClass(), mn, boolean.class);
                        } else if (realClassName.equals(CLASS_DIALTACTS_ACTIVITY_GOOGLE)) {
                            m = XposedHelpers.findMethodExactIfExists(
                                    param.thisObject.getClass().getSuperclass(), mn, boolean.class);
                        }
                        if (m != null) break;
                    }
                    if (m == null) {
                        GravityBox.log(TAG, "DialtactsActivity: couldn't identify showDialpadFragment method");
                    } else {
                        m.invoke(param.thisObject, false);
                        if (DEBUG) log("showDialpadFragment() called within " + realClassName);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, "DialtactsActivity: incompatible version of Dialer app", t);
        }

        try {
            final ClassInfo classInfoDialpadFragment = resolveDialpadFragment(classLoader);

            XposedHelpers.findAndHookMethod(classInfoDialpadFragment.clazz,
                    classInfoDialpadFragment.methods.get("onResume"), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param2) throws Throwable {
                    qhPrefs.reload();
                    mQuietHours = new QuietHours(qhPrefs);
                }
            });

            XposedHelpers.findAndHookMethod(classInfoDialpadFragment.clazz,
                    classInfoDialpadFragment.methods.get("playTone"),
                    int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.DIALPAD)) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, "DialpadFragment: incompatible version of Dialer app", t);
        }
    }
}
