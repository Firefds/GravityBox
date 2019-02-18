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
package com.ceco.oreo.gravitybox.ledcontrol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.ceco.oreo.gravitybox.GravityBoxAppCompatActivity;
import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.SettingsManager;
import com.ceco.oreo.gravitybox.WorldReadablePrefs;
import com.ceco.oreo.gravitybox.ledcontrol.QuietHoursRangeListAdapter.ListItemActionHandler;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class QuietHoursRangeListActivity extends GravityBoxAppCompatActivity
             implements OnItemClickListener, ListItemActionHandler {

    private FloatingActionButton mFab;
    private ListView mListView;
    private ArrayList<QuietHoursRangeListItem> mList;
    private WorldReadablePrefs mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.quiet_hours_range_list_activity);

        mListView = findViewById(R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setEmptyView(findViewById(R.id.empty));

        mFab = findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuietHoursRangeListActivity.this,
                        QuietHoursRangeActivity.class);
                startActivityForResult(intent, 0);
            }
        });

        mPrefs = SettingsManager.getInstance(this).getQuietHoursPrefs();
        mList = new ArrayList<>();
        for (String id : QuietHours.Range.getIdList(mPrefs)) {
            mList.add(new QuietHoursRangeListItem(this, QuietHours.Range.parse(
                    mPrefs.getStringSet(id, null))));
        }
        mListView.setAdapter(new QuietHoursRangeListAdapter(this, mList, this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            saveRange(new HashSet<>(data.getStringArrayListExtra(
                    QuietHoursRangeActivity.EXTRA_QH_RANGE)));
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void saveRange(Set<String> value) {
        QuietHours.Range range = QuietHours.Range.parse(value);
        boolean isNew = true;
        for (QuietHoursRangeListItem item : mList) {
            if (item.getRange().id.equals(range.id)) {
                item.getRange().days = range.days;
                item.getRange().startTime = range.startTime;
                item.getRange().endTime = range.endTime;
                item.getRange().muteLED = range.muteLED;
                item.getRange().muteVibe = range.muteVibe;
                item.getRange().muteSystemVibe = range.muteSystemVibe;
                item.getRange().muteSystemSounds = range.muteSystemSounds;
                item.getRange().ringerWhitelist = range.ringerWhitelist;
                isNew = false;
                break;
            }
        }
        if (isNew) {
            mList.add(new QuietHoursRangeListItem(this, range));
        }
        mPrefs.edit().putStringSet(range.id, range.getValue()).commit();
        QuietHoursActivity.broadcastSettings(this, mPrefs);
        ((QuietHoursRangeListAdapter)mListView.getAdapter()).notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        QuietHoursRangeListItem item = (QuietHoursRangeListItem) mListView.getItemAtPosition(position);
        Intent intent = new Intent(QuietHoursRangeListActivity.this,
                QuietHoursRangeActivity.class);
        intent.putStringArrayListExtra(QuietHoursRangeActivity.EXTRA_QH_RANGE,
                new ArrayList<>(item.getRange().getValue()));
        startActivityForResult(intent, 0);
    }

    @Override
    public void onItemDeleted(QuietHoursRangeListItem item) {
        for (int i = mList.size()-1; i >= 0; i--) {
            if (mList.get(i).getRange().id.equals(item.getRange().id)) {
                mList.remove(i);
                mPrefs.edit().remove(item.getRange().id).commit();
                QuietHoursActivity.broadcastSettings(this, mPrefs);
                ((QuietHoursRangeListAdapter)mListView.getAdapter()).notifyDataSetChanged();
            }
        }
    }
}
