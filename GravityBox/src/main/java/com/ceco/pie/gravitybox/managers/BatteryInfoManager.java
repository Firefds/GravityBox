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

package com.ceco.pie.gravitybox.managers;

import java.util.ArrayList;

import com.ceco.pie.gravitybox.BroadcastSubReceiver;
import com.ceco.pie.gravitybox.GravityBox;
import com.ceco.pie.gravitybox.GravityBoxSettings;
import com.ceco.pie.gravitybox.Utils;
import com.ceco.pie.gravitybox.ledcontrol.QuietHours;
import com.ceco.pie.gravitybox.ledcontrol.QuietHoursActivity;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.telephony.TelephonyManager;

public class BatteryInfoManager implements BroadcastSubReceiver {
    private static final String TAG = "GB:BatteryInfoManager";
    private BatteryData mBatteryData;
    private final ArrayList<BatteryStatusListener> mListeners = new ArrayList<>();
    private Context mContext;
    private Uri[] mSounds;
    private TelephonyManager mTelephonyManager;
    private LowBatteryWarningPolicy mLowBatteryWarningPolicy;
    private PowerManager mPowerManager;
    private static QuietHours mQuietHours;

    public static final int SOUND_CHARGED = 0;
    public static final int SOUND_PLUGGED = 1;
    public static final int SOUND_UNPLUGGED = 2;

    public enum LowBatteryWarningPolicy { DEFAULT, NONINTRUSIVE, OFF }

    public static final String ACTION_POWER_SAVE_MODE_CHANGING = 
            "android.os.action.POWER_SAVE_MODE_CHANGING";
    public static final String EXTRA_POWER_SAVE_MODE = "mode";
    public static final String EXTRA_FAST_CHARGE = "fastcharge_status";

    public class BatteryData {
        public boolean charging;
        public int level;
        public int powerSource;
        public int temperature;
        public int voltage;
        public boolean isPowerSaving;
        public boolean fastCharging;

        public float getTempCelsius() {
            return ((float)temperature/10f);
        }

        public float getTempFahrenheit() {
            return (((float)temperature/10f)*(9f/5f)+32);
        }

        public float getTemp(String unit) {
            if ("C".equals(unit))
                return getTempCelsius();
            else if ("F".equals(unit))
                return getTempFahrenheit();
            else
                return 0f; 
        }

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        public BatteryData clone() {
            BatteryData bd = new BatteryData();
            bd.charging = this.charging;
            bd.level = this.level;
            bd.powerSource = this.powerSource;
            bd.temperature = this.temperature;
            bd.voltage = this.voltage;
            bd.isPowerSaving = this.isPowerSaving;
            bd.fastCharging = this.fastCharging;
            return bd;
        }

        public String toString() {
            return "charging="+this.charging+"; level="+this.level+
                    "; powerSource="+this.powerSource+
                    "; temperature="+this.temperature+
                    "; voltage="+this.voltage+
                    "; isPowerSaving="+this.isPowerSaving+
                    "; fastCharging="+this.fastCharging;
                    
        }
    }

    public interface BatteryStatusListener {
        void onBatteryStatusChanged(BatteryData batteryData);
    }

    protected BatteryInfoManager(Context context, XSharedPreferences prefs, XSharedPreferences qhPrefs) {
        mContext = context;
        mQuietHours = new QuietHours(qhPrefs);
        mBatteryData = new BatteryData();
        mSounds = new Uri[4];
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mBatteryData.isPowerSaving = mPowerManager.isPowerSaveMode();

        setSound(BatteryInfoManager.SOUND_CHARGED,
                prefs.getString(GravityBoxSettings.PREF_KEY_BATTERY_CHARGED_SOUND, ""));
        setSound(BatteryInfoManager.SOUND_PLUGGED,
                prefs.getString(GravityBoxSettings.PREF_KEY_CHARGER_PLUGGED_SOUND, ""));
        setSound(BatteryInfoManager.SOUND_UNPLUGGED,
                prefs.getString(GravityBoxSettings.PREF_KEY_CHARGER_UNPLUGGED_SOUND, ""));

        try {
            mLowBatteryWarningPolicy = LowBatteryWarningPolicy.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_LOW_BATTERY_WARNING_POLICY, "DEFAULT"));
        } catch (Throwable t) {
            mLowBatteryWarningPolicy = LowBatteryWarningPolicy.DEFAULT;
        }
    }

    public void registerListener(BatteryStatusListener listener) {
        if (listener == null) return;
        synchronized(mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
                listener.onBatteryStatusChanged(mBatteryData);
            }
        }
    }

    public void unregisterListener(BatteryStatusListener listener) {
        if (listener == null) return;
        synchronized(mListeners) {
            mListeners.remove(listener);
        }
    }

    private void notifyListeners() {
        synchronized(mListeners) {
            for (int i = mListeners.size()-1; i >= 0; i--) {
                mListeners.get(i).onBatteryStatusChanged(mBatteryData.clone());
            }
        }
    }

    private void updateBatteryInfo(Intent intent) {
        if (intent == null) return;

        int newLevel = (int)(100f
                * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int newPowerSource = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean newCharging = newPowerSource != 0;
        int newTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int newVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        boolean newFastCharging = intent.getIntExtra(EXTRA_FAST_CHARGE, 0) != 0;

        if (mBatteryData.level != newLevel || mBatteryData.charging != newCharging ||
                mBatteryData.powerSource != newPowerSource ||
                mBatteryData.temperature != newTemp || 
                mBatteryData.voltage != newVoltage ||
                mBatteryData.fastCharging != newFastCharging) {
            if (newLevel == 100 && mBatteryData.level < 100 && mBatteryData.level > 0) {
                playSound(SOUND_CHARGED);
            }

            if (mBatteryData.powerSource != newPowerSource) {
                if (newPowerSource == 0) {
                    playSound(SOUND_UNPLUGGED);
                } else if (mBatteryData.powerSource == 0) {
                    playSound(SOUND_PLUGGED);
                }
            }

            mBatteryData.level = newLevel;
            mBatteryData.charging = newCharging;
            mBatteryData.powerSource = newPowerSource;
            mBatteryData.temperature = newTemp;
            mBatteryData.voltage = newVoltage;
            mBatteryData.fastCharging = newFastCharging;

            notifyListeners();
        }
    }

    private void updatePowerSavingInfo(boolean enabled) {
        if (mBatteryData.isPowerSaving != enabled) {
            mBatteryData.isPowerSaving = enabled;
            notifyListeners();
        }
    }

    public void setPowerSaving(boolean enabled) {
        try {
            XposedHelpers.callMethod(mPowerManager, "setPowerSaveMode", enabled);
        } catch (Throwable t) {
            XposedBridge.log("Error setting power saving mode: " + t.getMessage());
        }
    }

    public void togglePowerSaving() {
        setPowerSaving(!mPowerManager.isPowerSaveMode());
    }

    public BatteryData getCurrentBatteryData() {
        return mBatteryData;
    }

    public LowBatteryWarningPolicy getLowBatteryWarningPolicy() {
        return mLowBatteryWarningPolicy;
    }

    public void setSound(int type, String uri) {
        if (type < 0 || type > (mSounds.length-1)) return;

        if (uri == null || uri.isEmpty() || (type == SOUND_PLUGGED &&
                 uri.equals("content://settings/system/notification_sound"))) {
            mSounds[type] = null;
        } else {
            try {
                mSounds[type] = Uri.parse(uri);
            } catch (Exception e) {
                mSounds[type] = null;
            }
        }
    }

    private void playSound(int type) {
        if (type < 0 || type > (mSounds.length-1) || mSounds[type] == null || 
                !isPhoneIdle() || quietHoursActive()) return;
        try {
            final Ringtone sfx = RingtoneManager.getRingtone(mContext, mSounds[type]);
            if (sfx != null) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                sfx.setAudioAttributes(attrs);
                sfx.play();
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private boolean isPhoneIdle() {
        if (Utils.isWifiOnly(mContext)) return true;
        try {
            if (mTelephonyManager == null) {
                mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            }
            return (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return true;
        }
    }

    private boolean quietHoursActive() {
        return mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.CHARGER);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            updateBatteryInfo(intent);
        } else if (action.equals(ACTION_POWER_SAVE_MODE_CHANGING)) {
            updatePowerSavingInfo(intent.getBooleanExtra(EXTRA_POWER_SAVE_MODE, false));
        } else if (action.equals(GravityBoxSettings.ACTION_PREF_BATTERY_SOUND_CHANGED)) {
            setSound(intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_SOUND_TYPE, -1),
                    intent.getStringExtra(GravityBoxSettings.EXTRA_BATTERY_SOUND_URI));
        } else if (action.equals(GravityBoxSettings.ACTION_PREF_LOW_BATTERY_WARNING_POLICY_CHANGED) &&
                intent.hasExtra(GravityBoxSettings.EXTRA_LOW_BATTERY_WARNING_POLICY)) {
            mLowBatteryWarningPolicy = LowBatteryWarningPolicy.valueOf(intent.getStringExtra(
                    GravityBoxSettings.EXTRA_LOW_BATTERY_WARNING_POLICY));
        } else if (action.equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
            mQuietHours = new QuietHours(intent.getExtras());
        }
    }
}
