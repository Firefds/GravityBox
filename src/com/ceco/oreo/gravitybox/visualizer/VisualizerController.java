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
package com.ceco.oreo.gravitybox.visualizer;

import java.util.ArrayList;
import java.util.List;

import com.ceco.oreo.gravitybox.BroadcastSubReceiver;
import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.GravityBoxSettings;
import com.ceco.oreo.gravitybox.ModLockscreen;
import com.ceco.oreo.gravitybox.ModStatusBar;
import com.ceco.oreo.gravitybox.ModStatusBar.StatusBarStateChangedListener;
import com.ceco.oreo.gravitybox.managers.BatteryInfoManager;
import com.ceco.oreo.gravitybox.managers.SysUiManagers;
import com.ceco.oreo.gravitybox.managers.BatteryInfoManager.BatteryData;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.audiofx.Visualizer;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.graphics.Palette;
import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class VisualizerController implements StatusBarStateChangedListener,
                                             BatteryInfoManager.BatteryStatusListener,
                                             BroadcastSubReceiver,
                                             Palette.PaletteAsyncListener,
                                             Visualizer.OnDataCaptureListener {
    private static final String TAG = "GB:VisualizerController";
    private static final boolean DEBUG = false;

    private static final String CLASS_STATUSBAR_WINDOW_VIEW = "com.android.systemui.statusbar.phone.StatusBarWindowView";
    private static final String CLASS_NAVIGATION_BAR_VIEW = "com.android.systemui.statusbar.phone.NavigationBarView";
    private static final String CLASS_NAVIGATION_BAR_INFLATER_VIEW = "com.android.systemui.statusbar.phone.NavigationBarInflaterView";
    private static final String CLASS_LIGHT_BAR_CONTROLLER = "com.android.systemui.statusbar.phone.LightBarController";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    interface Listener {
        void initPreferences(XSharedPreferences prefs);
        void onPreferenceChanged(Intent intent);
        void onCreateView(ViewGroup parent) throws Throwable;
        void onActiveStateChanged(boolean active);
        void onMediaMetaDataUpdated(MediaMetadata md, Bitmap artwork);
        void onUserActivity();
        void onStatusBarStateChanged(int oldState, int newState);
        void onBatteryStatusChanged(BatteryData batteryData);
        void onColorUpdated(int color);
        void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate);
        void setVerticalLeft(boolean left);
        void setLight(boolean light);
        boolean isEnabled();
        boolean isAttached();
    }

    private XSharedPreferences mPrefs;
    private List<Listener> mListeners;
    private boolean mPlaying = false;
    private boolean mActive = false;
    private boolean mIsScreenOn = true;
    private boolean mDynamicColorEnabled;
    private int mDefaultColor;
    private int mCurrentColor;
    private int mOpacity;
    private Visualizer mVisualizer;
    private Handler mHandler;

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                log("+++ mLinkVisualizer run()");
            }

            if (mVisualizer != null) {
                mUnlinkVisualizer.run();
            }

            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                GravityBox.log(TAG, "error initializing visualizer", e);
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(66);
            mVisualizer.setDataCaptureListener(VisualizerController.this, Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);

            if (DEBUG) {
                log("--- mLinkVisualizer run()");
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                log("+++ mUnlinkVisualizer run(), mVisualizer: " + mVisualizer);
            }

            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
            }

            if (DEBUG) {
                log("--- mUnlinkVisualizer run()");
            }
        }
    };

    private final Runnable mAsyncUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            AsyncTask.execute(mUnlinkVisualizer);
        }
    };

    public VisualizerController(ClassLoader cl, XSharedPreferences prefs) {
        mPrefs = prefs;
        mListeners = new ArrayList<>();
        mDynamicColorEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DYNAMIC_COLOR, true);
        mDefaultColor = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_COLOR, Color.WHITE);
        mOpacity = Math.round(255f * ((float)prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_OPACITY, 50)/100f));
        mCurrentColor = mDynamicColorEnabled ? Color.TRANSPARENT : mDefaultColor;

        createHooks(cl);
    }

    private void createHooks(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(CLASS_STATUSBAR_WINDOW_VIEW, cl,
                    "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    ViewGroup parent = (ViewGroup) param.thisObject;
                    addListener(parent, new LockscreenVisualizerLayout(parent.getContext()));
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(ModStatusBar.CLASS_STATUSBAR, cl,
                    "updateMediaMetaData", boolean.class, boolean.class,
                        new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    updateMediaMetaData(param.thisObject, (boolean) param.args[0]);
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(ModLockscreen.CLASS_KGVIEW_MEDIATOR, cl,
                    "userActivity", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    onUserActivity();
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_NAVIGATION_BAR_VIEW, cl,
                    "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    ViewGroup parent = (ViewGroup) param.thisObject;
                    addListener(parent, new NavbarVisualizerLayout(parent.getContext()));
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_NAVIGATION_BAR_INFLATER_VIEW, cl,
                    "setAlternativeOrder", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    onNavbarSetAlternativeOrder((boolean) param.args[0]);
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_LIGHT_BAR_CONTROLLER, cl,
                    "updateNavigation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    onNavbarUpdateNavigation(XposedHelpers.getBooleanField(
                            param.thisObject, "mNavigationLight"));
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void addListener(ViewGroup parent, Listener l) throws Throwable {
        // cleanup stalled listeners
        for (int i = mListeners.size()-1; i >= 0; i--) {
            if (!mListeners.get(i).isAttached()) {
                mListeners.remove(i);
                if (DEBUG) log("Removed stalled listener: " + l);
            }
        }
        // add new
        l.onCreateView(parent);
        l.initPreferences(mPrefs);
        l.onColorUpdated(Color.argb(mOpacity, Color.red(mCurrentColor),
                Color.green(mCurrentColor), Color.blue(mCurrentColor)));
        mListeners.add(l);
        if (DEBUG) log("Current number of listeners: " + mListeners.size());
        updateActiveState(true);
    }

    private void postRunnable(Runnable r, long delayMs) {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        mHandler.postDelayed(r, delayMs);
    }

    private void removeRunnable(Runnable r) {
        if (mHandler != null) {
            mHandler.removeCallbacks(r);
        }
    }

    private void updateMediaMetaData(Object sb, boolean metaDataChanged) {
        MediaController mc = (MediaController) XposedHelpers
                .getObjectField(sb, "mMediaController");
        mPlaying = mc != null && mc.getPlaybackState() != null &&
                mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;

        final boolean wasActive = mActive;
        updateActiveState();
        metaDataChanged |= (mActive && !wasActive);

        if (mPlaying) {
            if (SysUiManagers.BatteryInfoManager != null) {
                SysUiManagers.BatteryInfoManager.registerListener(this);
            }
            if (metaDataChanged) {
                Bitmap artworkBitmap = null;
                MediaMetadata md = mc.getMetadata();
                if (md != null) {
                    artworkBitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    if (artworkBitmap == null) {
                        artworkBitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    }
                    if (DEBUG)
                        log("updateMediaMetaData: artwork change detected; bitmap=" + artworkBitmap);
                }
                if (mDynamicColorEnabled) {
                    if (artworkBitmap != null) { 
                        Palette.from(artworkBitmap).generate(this);
                    } else {
                        notifyColorUpdated(mDefaultColor);
                    }
                }
                for (Listener l : mListeners) {
                    l.onMediaMetaDataUpdated(md, artworkBitmap);
                }
            }
        } else {
            if (SysUiManagers.BatteryInfoManager != null) {
                SysUiManagers.BatteryInfoManager.unregisterListener(this);
            }
        }
    }

    private void updateActiveState() {
        updateActiveState(false);
    }

    private void updateActiveState(boolean forceNotifyListeners) {
        boolean atLeastOneListenerEnabled = false;
        for (Listener l : mListeners) {
            atLeastOneListenerEnabled |= l.isEnabled();
        }
        boolean newActive = mPlaying && mIsScreenOn && !isPowerSaving() && atLeastOneListenerEnabled;
        if (newActive != mActive) {
            mActive = newActive;
            removeRunnable(mAsyncUnlinkVisualizer);
            if (mActive) {
                AsyncTask.execute(mLinkVisualizer);
            } else {
                postRunnable(mAsyncUnlinkVisualizer, 800);
            }
            forceNotifyListeners = true;
        }
        if (forceNotifyListeners) {
            for (Listener l : mListeners) {
                l.onActiveStateChanged(mActive);
            }
        }
    }

    private boolean isPowerSaving() {
        if (SysUiManagers.BatteryInfoManager != null) {
            BatteryData bd = SysUiManagers.BatteryInfoManager.getCurrentBatteryData();
            return (bd != null && bd.isPowerSaving);
        }
        return false;
    }

    private void onUserActivity() {
        for (Listener l : mListeners) {
            l.onUserActivity();
        }
    }

    private void onNavbarSetAlternativeOrder(boolean navbarIsRight) {
        for (Listener l : mListeners) {
            l.setVerticalLeft(!navbarIsRight);
        }
    }

    private void onNavbarUpdateNavigation(boolean navbarIsLight) {
        for (Listener l : mListeners) {
            l.setLight(navbarIsLight);
        }
    }

    @Override
    public void onStatusBarStateChanged(int oldState, int newState) {
        for (Listener l : mListeners) {
            l.onStatusBarStateChanged(oldState, newState);
        }
        updateActiveState();
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        updateActiveState();
        for (Listener l : mListeners) {
            l.onBatteryStatusChanged(batteryData);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            mIsScreenOn = true;
            updateActiveState();
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            mIsScreenOn = false;
            updateActiveState();
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_VISUALIZER_SETTINGS_CHANGED)) {
            for (Listener l : mListeners) {
                l.onPreferenceChanged(intent);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DYNAMIC_COLOR)) {
                mDynamicColorEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_VISUALIZER_DYNAMIC_COLOR, true);
                if (!mDynamicColorEnabled) {
                    notifyColorUpdated(mDefaultColor);
                }
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_COLOR)) {
                mDefaultColor = intent.getIntExtra(
                        GravityBoxSettings.EXTRA_VISUALIZER_COLOR, Color.WHITE);
                if (!mDynamicColorEnabled) {
                    notifyColorUpdated(mDefaultColor);
                }
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_OPACITY)) {
                mOpacity = Math.round(255f * ((float)intent.getIntExtra(
                        GravityBoxSettings.EXTRA_VISUALIZER_OPACITY, 50)/100f));
                notifyColorUpdated(mCurrentColor);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_NAVBAR)) {
                updateActiveState();
            }
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        int color = palette.getVibrantColor(Color.TRANSPARENT);
        if (color == Color.TRANSPARENT) {
            color = palette.getLightVibrantColor(color);
            if (color == Color.TRANSPARENT) {
                color = palette.getDarkVibrantColor(color);
                if (color == Color.TRANSPARENT) {
                    color = mDefaultColor;
                }
            }
        }
        notifyColorUpdated(color);
    }

    private void notifyColorUpdated(int color) {
        mCurrentColor = color;
        color = Color.argb(mOpacity, Color.red(color), Color.green(color), Color.blue(color));
        for (Listener l : mListeners) {
            l.onColorUpdated(color);
        }
    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
        for (Listener l : mListeners) {
            if (l.isEnabled()) {
                l.onFftDataCapture(visualizer, fft, samplingRate);
            }
        }
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) { }
}
