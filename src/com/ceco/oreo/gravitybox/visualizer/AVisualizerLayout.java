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

import com.ceco.oreo.gravitybox.managers.BatteryInfoManager.BatteryData;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.audiofx.Visualizer;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

abstract class AVisualizerLayout extends FrameLayout implements VisualizerController.Listener {
    private static final String TAG = "GB:AVisualizerLayout";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    protected int mStatusBarState;
    protected int mColor;
    protected ValueAnimator mColorAnimator;
    protected VisualizerView mVisualizerView;

    protected boolean mActive = false;
    protected boolean mVisible = false;

    AVisualizerLayout(Context context) {
        super(context, null, 0);

        mColor = Color.TRANSPARENT;
        setAlpha(0f);
    }

    @Override
    public boolean isAttached() {
        return isAttachedToWindow();
    }

    @Override
    public void initPreferences(XSharedPreferences prefs) { }

    @Override
    public void onPreferenceChanged(Intent intent) { }

    @Override
    public abstract void onCreateView(ViewGroup parent) throws Throwable;

    @Override
    public void onActiveStateChanged(boolean active) {
        if (mActive != active) {
            mActive = active;
            updateViewVisibility();
        }
    }

    @Override
    public void onMediaMetaDataUpdated(MediaMetadata md, Bitmap artwork) { }

    @Override
    public void onUserActivity() { }

    @Override
    public void onStatusBarStateChanged(int oldState, int newState) {
        if (mStatusBarState != newState) {
            mStatusBarState = newState;
            updateViewVisibility();
        }
    }

    protected void updateViewVisibility() {
        final boolean newVisible = mActive && isEnabled();
        if (mVisible != newVisible) {
            mVisible = newVisible;
            if (DEBUG) log("updateViewVisibility: mVisible=" + mVisible);
            clearAnimation();
            animate().alpha(mVisible ? 1f : 0f).setDuration(isEnabled() ? 800 : 0);
        }
    }

    abstract boolean supportsCurrentStatusBarState();

    @Override
    public boolean isEnabled() {
        return (supportsCurrentStatusBarState());
    }

    protected void setColor(int color) {
        if (mColor != color) {
            final int oldColor = mColor;
            if (mColorAnimator != null) {
                mColorAnimator.cancel();
            }
            mColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                    oldColor, color);
            mColorAnimator.setDuration(1200);
            mColorAnimator.setStartDelay(600);
            mColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    mColor = (int) va.getAnimatedValue();
                    onColorAnimatedValueUpdated(mColor);
                }
            });
            mColorAnimator.start();
        }
    }

    void onColorAnimatedValueUpdated(int color) {
        mVisualizerView.setColor(color);
    }

    @Override
    public void onColorUpdated(int color) {
        setColor(color);
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) { }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
        mVisualizerView.setData(fft);
    }

    @Override
    public void setVerticalLeft(boolean left) {
        mVisualizerView.setVerticalLeft(left);
    }

    @Override
    public void setLight(boolean light) { }
}
