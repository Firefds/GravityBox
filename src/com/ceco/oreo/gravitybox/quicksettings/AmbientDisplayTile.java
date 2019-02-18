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
package com.ceco.oreo.gravitybox.quicksettings;

import com.ceco.oreo.gravitybox.R;

import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import de.robv.android.xposed.XSharedPreferences;

public class AmbientDisplayTile extends QsTile {
    public static final class Service extends QsTileServiceBase {
        static final String KEY = AmbientDisplayTile.class.getSimpleName()+"$Service";
    }
    private static final String DOZE_ENABLED = "doze_enabled";

    private Handler mHandler;
    private SettingsObserver mSettingsObserver;

    protected AmbientDisplayTile(Object host, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);

        mHandler = new Handler();
        mSettingsObserver = new SettingsObserver(mHandler);
        mState.label = mGbContext.getString(R.string.qs_tile_ambient_display);
    }

    class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(DOZE_ENABLED), false, this);
        }

        public void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override 
        public void onChange(boolean selfChange) { 
            refreshState();
        }
    }

    private boolean isEnabled() {
        return (Settings.Secure.getInt(mContext.getContentResolver(),
                    DOZE_ENABLED, 0) == 1);
    }

    @Override
    public String getSettingsKey() {
        return "gb_tile_ambient_display";
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mSettingsObserver.observe();
        } else {
            mSettingsObserver.unobserve();
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.booleanValue = isEnabled();
        mState.icon = iconFromResId(mState.booleanValue ? R.drawable.ic_qs_ambientdisplay_on :
            R.drawable.ic_qs_ambientdisplay_off);
        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        Settings.Secure.putInt(mContext.getContentResolver(), DOZE_ENABLED,
                isEnabled() ? 0 : 1);
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mSettingsObserver = null;
        mHandler = null;
    }
}
