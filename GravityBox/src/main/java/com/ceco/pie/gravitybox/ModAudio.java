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

import com.ceco.pie.gravitybox.ledcontrol.QuietHours;
import com.ceco.pie.gravitybox.ledcontrol.QuietHoursActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModAudio {
    private static final String TAG = "GB:ModAudio";
    private static final String CLASS_AUDIO_SERVICE = "com.android.server.audio.AudioService";
    private static final String CLASS_AUDIO_SYSTEM = "android.media.AudioSystem";
    private static final boolean DEBUG = false;

    private static Context mContext;
    private static boolean mVolForceRingControl;
    private static QuietHours mQh;
    private static AudioManager mAudioManager;
    private static Object mAudioService;
    private static StreamLink mRingNotifVolumesLinked;
    private static StreamLink mRingSystemVolumesLinked;
    private static Integer mNotifStreamAliasOrig;
    private static Integer mSystemStreamAliasOrig;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public enum StreamLink { DEFAULT, LINKED, UNLINKED }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_MEDIA_CONTROL_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOL_FORCE_RING_CONTROL)) {
                    mVolForceRingControl = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_VOL_FORCE_RING_CONTROL, false);
                    if (DEBUG) log("Force ring volume control set to: " + mVolForceRingControl);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOL_LINKED)) {
                    mRingNotifVolumesLinked = StreamLink.valueOf(
                            intent.getStringExtra(GravityBoxSettings.EXTRA_VOL_LINKED));
                    if (DEBUG) log("mRingNotifVolumesLinked set to: " + mRingNotifVolumesLinked);
                    updateStreamVolumeAlias();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOL_RINGER_SYSTEM_LINKED)) {
                    mRingSystemVolumesLinked = StreamLink.valueOf(
                            intent.getStringExtra(GravityBoxSettings.EXTRA_VOL_RINGER_SYSTEM_LINKED));
                    if (DEBUG) log("mRingSystemVolumesLinked set to: " + mRingSystemVolumesLinked);
                    updateStreamVolumeAlias();
                }
            } else if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQh = new QuietHours(intent.getExtras());
            }
        }
    };

    public static void initAndroid(final XSharedPreferences prefs, final XSharedPreferences qhPrefs, final ClassLoader classLoader) {
        try {
            final Class<?> classAudioService = XposedHelpers.findClass(CLASS_AUDIO_SERVICE, classLoader);
            final Class<?> classAudioSystem = XposedHelpers.findClass(CLASS_AUDIO_SYSTEM, classLoader);

            mQh = new QuietHours(qhPrefs);
            mRingNotifVolumesLinked = StreamLink.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_LINK_VOLUMES, "DEFAULT"));
            mRingSystemVolumesLinked = StreamLink.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_LINK_RINGER_SYSTEM_VOLUMES, "DEFAULT"));

            XposedBridge.hookAllConstructors(classAudioService, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    mAudioService = param.thisObject;
                    mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    if (mContext != null) {
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_MEDIA_CONTROL_CHANGED);
                        intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
                        if (DEBUG) log("AudioService constructed. Broadcast receiver registered");
                    }
                }
            });

            if (Utils.isSamsungRom()) {
                Utils.TriState triState = Utils.TriState.valueOf(prefs.getString(
                        GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME, "DEFAULT"));
                if (DEBUG) log(GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME + ": " + triState);
                if (triState == Utils.TriState.DISABLED) {
                    XposedHelpers.findAndHookConstructor("android.media.AudioManager", classLoader, Context.class,
                            new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object objService = XposedHelpers.callMethod(param.thisObject, "getService");
                            Context mApplicationContext = (Context) XposedHelpers.getObjectField(param.thisObject,
                                    "mApplicationContext");
                            if (objService != null && mApplicationContext != null) {
                                XposedHelpers.callMethod(param.thisObject, "disableSafeMediaVolume");
                            }
                        }
                    });
                }
            }
            
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                XposedHelpers.findAndHookMethod(classAudioService, "createStreamStates",
                        new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int[] maxStreamVolume = (int[])
                                XposedHelpers.getStaticObjectField(classAudioService, "MAX_STREAM_VOLUME");
                        maxStreamVolume[AudioManager.STREAM_MUSIC] = prefs.getInt(
                                GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS_VALUE, 30);
                        if (DEBUG) log("createStreamStates: MAX_STREAM_VOLUME for music stream set to " +
                                maxStreamVolume[AudioManager.STREAM_MUSIC]);
                        int [] defaultStreamVolume = (int[])
                                XposedHelpers.getStaticObjectField(classAudioSystem, "DEFAULT_STREAM_VOLUME");
                        defaultStreamVolume[AudioManager.STREAM_MUSIC] = maxStreamVolume[AudioManager.STREAM_MUSIC] / 3;
                        if (DEBUG) log("createStreamStates: DEFAULT_STREAM_VOLUME for music stream set to " +
                                defaultStreamVolume[AudioManager.STREAM_MUSIC]);
                    }
                });

                XposedHelpers.findAndHookMethod(classAudioService, "onConfigureSafeVolume",
                        boolean.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int safeIndex = Math.round(prefs.getInt(
                                GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS_VALUE, 30) * (2f / 3f)) * 10;
                        XposedHelpers.setIntField(param.thisObject, "mSafeMediaVolumeIndex", safeIndex);
                        if (DEBUG)
                            log("onConfigureSafeVolume: mSafeMediaVolumeIndex set to " + safeIndex);
                    }
                });
            }

            mVolForceRingControl = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_VOL_FORCE_RING_CONTROL, false);
            XposedHelpers.findAndHookMethod(classAudioService, "getActiveStreamType",
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (mVolForceRingControl) {
                        int activeStreamType = (int) param.getResult();
                        if (activeStreamType == AudioManager.STREAM_MUSIC && !isMusicActive()) {
                            param.setResult(AudioManager.STREAM_RING);
                            if (DEBUG) log("getActiveStreamType: Forcing STREAM_RING");
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classAudioService, "playSoundEffectVolume",
                    int.class, float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    if (mQh.isSystemSoundMuted(QuietHours.SystemSound.TOUCH)) {
                        param.setResult(false);
                    }
                } 
            });

            XposedHelpers.findAndHookMethod(classAudioService, "updateStreamVolumeAlias",
                    boolean.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    if ((Boolean) XposedHelpers.callMethod(param.thisObject, "isPlatformVoice")) {
                        int[] streamVolumeAlias = (int[]) XposedHelpers.getObjectField(param.thisObject, "mStreamVolumeAlias");
                        if (mNotifStreamAliasOrig == null) mNotifStreamAliasOrig = streamVolumeAlias[AudioManager.STREAM_NOTIFICATION];
                        if (mSystemStreamAliasOrig == null) mSystemStreamAliasOrig = streamVolumeAlias[AudioManager.STREAM_SYSTEM];
                        streamVolumeAlias[AudioManager.STREAM_NOTIFICATION] = getNotifStreamAlias();
                        if (DEBUG) log("AudioService mStreamVolumeAlias updated, STREAM_NOTIFICATION set to: " +
                                    streamVolumeAlias[AudioManager.STREAM_NOTIFICATION]);
                        streamVolumeAlias[AudioManager.STREAM_SYSTEM] = getSystemStreamAlias();
                        if (DEBUG) log("AudioService mStreamVolumeAlias updated, STREAM_SYSTEM set to: " +
                                streamVolumeAlias[AudioManager.STREAM_SYSTEM]);
                    }
                }
            });
        } catch(Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static int getNotifStreamAlias() {
        switch (mRingNotifVolumesLinked) {
            default:
            case DEFAULT: return mNotifStreamAliasOrig == null ?
                    AudioManager.STREAM_RING : mNotifStreamAliasOrig;
            case LINKED: return AudioManager.STREAM_RING;
            case UNLINKED: return AudioManager.STREAM_NOTIFICATION;
        }
    }

    private static int getSystemStreamAlias() {
        switch (mRingSystemVolumesLinked) {
            default:
            case DEFAULT: return mSystemStreamAliasOrig == null ?
                    AudioManager.STREAM_RING : mSystemStreamAliasOrig;
            case LINKED: return AudioManager.STREAM_RING;
            case UNLINKED: return AudioManager.STREAM_SYSTEM;
        }
    }

    private static AudioManager getAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }
        return mAudioManager;
    }

    private static boolean isMusicActive() {
        try {
            // check local
            if (getAudioManager().isMusicActive())
                return true;
            // check remote
            if ((boolean) XposedHelpers.callMethod(getAudioManager(), "isMusicActiveRemotely"))
                return true;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
        return false;
    }

    private static void updateStreamVolumeAlias() {
        if (mAudioService == null) {
            if (DEBUG) log("updateStreamVolumeAlias: AudioService is null");
            return;
        }

        try {
            XposedHelpers.callMethod(mAudioService, "updateStreamVolumeAlias", true, "AudioService");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }
}
