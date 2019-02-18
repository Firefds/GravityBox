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

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.adapters.IBaseListAdapterItem;

import android.content.Context;
import android.text.format.DateFormat;

public class QuietHoursRangeListItem implements IBaseListAdapterItem {

    private Context mContext;
    private QuietHours.Range mRange;

    protected QuietHoursRangeListItem(Context context, QuietHours.Range range) {
        mContext = context;
        mRange = range;
    }

    protected QuietHours.Range getRange() {
        return mRange;
    }

    @Override
    public String getText() {
        String text = String.format("%s - %s", formatTime(mRange.startTime), 
                formatTime(mRange.endTime));
        if (mRange.endsNextDay()) {
            text = String.format("%s %s", text,
                    mContext.getString(R.string.next_day));
        }
        return text;
    }

    private String formatTime(int time) {
        int hours = (int) (time / 60);
        int minutes = time - hours*60;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
        if (!DateFormat.is24HourFormat(mContext)) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            try {
                Date dateObj = sdf.parse(timeStr);
                return new SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(dateObj);
            } catch (ParseException e) { }
        }
        return timeStr;
    }

    @Override
    public String getSubText() {
        String[] days = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();
        Set<String> values = new TreeSet<String>(mRange.days);
        String summary = "";
        for (String wday : values) {
            if (!summary.isEmpty()) summary += ", ";
            try {
                summary += days[Integer.valueOf(wday)];
            } catch (NumberFormatException e) { }
        }
        return summary;
    }
}
