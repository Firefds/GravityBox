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

import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.GravityBoxSettings;
import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.managers.BatteryInfoManager.BatteryData;
import com.ceco.oreo.gravitybox.ModStatusBar.StatusBarState;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class LockscreenVisualizerLayout extends AVisualizerLayout
        implements View.OnClickListener {

    private static final String TAG = "GB:LockscreenVisualizerLayout";
    private static final boolean DEBUG = false;

    private static final long DIM_STATE_DELAY = 10000l;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private int mBgColor;
    private ValueAnimator mBgColorAnimator;
    private boolean mActiveMode;
    private int mPosition;
    private boolean mIsDimmed;
    private boolean mDimEnabled;
    private int mDimLevel;
    private boolean mDimInfoEnabled;
    private boolean mDimHeaderEnabled;
    private boolean mDimControlsEnabled;
    private boolean mDimArtworkEnabled;
    private PowerManager mPowerManager;
    private AudioManager mAudioManager;

    private View mScrim;
    private TextClock mClock;
    private TextView mBattery;
    private TextView mArtist;
    private TextView mTitle;
    private ImageView mArtwork;
    private ViewGroup mHeaderGroup;
    private ViewGroup mInfoGroup;
    private ViewGroup mControlsGroup;
    private ImageView mControlNext;
    private ImageView mControlStop;
    private ImageView mControlPrev;

    public LockscreenVisualizerLayout(Context context) throws Throwable {
        super(context);

        mBgColor = 0;

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void initPreferences(XSharedPreferences prefs) {
        super.initPreferences(prefs);

        mActiveMode = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_ACTIVE_MODE, false);
        mDimEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM, true);
        mDimLevel = Math.round(255f * ((float)prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM_LEVEL, 80)/100f));
        mDimInfoEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM_INFO, true);
        mDimHeaderEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM_HEADER, true);
        mDimControlsEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM_CONTROLS, true);
        mDimArtworkEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM_ARTWORK, true);
    }

    @Override
    public void onPreferenceChanged(Intent intent) {
        super.onPreferenceChanged(intent);

        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_ACTIVE_MODE)) {
            mActiveMode = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_ACTIVE_MODE, false);
        }
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM)) {
            mDimEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM, true);
        }
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_LEVEL)) {
            mDimLevel = Math.round(255f * ((float)intent.getIntExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_LEVEL, 80)/100f));
        }
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_INFO)) {
            mDimInfoEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_INFO, true);
        }
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_HEADER)) {
            mDimHeaderEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_HEADER, true);
        }
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_CONTROLS)) {
            mDimControlsEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_CONTROLS, true);
        }
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_ARTWORK)) {
            mDimArtworkEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_ARTWORK, true);
        }
    }

    @Override
    public void onCreateView(ViewGroup parent) throws Throwable {
        // find suitable position, put as last if failed
        mPosition = parent.getChildCount();
        int resId = parent.getResources().getIdentifier("status_bar_container", "id",
                parent.getContext().getPackageName());
        if (resId != 0) {
            View v = parent.findViewById(resId);
            if (v != null) {
                mPosition = parent.indexOfChild(v);
            }
        }
        if (DEBUG) log("Computed view position: " + mPosition);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        setLayoutParams(lp);
        parent.addView(this, mPosition);

        LayoutInflater inflater = LayoutInflater.from(Utils.getGbContext(getContext(),
                getContext().getResources().getConfiguration()));
        inflater.inflate(R.layout.visualizer, this);
        mVisualizerView = new VisualizerView(getContext());
        int idx = indexOfChild(findViewById(R.id.visualizer));
        removeViewAt(idx);
        addView(mVisualizerView, idx);

        mScrim = findViewById(R.id.scrim);
        mScrim.setBackgroundColor(mBgColor);
        mClock = findViewById(R.id.clock);
        mBattery = findViewById(R.id.battery);
        mArtist = findViewById(R.id.artist);
        mTitle = findViewById(R.id.title);
        mArtwork = findViewById(R.id.artwork);

        mHeaderGroup = findViewById(R.id.header);
        mInfoGroup = findViewById(R.id.info);

        mControlsGroup = findViewById(R.id.media_controls);
        mControlPrev = findViewById(R.id.control_prev);
        mControlPrev.setOnClickListener(this);
        mControlStop = findViewById(R.id.control_stop);
        mControlStop.setOnClickListener(this);
        mControlNext = findViewById(R.id.control_next);
        mControlNext.setOnClickListener(this);
    }

    private void userActivity() {
        try {
            XposedHelpers.callMethod(mPowerManager, "userActivity",
                    SystemClock.uptimeMillis(), false);
            if (DEBUG) log("Virtual userActivity sent");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private final Runnable mUserActivityRunnable = new Runnable() {
        @Override
        public void run() {
            userActivity();
            if (mActiveMode && mActive && isEnabled()) {
                postDelayed(this, 4000);
            }
        }
    };

    private final Runnable mEnterDimStateRunnable = new Runnable() {
        @Override
        public void run() {
            setDimState(true);
        }
    };

    private final Runnable mExitDimStateRunnable = new Runnable() {
        @Override
        public void run() {
            setDimState(false);
        }
    };

    private void setDimState(final boolean dim) {
        mIsDimmed = dim;

        if (mBgColorAnimator != null) {
            mBgColorAnimator.cancel();
            mBgColorAnimator = null;
        }

        if (isAttachedToWindow()) {
            ViewGroup parent = (ViewGroup) getParent();
            int targetPos = dim ? parent.getChildCount()-1 : mPosition;
            if (targetPos != parent.indexOfChild(this)) {
                parent.removeView(this);
                parent.addView(this, targetPos);
            }
        }

        if (dim) {
            if (mDimInfoEnabled) {
                mArtist.setVisibility(View.VISIBLE);
                mTitle.setVisibility(View.VISIBLE);
            }
            if (mDimControlsEnabled) {
                mControlsGroup.setVisibility(View.VISIBLE);
            }
            if (mDimArtworkEnabled) {
                mArtwork.setVisibility(View.VISIBLE);
                mArtwork.animate().setDuration(1200).setStartDelay(600).alpha(1f);
            }

            if (mDimHeaderEnabled) {
                mHeaderGroup.setVisibility(View.VISIBLE);
                mHeaderGroup.animate().setDuration(1200).setStartDelay(600).alpha(1f);
            }
            if (mDimInfoEnabled || mDimControlsEnabled) {
                mInfoGroup.setVisibility(View.VISIBLE);
                mInfoGroup.animate().setDuration(1200).setStartDelay(600).alpha(1f);
            }

            mBgColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                    mBgColor, Color.argb(mDimLevel, 0, 0, 0));
            mBgColorAnimator.setDuration(1200);
            mBgColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    mBgColor = (int) va.getAnimatedValue();
                    mScrim.setBackgroundColor(mBgColor);
                }
            });
            mBgColorAnimator.start();
        } else {
            mBgColor = 0;
            mScrim.setBackgroundColor(mBgColor);
            mArtist.setVisibility(View.GONE);
            mTitle.setVisibility(View.GONE);
            mArtwork.setVisibility(View.GONE);
            mControlsGroup.setVisibility(View.GONE);
            mHeaderGroup.setVisibility(View.GONE);
            mInfoGroup.setVisibility(View.GONE);
            mArtwork.setAlpha(0f);
            mHeaderGroup.setAlpha(0f);
            mInfoGroup.setAlpha(0f);
        }
    }

    @Override
    public void onUserActivity() {
        super.onUserActivity();

        if (DEBUG) log("onUserActivity");
        removeCallbacks(mEnterDimStateRunnable);
        if (mIsDimmed) {
            post(mExitDimStateRunnable);
        }
        if (isEnabled() && mActive && mActiveMode && mDimEnabled) {
            postDelayed(mEnterDimStateRunnable, DIM_STATE_DELAY);
        }
    }

    
    @Override
    public void onMediaMetaDataUpdated(MediaMetadata md, Bitmap artwork) {
        super.onMediaMetaDataUpdated(md, artwork);

        mArtist.setText(md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null);
        mTitle.setText(md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null);
        mArtwork.setImageBitmap(artwork);
    }

    
    @Override
    public void onActiveStateChanged(boolean active) {
        super.onActiveStateChanged(active);

        if (active && isEnabled()) {
            if (mActiveMode) {
                postDelayed(mUserActivityRunnable, 4000);
                if (mDimEnabled) {
                    postDelayed(mEnterDimStateRunnable, DIM_STATE_DELAY);
                }
            }
        } else {
            removeCallbacks(mEnterDimStateRunnable);
            removeCallbacks(mUserActivityRunnable);
            if (mIsDimmed) {
                post(mExitDimStateRunnable);
            }
        }
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        super.onBatteryStatusChanged(batteryData);
        mBattery.setText(String.format("%d%%", batteryData.level));
    }

    @Override
    boolean supportsCurrentStatusBarState() {
        return mStatusBarState != StatusBarState.SHADE;
    }

    @Override
    void onColorAnimatedValueUpdated(int color) {
        super.onColorAnimatedValueUpdated(color);
        mClock.setTextColor(color);
        mBattery.setTextColor(color);
        mArtist.setTextColor(color);
        mTitle.setTextColor(color);
        mControlPrev.setImageTintList(ColorStateList.valueOf(color));
        mControlStop.setImageTintList(ColorStateList.valueOf(color));
        mControlNext.setImageTintList(ColorStateList.valueOf(color));
    }

    @Override
    public void onClick(View v) {
        if (v == mControlPrev) {
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if (v == mControlStop) {
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PAUSE);
        } else if (v == mControlNext) {
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
        }
    }

    private void sendMediaButtonEvent(int code) {
        long eventtime = SystemClock.uptimeMillis();
        Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent keyEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        mAudioManager.dispatchMediaKeyEvent(keyEvent);

        keyEvent = KeyEvent.changeAction(keyEvent, KeyEvent.ACTION_UP);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        mAudioManager.dispatchMediaKeyEvent(keyEvent);
    }
}
