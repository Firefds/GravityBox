/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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

import java.io.File;

import com.ceco.nougat.gravitybox.managers.FingerprintLauncher;

import android.os.Build;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GravityBox implements IXposedHookZygoteInit, IXposedHookInitPackageResources, IXposedHookLoadPackage {
    public static final String PACKAGE_NAME = GravityBox.class.getPackage().getName();
    public static String MODULE_PATH = null;
    private static final File prefsFileProt = new File("/data/user_de/0/com.ceco.nougat.gravitybox/shared_prefs/com.ceco.nougat.gravitybox_preferences.xml");
    private static final File qhPrefsFileProt = new File("/data/user_de/0/com.ceco.nougat.gravitybox/shared_prefs/quiet_hours.xml");
    private static final File uncPrefsFileProt = new File("/data/user_de/0/com.ceco.nougat.gravitybox/shared_prefs/ledcontrol.xml");
    private static XSharedPreferences prefs;
    private static XSharedPreferences qhPrefs;
    private static XSharedPreferences uncPrefs;
    private static boolean LOG_ERRORS;

    public static void log(String tag, String message, Throwable t) {
        if (LOG_ERRORS) {
            if (message != null) {
                XposedBridge.log(tag + ": " + message);
            }
            if (t != null) {
                XposedBridge.log(t);
            }
        }
    }

    public static void log(String tag, String message) {
        log(tag, message, null);
    }

    public static void log(String tag, Throwable t) {
        log(tag, null, t);
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
        if (Utils.USE_DEVICE_PROTECTED_STORAGE) {
            prefs = new XSharedPreferences(prefsFileProt);
            uncPrefs = new XSharedPreferences(uncPrefsFileProt);
            qhPrefs = new XSharedPreferences(qhPrefsFileProt);
        } else {
            prefs = new XSharedPreferences(PACKAGE_NAME);
            uncPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "ledcontrol");
            qhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
        }
        LOG_ERRORS = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LOG_ERRORS, false);

        if (!startupParam.startsSystemServer) return;

        XposedBridge.log("GB:Hardware: " + Build.HARDWARE);
        XposedBridge.log("GB:Product: " + Build.PRODUCT);
        XposedBridge.log("GB:Device manufacturer: " + Build.MANUFACTURER);
        XposedBridge.log("GB:Device brand: " + Build.BRAND);
        XposedBridge.log("GB:Device model: " + Build.MODEL);
        XposedBridge.log("GB:Device type: " + (Utils.isTablet() ? "tablet" : "phone"));
        XposedBridge.log("GB:Is MTK device: " + Utils.isMtkDevice());
        XposedBridge.log("GB:Is Xperia device: " + Utils.isXperiaDevice());
        XposedBridge.log("GB:Is Moto XT device: " + Utils.isMotoXtDevice());
        XposedBridge.log("GB:Is OxygenOS ROM: " + Utils.isOxygenOsRom());
        XposedBridge.log("GB:Has Lenovo custom UI: " + Utils.hasLenovoCustomUI());
        if (Utils.hasLenovoCustomUI()) {
            XposedBridge.log("GB:Lenovo UI is VIBE: " + Utils.hasLenovoVibeUI());
            XposedBridge.log("GB:Lenovo ROM is ROW: " + Utils.isLenovoROW());
        }
        XposedBridge.log("GB:Has telephony support: " + Utils.hasTelephonySupport());
        XposedBridge.log("GB:Has Gemini support: " + Utils.hasGeminiSupport());
        XposedBridge.log("GB:Android SDK: " + Build.VERSION.SDK_INT);
        XposedBridge.log("GB:Android Release: " + Build.VERSION.RELEASE);
        XposedBridge.log("GB:ROM: " + Build.DISPLAY);
        XposedBridge.log("GB:Error logging: " + LOG_ERRORS);

        if (Build.VERSION.SDK_INT < 24 || Build.VERSION.SDK_INT > 25) {
            XposedBridge.log("!!! GravityBox you are running is not designed for "
                    + "Android SDK " + Build.VERSION.SDK_INT + " !!!");
            return;
        }

        SystemWideResources.initResources(prefs);

        // Common
        ModInputMethod.initZygote(prefs);
        PhoneWrapper.initZygote(prefs);
        ModTelephony.initZygote(prefs);
    }

    @Override
    public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
        if (Build.VERSION.SDK_INT < 24 || Build.VERSION.SDK_INT > 25) {
            return;
        }

        if (resparam.packageName.equals(ModStatusBar.PACKAGE_NAME)) {
            ModStatusBar.initResources(prefs, resparam);
        }

        if (resparam.packageName.equals(ModSettings.PACKAGE_NAME)) {
            ModSettings.initPackageResources(prefs, resparam);
        }

        if (resparam.packageName.equals(ModLockscreen.PACKAGE_NAME)) {
            ModLockscreen.initResources(prefs, resparam);
        }

        if (resparam.packageName.equals(ModVolumePanel.PACKAGE_NAME)) {
            ModVolumePanel.initResources(prefs, resparam);
        }

        if (resparam.packageName.equals(ModQsTiles.PACKAGE_NAME) &&
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_ENABLE, false)) {
            ModQsTiles.initResources(resparam);
        }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (Build.VERSION.SDK_INT < 24 || Build.VERSION.SDK_INT > 25) {
            return;
        }

        if (lpparam.packageName.equals("android") &&
                lpparam.processName.equals("android")) {
            XposedBridge.log("GB:Is AOSP forced: " + Utils.isAospForced());
            ModVolumeKeySkipTrack.initAndroid(prefs, lpparam.classLoader);
            ModHwKeys.initAndroid(prefs, lpparam.classLoader);
            ModExpandedDesktop.initAndroid(prefs, lpparam.classLoader);
            ModAudio.initAndroid(prefs, qhPrefs, lpparam.classLoader);
            PermissionGranter.initAndroid(lpparam.classLoader);
            ModLowBatteryWarning.initAndroid(prefs, lpparam.classLoader);
            ModDisplay.initAndroid(prefs, lpparam.classLoader);
            ConnectivityServiceWrapper.initAndroid(lpparam.classLoader);
            ModViewConfig.initAndroid(prefs, lpparam.classLoader);
            ModPower.initAndroid(prefs, lpparam.classLoader);
            ModLedControl.initAndroid(prefs, uncPrefs, qhPrefs, lpparam.classLoader);
            ModTrustManager.initAndroid(prefs, lpparam.classLoader);
            ModPowerMenu.initAndroid(prefs, lpparam.classLoader);
            ModFingerprint.initAndroid(prefs, lpparam.classLoader);
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_FINGERPRINT_LAUNCHER_ENABLE, false)) {
                FingerprintLauncher.initAndroid(lpparam.classLoader);
            }
            if (Build.VERSION.SDK_INT >= 25) {
                ModActivityManager.initAndroid(lpparam.classLoader);
            }
        }

        if (lpparam.packageName.equals(SystemPropertyProvider.PACKAGE_NAME)) {
            SystemPropertyProvider.init(prefs, qhPrefs, lpparam.classLoader);
        }

        // Common
        if (lpparam.packageName.equals(ModLowBatteryWarning.PACKAGE_NAME)) {
            ModLowBatteryWarning.init(prefs, qhPrefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModClearAllRecents.PACKAGE_NAME)) {
            ModClearAllRecents.init(prefs, lpparam.classLoader);
        }

        if (ModDialer.PACKAGE_NAMES.contains(lpparam.packageName) && !Utils.isOxygenOsRom()) {
            if (lpparam.appInfo.targetSdkVersion >= 26) {
                ModDialer26.init(prefs, qhPrefs, lpparam.classLoader, lpparam.packageName);
            } else if (lpparam.appInfo.targetSdkVersion == 25) {
                ModDialer25.init(prefs, qhPrefs, lpparam.classLoader, lpparam.packageName);
            } else if (lpparam.appInfo.targetSdkVersion == 24) {
                ModDialer24.init(prefs, qhPrefs, lpparam.classLoader, lpparam.packageName);
            } else {
                ModDialer.init(prefs, qhPrefs, lpparam.classLoader, lpparam.packageName);
            }
        }

        if (lpparam.packageName.equals(ModQsTiles.PACKAGE_NAME) &&
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_ENABLE, false)) {
            ModQsTiles.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModStatusbarColor.PACKAGE_NAME)) {
            ModStatusbarColor.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModStatusBar.PACKAGE_NAME)) {
            ModStatusBar.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModSettings.PACKAGE_NAME)) {
            ModSettings.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModVolumePanel.PACKAGE_NAME)) {
            ModVolumePanel.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModPieControls.PACKAGE_NAME)) {
            ModPieControls.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModNavigationBar.PACKAGE_NAME)
                && prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_OVERRIDE, false)) {
            ModNavigationBar.init(prefs, lpparam.classLoader);
        }

        if (!Utils.hasLenovoVibeUI() &&
                lpparam.packageName.equals(ModLockscreen.PACKAGE_NAME)) {
            ModLockscreen.init(prefs, qhPrefs, lpparam.classLoader);
        }

        // TODO: launcher tweaks? probably not...
        //if (ModLauncher.PACKAGE_NAMES.contains(lpparam.packageName)) {
        //    ModLauncher.init(prefs, lpparam.classLoader);
        //}

        if (lpparam.packageName.equals(ModSmartRadio.PACKAGE_NAME) &&
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false)) {
            ModSmartRadio.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModDownloadProvider.PACKAGE_NAME)) {
            ModDownloadProvider.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModRinger.PACKAGE_NAME)) {
            ModRinger.init(prefs, qhPrefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModLedControl.PACKAGE_NAME_SYSTEMUI)) {
            ModLedControl.init(prefs, lpparam.classLoader);
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_MASTER_SWITCH, false)) {
                ModLedControl.initHeadsUp(prefs, uncPrefs, lpparam.classLoader);
            }
        }

        if (lpparam.packageName.equals(ModTelecom.PACKAGE_NAME)) {
            ModTelecom.init(prefs, lpparam.classLoader);
        }

        if (Utils.isOxygenOsRom() &&
                lpparam.packageName.equals((ModDialerOOS.PACKAGE_NAME_IN_CALL_UI))) {
            ModDialerOOS.initInCallUi(prefs, lpparam.classLoader);
        }
    }
}

