/*
 * Copyright (C) 2013 rovo89@xda
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

import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModVolumeKeySkipTrack {
    private static final String TAG = "GB:ModVolumeKeySkipTrack";
    private static final String CLASS_PHONE_WINDOW_MANAGER = "com.android.server.policy.PhoneWindowManager";
    private static final String CLASS_IWINDOW_MANAGER = "android.view.IWindowManager";
    private static final String CLASS_WINDOW_MANAGER_FUNCS = "com.android.server.policy.WindowManagerPolicy.WindowManagerFuncs";
    private static final boolean DEBUG = false;


    private static boolean mIsLongPress = false;
    private static boolean mAllowSkipTrack;
    private static AudioManager mAudioManager;
    private static PowerManager mPowerManager;
    private static String mVolumeRockerWakeMode;
    private static boolean mVolumeRockerWakeAllowMusic;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(GravityBoxSettings.ACTION_PREF_MEDIA_CONTROL_CHANGED) &&
                    intent.hasExtra(GravityBoxSettings.EXTRA_VOL_MUSIC_CONTROLS)) {
                mAllowSkipTrack = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VOL_MUSIC_CONTROLS, false);
                if (DEBUG) log("mAllowSkipTrack=" + mAllowSkipTrack);
            } else if (action.equals(GravityBoxSettings.ACTION_PREF_VOLUME_ROCKER_WAKE_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOLUME_ROCKER_WAKE)) {
                    mVolumeRockerWakeMode = intent.getStringExtra(GravityBoxSettings.EXTRA_VOLUME_ROCKER_WAKE);
                    if (DEBUG) log("mVolumeRockerWakeMode=" + mVolumeRockerWakeMode);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOLUME_ROCKER_WAKE_ALLOW_MUSIC)) {
                    mVolumeRockerWakeAllowMusic = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_VOLUME_ROCKER_WAKE_ALLOW_MUSIC, false);
                    if (DEBUG) log("mVolumeRockerWakeAllowMusic=" + mVolumeRockerWakeAllowMusic);
                }
            }
        }
    };

    static void initAndroid(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            if (DEBUG) log("init");

            mAllowSkipTrack = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOL_MUSIC_CONTROLS, false);
            mVolumeRockerWakeMode = prefs.getString(GravityBoxSettings.PREF_KEY_VOLUME_ROCKER_WAKE, "default");
            mVolumeRockerWakeAllowMusic =  prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_ROCKER_WAKE_ALLOW_MUSIC, false);
            if (DEBUG) log("mAllowSkipTrack=" + mAllowSkipTrack +
                    "; mVolumeRockerWakeMode=" + mVolumeRockerWakeMode +
                    "; mVolumeRockerWakeAllowMusic=" + mVolumeRockerWakeAllowMusic);

            XposedHelpers.findAndHookMethod(CLASS_PHONE_WINDOW_MANAGER, classLoader, "init",
                    Context.class, CLASS_IWINDOW_MANAGER, CLASS_WINDOW_MANAGER_FUNCS,
                    handleConstructPhoneWindowManager);

            XposedHelpers.findAndHookMethod(CLASS_PHONE_WINDOW_MANAGER, classLoader,
                    "interceptKeyBeforeQueueing", KeyEvent.class, int.class, handleInterceptKeyBeforeQueueing);
        } catch (Throwable t) { 
            GravityBox.log(TAG, t); 
        }
    }

    private static XC_MethodHook handleInterceptKeyBeforeQueueing = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (!mAllowSkipTrack) return;

            final KeyEvent event = (KeyEvent) param.args[0];
            final int keyCode = event.getKeyCode();
            initManagers((Context) XposedHelpers.getObjectField(param.thisObject, "mContext"));
            if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_UP) &&
                    (event.getFlags() & KeyEvent.FLAG_FROM_SYSTEM) != 0 &&
                    !mPowerManager.isInteractive() &&
                    mAudioManager != null && isMusicActive()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    mIsLongPress = false;
                    handleVolumeLongPress(param.thisObject, keyCode);
                } else {
                    handleVolumeLongPressAbort(param.thisObject);
                    if (!mIsLongPress) {
                        if (shouldTriggerWakeUp()) {
                            wakeUp();
                        } else {
                            mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                                keyCode == KeyEvent.KEYCODE_VOLUME_UP ?
                                AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                        }
                    }
                }
                param.setResult(0);
            }
        }
    };

    private static XC_MethodHook handleConstructPhoneWindowManager = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(final MethodHookParam param) {
            Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(GravityBoxSettings.ACTION_PREF_MEDIA_CONTROL_CHANGED);
            intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOLUME_ROCKER_WAKE_CHANGED);
            ctx.registerReceiver(mBroadcastReceiver, intentFilter);

            /**
             * When a volumeup-key longpress expires, skip songs based on key press
             */
            Runnable mVolumeUpLongPress = () -> {
                // set the long press flag to true
                mIsLongPress = true;

                // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
                sendMediaButtonEvent(param.thisObject, KeyEvent.KEYCODE_MEDIA_NEXT);
            };

            /**
             * When a volumedown-key longpress expires, skip songs based on key press
             */
            Runnable mVolumeDownLongPress = () -> {
                // set the long press flag to true
                mIsLongPress = true;

                // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
                sendMediaButtonEvent(param.thisObject, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            };

            setAdditionalInstanceField(param.thisObject, "mVolumeUpLongPress", mVolumeUpLongPress);
            setAdditionalInstanceField(param.thisObject, "mVolumeDownLongPress", mVolumeDownLongPress);
        }
    };

    private static void initManagers(Context ctx) {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        }
        if (mPowerManager == null) {
            mPowerManager = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        }
    }

    private static boolean isMusicActive() {
        // check local
        if (mAudioManager.isMusicActive())
            return true;
        // check remote
        try {
            if ((boolean) XposedHelpers.callMethod(mAudioManager, "isMusicActiveRemotely"))
                return true;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
        return false;
    }

    private static void sendMediaButtonEvent(Object phoneWindowManager, int code) {
        long eventtime = SystemClock.uptimeMillis();
        Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent keyEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        dispatchMediaButtonEvent(keyEvent);

        keyEvent = KeyEvent.changeAction(keyEvent, KeyEvent.ACTION_UP);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        dispatchMediaButtonEvent(keyEvent);
    }

    private static void dispatchMediaButtonEvent(KeyEvent keyEvent) {
        try {
            mAudioManager.dispatchMediaKeyEvent(keyEvent);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void handleVolumeLongPress(Object phoneWindowManager, int keycode) {
        Handler mHandler = (Handler) getObjectField(phoneWindowManager, "mHandler");
        Runnable mVolumeUpLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeUpLongPress");
        Runnable mVolumeDownLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeDownLongPress");

        mHandler.postDelayed(keycode == KeyEvent.KEYCODE_VOLUME_UP ? mVolumeUpLongPress :
            mVolumeDownLongPress, ViewConfiguration.getLongPressTimeout());
    }

    private static void handleVolumeLongPressAbort(Object phoneWindowManager) {
        Handler mHandler = (Handler) getObjectField(phoneWindowManager, "mHandler");
        Runnable mVolumeUpLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeUpLongPress");
        Runnable mVolumeDownLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeDownLongPress");

        mHandler.removeCallbacks(mVolumeUpLongPress);
        mHandler.removeCallbacks(mVolumeDownLongPress);
    }

    private static boolean shouldTriggerWakeUp() {
        return ("enabled".equals(mVolumeRockerWakeMode) && mVolumeRockerWakeAllowMusic);
    }

    private static void wakeUp() {
        long ident = Binder.clearCallingIdentity();
        try {
            XposedHelpers.callMethod(mPowerManager, "wakeUp", SystemClock.uptimeMillis());
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }
}
