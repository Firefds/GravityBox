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

import de.robv.android.xposed.XposedHelpers;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.ContactsContract.Contacts;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

public class Utils {
    private static final String TAG = "GB:Utils";
    @SuppressWarnings("unused")
    private static final boolean DEBUG = false;
    public static final boolean USE_DEVICE_PROTECTED_STORAGE = true;

    @SuppressLint("SdCardPath")
    public static final String AOSP_FORCED_FILE_PATH = USE_DEVICE_PROTECTED_STORAGE ?
            "/data/user_de/0/com.ceco.pie.gravitybox/files/aosp_forced" :
            "/data/data/com.ceco.pie.gravitybox/files/aosp_forced";

    // Device types
    private static final int DEVICE_PHONE = 0;
    private static final int DEVICE_HYBRID = 1;
    private static final int DEVICE_TABLET = 2;

    // Device type reference
    private static int mDeviceType = -1;
    private static Boolean mIsMtkDevice = null;
    private static Boolean mIsXperiaDevice = null;
    private static Boolean mIsMotoXtDevice = null;
    private static Boolean mIsGpeDevice = null;
    private static Boolean mIsExynosDevice = null;
    private static Boolean mIsSamsumgRom = null;
    private static Boolean mIsWifiOnly = null;
    private static String mDeviceCharacteristics = null;
    private static Boolean mIsOxygenOsRom = null;
    private static Boolean mIsFileBasedEncrypted = null;

    // Device features
    private static Boolean mHasGeminiSupport = null;
    private static Boolean mHasTelephonySupport = null;
    private static Boolean mHasVibrator = null;
    private static Boolean mHasFlash = null;
    private static Boolean mHasGPS = null;
    private static Boolean mHasNfc = null;
    private static Boolean mHasCompass;
    private static Boolean mHasProximitySensor = null;
    private static String mDefaultDialerPkgName = null;

    // GB Context
    private static Context mGbContext;

    public static synchronized Context getGbContext(Context context, Configuration config) throws Throwable {
        if (mGbContext == null) {
            mGbContext = context.createPackageContext(GravityBox.PACKAGE_NAME,
                    Context.CONTEXT_IGNORE_SECURITY);
            if (USE_DEVICE_PROTECTED_STORAGE) {
                mGbContext = mGbContext.createDeviceProtectedStorageContext();
            }
        }
        return (config == null ? mGbContext : mGbContext.createConfigurationContext(config));
    }

    public static synchronized Context getGbContext(Context context) throws Throwable {
        return getGbContext(context, null);
    }

    public static File getFilesDir(Context ctx) {
        if (USE_DEVICE_PROTECTED_STORAGE) {
            return ctx.isDeviceProtectedStorage() ?
                    ctx.getFilesDir() : ctx.createDeviceProtectedStorageContext().getFilesDir();
        }
        return ctx.getFilesDir();
    }

    public static File getCacheDir(Context ctx) {
        if (USE_DEVICE_PROTECTED_STORAGE) {
            return ctx.isDeviceProtectedStorage() ?
                    ctx.getCacheDir() : ctx.createDeviceProtectedStorageContext().getCacheDir();
        }
        return ctx.getCacheDir();
    }

    private static int getScreenType(Context con) {
        if (mDeviceType == -1) {
            WindowManager wm = (WindowManager)con.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics outMetrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(outMetrics);
            int shortSize = Math.min(outMetrics.heightPixels, outMetrics.widthPixels);
            int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / outMetrics.densityDpi;
            if (shortSizeDp < 600) {
                // 0-599dp: "phone" UI with a separate status & navigation bar
                mDeviceType =  DEVICE_PHONE;
            } else if (shortSizeDp < 720) {
                // 600-719dp: "phone" UI with modifications for larger screens
                mDeviceType = DEVICE_HYBRID;
            } else {
                // 720dp: "tablet" UI with a single combined status & navigation bar
                mDeviceType = DEVICE_TABLET;
            }
        }
        return mDeviceType;
    }

    public static boolean isPhoneUI(Context con) {
        return (getScreenType(con) == DEVICE_PHONE && !isTablet());
    }

    public static boolean isHybridUI(Context con) {
        return getScreenType(con) == DEVICE_HYBRID;
    }

    public static boolean isTabletUI(Context con) {
        return getScreenType(con) == DEVICE_TABLET;
    }

    public static boolean isTablet() {
        String deviceCharacteristics = getDeviceCharacteristics();
        return (deviceCharacteristics != null
                    && deviceCharacteristics.contains("tablet"));
    }

    public enum MethodState {
        UNKNOWN,
        METHOD_ENTERED,
        METHOD_EXITED
    }

    public enum TriState { DEFAULT, ENABLED, DISABLED }

    /**
     * Pay attention to isAospForced() when called from Zygote!
     */
    public static boolean isMtkDevice() {
        if (mIsMtkDevice == null) {
            mIsMtkDevice = Build.HARDWARE.toLowerCase(Locale.US).matches("^mt[68][1-9][1-9][1-9]$") &&
                !isMotoXtDevice();
        }
        return (mIsMtkDevice && !isAospForced());
    }

    /**
     * Pay attention to isAospForced() when called from Zygote!
     */
    public static boolean isXperiaDevice() {
        if (mIsXperiaDevice == null) {
            mIsXperiaDevice = Build.MANUFACTURER.equalsIgnoreCase("sony")
                && !isMtkDevice() && !isGpeDevice();
        }
        return (mIsXperiaDevice && !isAospForced());
    }

    /**
     * Pay attention to isAospForced() when called from Zygote!
     */
    public static boolean isMotoXtDevice() {
        if (mIsMotoXtDevice == null) {
            String model = Build.MODEL.toLowerCase(Locale.US);
            mIsMotoXtDevice = Build.MANUFACTURER.equalsIgnoreCase("motorola") &&
                    (model.startsWith("xt") ||
                     model.contains("razr") ||
                     model.contains("moto")) &&
                     !isGpeDevice();
        }
        return (mIsMotoXtDevice && !isAospForced());
    }

    public static boolean isGpeDevice() {
        if (mIsGpeDevice != null) return mIsGpeDevice;

        String productName = Build.PRODUCT.toLowerCase(Locale.US);
        mIsGpeDevice = Build.DEVICE.toLowerCase(Locale.US).contains("gpe") || productName.contains("google")
                || productName.contains("ged") || productName.contains("gpe") ||
                productName.contains("aosp");
        return mIsGpeDevice;
    }

    /**
     * NOTE: Always returns false when called from Zygote!
     */
    public static boolean isAospForced() {
        return (new File(AOSP_FORCED_FILE_PATH).exists());
    }

    public static boolean isExynosDevice() {
        if (mIsExynosDevice != null) return mIsExynosDevice;

        mIsExynosDevice = Build.HARDWARE.toLowerCase(Locale.US).contains("smdk");
        return mIsExynosDevice;
    }

    public static boolean isSamsungRom() {
        if (mIsSamsumgRom != null) return mIsSamsumgRom;

        mIsSamsumgRom = new File("/system/framework/com.samsung.device.jar").isFile();
        return mIsSamsumgRom;
    }

    public static boolean isOxygenOsRom() {
        if (mIsOxygenOsRom == null) {
            String version = SystemPropertyProvider.get("ro.oxygen.version", "0");
            mIsOxygenOsRom = version != null && !version.isEmpty() &&  !"0".equals(version); 
        }
        return mIsOxygenOsRom;
    }

    public static boolean isFileBasedEncrypted(Context con) {
        if (mIsFileBasedEncrypted != null) return mIsFileBasedEncrypted;

        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    con.getSystemService(Context.DEVICE_POLICY_SERVICE);
            mIsFileBasedEncrypted = dpm.getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER;
            return mIsFileBasedEncrypted;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            mIsFileBasedEncrypted = null;
            return false;
        }
    }

    public static boolean isUserUnlocked(Context con){
        try {
            UserManager um = (UserManager) con.getSystemService(Context.USER_SERVICE);
            return um.isUserUnlocked();
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return true;
        }
    }

    public static boolean isParanoidRom() {
        return (Build.DISPLAY != null && Build.DISPLAY.startsWith("pa_"));
    }

    public static boolean hasGeminiSupport() {
        if (mHasGeminiSupport != null) return mHasGeminiSupport;

        mHasGeminiSupport = false;
        return mHasGeminiSupport;
    }

    public static boolean isWifiOnly(Context con) {
        // returns true if device doesn't support mobile data (is wifi only)
        if (mIsWifiOnly != null) return mIsWifiOnly;

        try {
            mIsWifiOnly = !con.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY);
            return mIsWifiOnly;
        } catch (Throwable t) {
            mIsWifiOnly = null;
            return false;
        }
    }

    // to be called from settings or other user activities
    public static boolean hasTelephonySupport(Context con) {
        // returns false if device has no phone radio (no telephony support)
        if (mHasTelephonySupport != null) return mHasTelephonySupport;

        try {
            TelephonyManager tm = (TelephonyManager) con.getSystemService(
                Context.TELEPHONY_SERVICE);
            mHasTelephonySupport = (tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE);
            return mHasTelephonySupport;
        } catch (Throwable t) {
            mHasTelephonySupport = null;
            return false;
        }
    }

    // to be called from system context only
    public static boolean hasTelephonySupport() {
        try {
            Resources res = Resources.getSystem();
            return res.getBoolean(res.getIdentifier("config_voice_capable", "bool", "android"));
        } catch (Throwable t) {
            GravityBox.log(TAG, "hasTelephonySupport(): ", t);
            return false;
        }
    }

    public static boolean hasVibrator(Context con) {
        if (mHasVibrator != null) return mHasVibrator;

        try {
            Vibrator v = (Vibrator) con.getSystemService(Context.VIBRATOR_SERVICE);
            mHasVibrator = v.hasVibrator();
            return mHasVibrator;
        } catch (Throwable t) {
            mHasVibrator = null;
            return false;
        }
    }

    public static boolean hasFlash(Context con) {
        if (mHasFlash != null) return mHasFlash;

        try {
            PackageManager pm = con.getPackageManager();
            mHasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
            return mHasFlash;
        } catch (Throwable t) {
            mHasFlash = null;
            return false;
        }
    }

    public static boolean hasGPS(Context con) {
        if (mHasGPS != null) return mHasGPS;

        try {
            PackageManager pm = con.getPackageManager();
            mHasGPS = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
            return mHasGPS;
        } catch (Throwable t) {
            mHasGPS = null;
            return false;
        }
    }

    public static boolean hasNfc(Context con) {
        if (mHasNfc != null) return mHasNfc;

        try {
            PackageManager pm = con.getPackageManager();
            mHasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC);
            return mHasNfc;
        } catch (Throwable t) {
            mHasNfc = null;
            return false;
        }
    }

    public static boolean hasCompass(Context context) {
        if (mHasCompass != null) return mHasCompass;

        try {
            SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            mHasCompass = (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null);
            return mHasCompass;
        } catch (Throwable t) {
            mHasCompass = null;
            return false;
        }
    }

    public static boolean hasProximitySensor(Context con) {
        if (mHasProximitySensor != null) return mHasProximitySensor;

        try {
            PackageManager pm = con.getPackageManager();
            mHasProximitySensor = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY);
            return mHasProximitySensor;
        } catch (Throwable t) {
            mHasProximitySensor = null;
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public static boolean supportsFingerprint(Context ctx) {
        try {
            FingerprintManager fpm = (FingerprintManager) ctx.getSystemService(Context.FINGERPRINT_SERVICE);
            return (fpm != null && fpm.isHardwareDetected());
        } catch (Throwable t) {
            GravityBox.log(TAG, "Error checkin for fingerprint support: ", t);
            return false;
        }
    }

    public static String getDeviceCharacteristics() {
        if (mDeviceCharacteristics != null) return mDeviceCharacteristics;

        mDeviceCharacteristics = SystemPropertyProvider.get("ro.build.characteristics");
        return mDeviceCharacteristics;
    }

    public static boolean shouldAllowMoreVolumeSteps() {
        return !("GT-I9505G".equals(Build.MODEL) &&
                    !isMtkDevice());
    }

    public static String join(String[] stringArray, String separator) {
        String buf = "";
        for (String s : stringArray) {
            if (!buf.isEmpty()) buf += separator;
            buf += s;
        }
        return buf;
    }

    public static boolean isAppInstalled(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static PackageInfo getPackageInfo(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager(); 
            return pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
        } catch (Exception e) {
            return null;
        }
    }

    public static void copyFile(File source, File dest) throws IOException { 
        InputStream input = null; 
        OutputStream output = null; 
        try { 
            input = new FileInputStream(source); 
            output = new FileOutputStream(dest); 
            byte[] buf = new byte[1024]; 
            int bytesRead; 
            while ((bytesRead = input.read(buf)) > 0) { 
                output.write(buf, 0, bytesRead); 
            }
        } finally {
            if (input != null) input.close(); 
            if (output != null) output.close(); 
        } 
    } 

    public static boolean writeAssetToFile(Context context, String assetName, File outFile) {
        try {
            AssetManager am = context.getAssets();
            InputStream input = am.open(assetName);
            FileOutputStream output = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = input.read(buffer)) > 0){
                output.write(buffer, 0, len);
            }
            input.close();
            output.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void performSoftReboot() {
        try {
            Class<?> classSm = XposedHelpers.findClass("android.os.ServiceManager", null);
            Class<?> classIpm = XposedHelpers.findClass("android.os.IPowerManager.Stub", null);
            IBinder b = (IBinder) XposedHelpers.callStaticMethod(
                    classSm, "getService", Context.POWER_SERVICE);
            Object ipm = XposedHelpers.callStaticMethod(classIpm, "asInterface", b);
            XposedHelpers.callMethod(ipm, "crash", "Hot reboot");
        } catch (Throwable t) {
            try {
                SystemPropertyProvider.set("ctl.restart", "surfaceflinger");
                SystemPropertyProvider.set("ctl.restart", "zygote");
            } catch (Throwable t2) {
                GravityBox.log(TAG, t);
                GravityBox.log(TAG, t2);
            }
        }
    }

    public static String intArrayToString(int[] array) {
        String buf = "[";
        for (int item : array) {
            if (buf.length() > 1) buf += ",";
            buf += item;
        }
        buf += "]";
        return buf;
    }

    public static long[] csvToLongArray(String csv) {
        String[] vals = csv.split(",");
        long[] arr = new long[vals.length];
        for (int i=0; i<arr.length; i++) {
            arr[i] = Long.valueOf(vals[i]);
        }
        return arr;
    }

    public static boolean isTimeOfDayInRange(long timeMs, int startMin, int endMin) {
        Calendar c = new GregorianCalendar();

        c.setTimeInMillis(timeMs);
        int timeMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);

        if (startMin == endMin) {
            return false;
        } else if (startMin > endMin) {
            return (timeMin >= startMin || timeMin < endMin);
        } else {
            return (timeMin >= startMin && timeMin < endMin);
        }
    }

    public static int getCurrentUser() {
        try {
            return (int) XposedHelpers.callStaticMethod(ActivityManager.class, "getCurrentUser");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return 0;
        }
    }

    public static UserHandle getUserHandle(int userId) throws Exception {
        Constructor<?> uhConst = XposedHelpers.findConstructorExact(UserHandle.class, int.class);
        return (UserHandle) uhConst.newInstance(userId);
    }

    public static void postToast(final Context ctx, final int msgResId) {
        if (msgResId == 0) return;
        try {
            final String msg = Utils.getGbContext(ctx).getString(msgResId);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
        } catch (Throwable t) {
            GravityBox.log(TAG, "Error showing toast: ", t);
        }
    }

    public static boolean isRTL(Configuration config) {
        return (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
    }

    public static boolean isRTL(Context ctx) {
        Configuration config = ctx.getResources().getConfiguration();
        return isRTL(config);
    }

    public static String getDefaultDialerPackageName(Context ctx) {
        if (mDefaultDialerPkgName == null) {
            try {
                TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
                mDefaultDialerPkgName = tm.getDefaultDialerPackage();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return mDefaultDialerPkgName;
    }

    public static boolean hasFieldOfType(Object o, String name, Class<?> type) {
        Field f = XposedHelpers.findFieldIfExists(o.getClass(), name);
        return (f != null && type.isAssignableFrom(f.getType()));
    }

    public static String getContactLookupKey(Context ctx, Uri uri) {
        String lookupKey = null;
        String[] projection = new String[] { Contacts.LOOKUP_KEY };
        Cursor cursor = ctx.getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null && cursor.moveToNext()) {
            lookupKey = cursor.getString(0);
            cursor.close();
        }
        return lookupKey;
    }

    public static Rect getDisplayCutoutTop(WindowInsets insets) {
        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout != null && cutout.getBoundingRects().size() > 0) {
            Rect r = cutout.getBoundingRects().get(0);
            if (r.left > 0 && r.top <= 100)
                return r;
        }
        return null;
    }
}
