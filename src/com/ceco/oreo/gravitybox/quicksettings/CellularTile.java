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

import java.util.Arrays;
import java.util.List;

import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.Utils;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class CellularTile extends AospTile {
    public static final String AOSP_KEY = "cell";
    public static final String MSIM_KEY1 = "cell1";
    public static final String MSIM_KEY2 = "cell2";
    public static final List<String> AOSP_KEYS = Arrays.asList(AOSP_KEY, MSIM_KEY1, MSIM_KEY2);

    protected CellularTile(Object host, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        if (!Utils.isOxygenOsRom()) {
            try {
                XposedHelpers.setBooleanField(state, "dualTarget", true);
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
        super.handleUpdateState(state, arg);
    }

    @Override
    public String getSettingsKey() {
        return "aosp_tile_cell";
    }
}
