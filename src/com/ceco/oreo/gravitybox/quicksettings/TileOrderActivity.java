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

package com.ceco.oreo.gravitybox.quicksettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.GravityBoxListActivity;
import com.ceco.oreo.gravitybox.GravityBoxSettings;
import com.ceco.oreo.gravitybox.SettingsManager;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.WorldReadablePrefs;
import com.ceco.oreo.gravitybox.ledcontrol.LedSettings;
import com.ceco.oreo.gravitybox.ledcontrol.QuietHoursActivity;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

public class TileOrderActivity extends GravityBoxListActivity implements View.OnClickListener {
    public static final String PREF_KEY_TILE_ENABLED = "pref_qs_tile_enabled";
    public static final String PREF_KEY_TILE_SECURED = "pref_qs_tile_secured";
    public static final String EXTRA_TILE_SECURED_LIST = "tileSecuredList";

    @SuppressWarnings("serial")
    private static Map<String, Class<?>> SERVICES = new HashMap<String, Class<?>>() {{
        put("gb_tile_nfc", NfcTile.Service.class);
        put("gb_tile_gps_slimkat", LocationTileSlimkat.Service.class);
        put("gb_tile_gps_alt", GpsTile.Service.class);
        put("gb_tile_ringer_mode", RingerModeTile.Service.class);
        put("gb_tile_volume", VolumeTile.Service.class);
        put("gb_tile_network_mode", NetworkModeTile.Service.class);
        put("gb_tile_smart_radio", SmartRadioTile.Service.class);
        put("gb_tile_sync", SyncTile.Service.class);
        put("gb_tile_torch", TorchTile.Service.class);
        put("gb_tile_sleep", SleepTile.Service.class);
        put("gb_tile_stay_awake", StayAwakeTile.Service.class);
        put("gb_tile_quickrecord", QuickRecordTile.Service.class);
        put("gb_tile_quickapp", QuickAppTile.Service1.class);
        put("gb_tile_quickapp2", QuickAppTile.Service2.class);
        put("gb_tile_quickapp3", QuickAppTile.Service3.class);
        put("gb_tile_quickapp4", QuickAppTile.Service4.class);
        put("gb_tile_expanded_desktop", ExpandedDesktopTile.Service.class);
        put("gb_tile_screenshot", ScreenshotTile.Service.class);
        put("gb_tile_gravitybox", GravityBoxTile.Service.class);
        put("gb_tile_usb_tether", UsbTetherTile.Service.class);
        put("gb_tile_lock_screen", LockScreenTile.Service.class);
        put("gb_tile_quiet_hours", QuietHoursTile.Service.class);
        put("gb_tile_compass", CompassTile.Service.class);
        put("gb_tile_bt_tethering", BluetoothTetheringTile.Service.class);
        put("gb_tile_ambient_display", AmbientDisplayTile.Service.class);
        put("gb_tile_heads_up", HeadsUpTile.Service.class);
    }};

    private ListView mTileListView;
    private TileAdapter mTileAdapter;
    private Context mContext;
    private Resources mResources;
    private WorldReadablePrefs mPrefs;
    private Map<String, String> mTileSpecs;
    private Map<String, TileInfo> mTileList;
    private Button mBtnSave;
    private Button mBtnCancel;

    class TileInfo {
        String key;
        String name;
        boolean enabled;
        boolean secured;
        boolean isStock() { return key.startsWith("aosp_tile_"); }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.order_tile_list_activity);

        mContext = this;
        mResources = mContext.getResources();
        mPrefs = SettingsManager.getInstance(mContext).getMainPrefs();

        mBtnSave = (Button) findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(this);
        mBtnCancel = (Button) findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(this);

        String[] allTileKeys = mResources.getStringArray(R.array.qs_tile_values);
        String[] allTileNames = mResources.getStringArray(R.array.qs_tile_entries);
        mTileSpecs = new LinkedHashMap<String, String>();
        for (int i = 0; i < allTileKeys.length; i++) {
            mTileSpecs.put(allTileKeys[i], allTileNames[i]);
        }
        mTileList = getTileList();
        filterOutStoredTileLists();

        mTileListView = getListView();
        mTileAdapter = new TileAdapter(mContext);
    }

    private void filterOutStoredTileLists() {
        List<String> toRemove = new ArrayList<>();

        List<String> enabledList = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_ENABLED, "").split(",")));
        for (String key : enabledList) {
            if (!mTileList.containsKey(key) || key.startsWith("aosp_tile_"))
                toRemove.add(key);
        }
        if (toRemove.size() > 0) {
            for (String key : toRemove) enabledList.remove(key);
            mPrefs.edit().putString(PREF_KEY_TILE_ENABLED, Utils.join(
                    enabledList.toArray(new String[enabledList.size()]), ",")).commit();
            updateServiceComponents(this);
        }

        toRemove.clear();
        List<String> securedList = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_SECURED, "").split(",")));
        for (String key : securedList) {
            if (!mTileList.containsKey(key))
                toRemove.add(key);
        }
        if (toRemove.size() > 0) {
            for (String key : toRemove) securedList.remove(key);
            mPrefs.edit().putString(PREF_KEY_TILE_SECURED, Utils.join(
                    securedList.toArray(new String[securedList.size()]), ",")).commit();
            broadcastSecuredList();
        }
    }

    public static void updateServiceComponents(Context ctx) {
        SharedPreferences prefs = SettingsManager.getInstance(ctx).getMainPrefs();
        List<String> enabledList = new ArrayList<String>(Arrays.asList(
                prefs.getString(PREF_KEY_TILE_ENABLED, "").split(",")));
        PackageManager pm = ctx.getPackageManager();
        for (Entry<String,Class<?>> service : SERVICES.entrySet()) {
            pm.setComponentEnabledSetting(new ComponentName(ctx, service.getValue()),
                    enabledList.contains(service.getKey()) ?
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    private void broadcastSecuredList() {
        Intent intent = new Intent(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED);
        intent.putExtra(EXTRA_TILE_SECURED_LIST, mPrefs.getString(
                PREF_KEY_TILE_SECURED, ""));
        sendBroadcast(intent);
    }

    private boolean supportedTile(String key) {
        if (!mTileSpecs.containsKey(key))
            return false;
        if (key.equals("gb_tile_torch") && !Utils.hasFlash(mContext))
            return false;
        if ((key.equals("gb_tile_gps_alt") || key.equals("gb_tile_gps_slimkat")) &&
                !Utils.hasGPS(mContext))
            return false;
        if ((key.equals("aosp_tile_cell") || key.equals("gb_tile_network_mode") ||
                key.equals("gb_tile_smart_radio")) && Utils.isWifiOnly(mContext))
            return false;
        if (key.equals("gb_tile_nfc") && !Utils.hasNfc(mContext))
            return false;
        if (key.equals("gb_tile_quiet_hours") &&
                (LedSettings.isUncLocked(mContext) ||
                 !SettingsManager.getInstance(mContext).getQuietHoursPrefs()
                     .getBoolean(QuietHoursActivity.PREF_KEY_QH_ENABLED, false)))
            return false;
        if (key.equals("gb_tile_compass") && !Utils.hasCompass(mContext))
            return false;
        if (key.equals("gb_tile_smart_radio") && !mPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false))
            return false;
        if (key.equals("gb_tile_quickrecord") && !isAudioRecordingAllowed())
            return false;
        if (key.equals("aosp_tile_data") && !Utils.isMotoXtDevice())
            return false;

        return true;
    }

    private boolean isAudioRecordingAllowed() {
        return (checkSelfPermission(permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onStart() {
        super.onStart();
        setListAdapter(mTileAdapter);
    }

    @Override
    public void onStop() {
        super.onStop();
        setListAdapter(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mTileListView.invalidateViews();
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnSave) {
            saveTileList();
            finish();
        } else if (v == mBtnCancel) {
            finish();
        }
    }

    private Map<String, TileInfo> getTileList() {
        List<String> enabledTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_ENABLED, "").split(",")));
        List<String> securedTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_SECURED, "").split(",")));

        Map<String, TileInfo> tiles = new LinkedHashMap<>();
        for (Entry<String,String> entry : mTileSpecs.entrySet()) {
            if (!supportedTile(entry.getKey()))
                continue;
            TileInfo ti = new TileInfo();
            ti.key = entry.getKey();
            ti.name = entry.getValue();
            ti.enabled = ti.isStock() || enabledTiles.contains(ti.key);
            ti.secured = securedTiles.contains(ti.key) || ti.key.equals("gb_tile_lock_screen");
            tiles.put(ti.key, ti);
        }

        return tiles;
    }

    private void saveTileList() {
        String newEnabledList = "";
        String newSecuredList = "";

        for (Entry<String,TileInfo> entry : mTileList.entrySet()) {
            if (entry.getValue().enabled && !entry.getValue().isStock()) {
                if (!newEnabledList.isEmpty()) newEnabledList += ",";
                newEnabledList += entry.getKey();
            }
            if (entry.getValue().secured) {
                if (!newSecuredList.isEmpty()) newSecuredList += ",";
                newSecuredList += entry.getKey();
            }
        }

        mPrefs.edit()
            .putString(PREF_KEY_TILE_ENABLED, newEnabledList)
            .putString(PREF_KEY_TILE_SECURED, newSecuredList)
            .commit();

        updateServiceComponents(this);
        broadcastSecuredList();
    }

    private class TileAdapter extends BaseAdapter {
        private Context mContext;
        private LayoutInflater mInflater;
        private List<TileInfo> mList;

        public TileAdapter(Context c) {
            mContext = c;
            mInflater = LayoutInflater.from(mContext);
            mList = new ArrayList<>(mTileList.values());
        }

        public int getCount() {
            return mList.size();
        }

        public Object getItem(int position) {
            return mList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("InflateParams")
        public View getView(int position, View convertView, ViewGroup parent) {
            final View itemView;
            final TileInfo tileInfo = mList.get(position);

            if (convertView == null) {
                itemView = mInflater.inflate(R.layout.order_tile_list_item, null);
                final CheckBox enabled = (CheckBox) itemView.findViewById(R.id.chkEnable);
                enabled.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TileInfo ti = (TileInfo) itemView.getTag();
                        ti.enabled = ((CheckBox)v).isChecked();
                        ti.secured &= ti.enabled || ti.key.equals("gb_tile_lock_screen");
                        mTileListView.invalidateViews();
                    }
                });
                final CheckBox secured = (CheckBox) itemView.findViewById(R.id.chkProtect);
                secured.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TileInfo ti = (TileInfo) itemView.getTag();
                        ti.secured = ((CheckBox)v).isChecked();
                        mTileListView.invalidateViews();
                    }
                });
            } else {
                itemView = convertView;
            }

            itemView.setTag(tileInfo);
            final TextView name = (TextView) itemView.findViewById(R.id.name);
            final TextView info = (TextView) itemView.findViewById(R.id.info);
            final CheckBox enabled = (CheckBox) itemView.findViewById(R.id.chkEnable);
            final CheckBox secured = (CheckBox) itemView.findViewById(R.id.chkProtect);
            name.setText(tileInfo.name);
            String infoTxt = "";
            if (tileInfo.enabled) {
                if (!tileInfo.isStock()) { 
                    infoTxt = getString(R.string.state_enabled);
                }
                if (tileInfo.secured) {
                    if (infoTxt.length() > 0) infoTxt += "; ";
                    infoTxt += getString(R.string.qs_protected_summary);
                }
            }
            info.setText(infoTxt);
            info.setVisibility(infoTxt.length() == 0 ? View.GONE : View.VISIBLE);

            enabled.setChecked(tileInfo.enabled);
            enabled.setVisibility(tileInfo.isStock() ? View.INVISIBLE : View.VISIBLE);
            secured.setChecked(tileInfo.enabled && tileInfo.secured);
            secured.setEnabled(enabled.isChecked() &&
                    !tileInfo.key.equals("gb_tile_lock_screen"));

            return itemView;
        }
    }
}
