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
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.ceco.oreo.gravitybox.ModLedControl;
import com.ceco.oreo.gravitybox.Utils;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;

public class QuietHours {
    public static final String PKG_WEARABLE_APP = "com.google.android.wearable.app";
    public enum Mode { ON, OFF, AUTO, WEAR };

    public static final class SystemSound {
        public static final String DIALPAD = "dialpad";
        public static final String TOUCH = "touch";
        public static final String SCREEN_LOCK = "screen_lock";
        public static final String CHARGER = "charger";
        public static final String RINGER = "ringer";
    }

    public static final class Range {
        public String id;
        public Set<String> days;
        public int startTime;
        public int endTime;
        public boolean muteLED;
        public boolean muteVibe;
        public boolean muteSystemVibe;
        public Set<String> muteSystemSounds;
        public Set<String> ringerWhitelist;

        private Range() { }

        public static Range parse(Set<String> dataSet) {
            Range r = createDefault();
            if (dataSet == null || dataSet.isEmpty())
                return r;

            for (String val : dataSet) {
                String[] data = val.split(":", 2);
                if (data[0].equals("id")) {
                    r.id = data[1];
                } else if (data[0].equals("days")) {
                    r.days = new HashSet<String>(Arrays.asList(data[1].split(",")));
                } else if (data[0].equals("startTime")) {
                    r.startTime = Integer.valueOf(data[1]);
                } else if (data[0].equals("endTime")) {
                    r.endTime = Integer.valueOf(data[1]);
                } else if (data[0].equals("muteLED")) {
                    r.muteLED = Boolean.valueOf(data[1]);
                } else if (data[0].equals("muteVibe")) {
                    r.muteVibe = Boolean.valueOf(data[1]);
                } else if (data[0].equals("muteSystemVibe")) {
                    r.muteSystemVibe = Boolean.valueOf(data[1]);
                } else if (data[0].equals("muteSystemSounds")) {
                    r.muteSystemSounds = new HashSet<String>(Arrays.asList(data[1].split(",")));
                } else if (data[0].equals("ringerWhitelist")) {
                    r.ringerWhitelist = new HashSet<String>(Arrays.asList(data[1].split(",")));
                }
            }
            return r;
        }

        public static Range createDefault() {
            Range r = new Range();
            r.id = String.format("qhr-%s", UUID.randomUUID().toString());
            r.days = new HashSet<String>(Arrays.asList("1","2","3","4","5","6","7"));
            r.startTime = 1380;
            r.endTime = 360;
            r.muteLED = false;
            r.muteVibe = true;
            r.muteSystemVibe = false;
            r.muteSystemSounds = new HashSet<String>();
            r.ringerWhitelist = new HashSet<String>();
            return r;
        }

        public Set<String> getValue() {
            Set<String> dataSet = new HashSet<String>();
            dataSet.add("id:" + id);
            String buf = "";
            for (String day : days) {
                if (!buf.isEmpty()) buf += ",";
                buf += day;
            }
            dataSet.add("days:" + buf);
            dataSet.add("startTime:" + String.valueOf(startTime));
            dataSet.add("endTime:" + String.valueOf(endTime));
            dataSet.add("muteLED:" + String.valueOf(muteLED));
            dataSet.add("muteVibe:" + String.valueOf(muteVibe));
            dataSet.add("muteSystemVibe:" + String.valueOf(muteSystemVibe));
            buf = "";
            for (String ss : muteSystemSounds) {
                if (!buf.isEmpty()) buf += ",";
                buf += ss;
            }
            dataSet.add("muteSystemSounds:" + buf);
            buf = "";
            for (String ss : ringerWhitelist) {
                if (!buf.isEmpty()) buf += ",";
                buf += ss;
            }
            dataSet.add("ringerWhitelist:" + buf);
            return dataSet;
        }

        public boolean endsNextDay() {
            return (endTime < startTime);
        }

        public static ArrayList<String> getIdList(SharedPreferences prefs) {
            ArrayList<String> list = new ArrayList<>();
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith("qhr-")) {
                    list.add(key);
                }
            }
            return list;
        }
    }

    private static final List<String> NOTIF_TEXT_FIELDS = new ArrayList<>(Arrays.asList(
            "android.title","android.text","android.subText","android.infoText",
            "android.summaryText","android.bigText"));

    public boolean uncLocked;
    public boolean enabled;
    private boolean muteLED;
    private boolean muteVibe;
    private Set<String> muteSystemSounds;
    public boolean showStatusbarIcon;
    public Mode mode;
    public boolean interactive;
    private boolean muteSystemVibe;
    private Set<String> ringerWhitelist;
    private Set<Range> ranges;

    public QuietHours(Bundle prefs) {
        uncLocked = prefs.getBoolean(QuietHoursActivity.EXTRA_QH_LOCKED);
        enabled = prefs.getBoolean(QuietHoursActivity.EXTRA_QH_ENABLED);
        muteLED = prefs.getBoolean(QuietHoursActivity.EXTRA_QH_MUTE_LED);
        muteVibe = prefs.getBoolean(QuietHoursActivity.EXTRA_QH_MUTE_VIBE);
        muteSystemSounds = new HashSet<String>(prefs.getStringArrayList(QuietHoursActivity.EXTRA_QH_MUTE_SYSTEM_SOUNDS));
        showStatusbarIcon = prefs.getBoolean(QuietHoursActivity.EXTRA_QH_STATUSBAR_ICON);
        mode = Mode.valueOf(prefs.getString(QuietHoursActivity.EXTRA_QH_MODE));
        interactive = prefs.getBoolean(QuietHoursActivity.EXTRA_QH_INTERACTIVE);
        muteSystemVibe = prefs.getBoolean(QuietHoursActivity.EXTRA_QH_MUTE_SYSTEM_VIBE);
        ringerWhitelist = new HashSet<String>(prefs.getStringArrayList(QuietHoursActivity.EXTRA_QH_RINGER_WHITELIST));
        ranges = new HashSet<Range>();
        for (String key : prefs.keySet()) {
            if (key.startsWith("qhr-")) {
                ranges.add(Range.parse(new HashSet<>(prefs.getStringArrayList(key))));
            }
        }
    }

    public QuietHours(SharedPreferences prefs) {
        uncLocked = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_LOCKED, false);
        enabled = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_ENABLED, false);
        muteLED = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_MUTE_LED, false);
        muteVibe = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_MUTE_VIBE, true);
        muteSystemSounds = prefs.getStringSet(QuietHoursActivity.PREF_KEY_QH_MUTE_SYSTEM_SOUNDS,
                new HashSet<String>());
        showStatusbarIcon = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_STATUSBAR_ICON, true);
        mode = Mode.valueOf(prefs.getString(QuietHoursActivity.PREF_KEY_QH_MODE, "AUTO"));
        interactive = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_INTERACTIVE, false);
        muteSystemVibe = prefs.getBoolean(QuietHoursActivity.PREF_KEY_MUTE_SYSTEM_VIBE, false);
        ringerWhitelist = prefs.getStringSet(QuietHoursActivity.PREF_KEY_QH_RINGER_WHITELIST,
                new HashSet<String>());
        ranges = new HashSet<Range>();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("qhr-")) {
                ranges.add(Range.parse(new HashSet<>(prefs.getStringSet(key, null))));
            }
        }
    }

    public boolean quietHoursActive(LedSettings ls, Notification n, boolean userPresent) {
        if (uncLocked || !enabled) return false;

        if (mode == Mode.WEAR) {
            return true;
        }

        if (ls.getEnabled() && ls.getQhIgnore()) {
            boolean defaultIgnoreResult = (interactive && userPresent) ? !ls.getQhIgnoreInteractive() : false;
            if (ls.getQhIgnoreList() == null || ls.getQhIgnoreList().trim().isEmpty()) {
                if (ModLedControl.DEBUG) ModLedControl.log("QH ignored for all notifications");
                return defaultIgnoreResult;
            } else {
                List<CharSequence> notifTexts = getNotificationTexts(n);
                String[] keywords = ls.getQhIgnoreList().trim().split(",");
                boolean ignore = false;
                for (String kw : keywords) {
                    kw = kw.toLowerCase(Locale.getDefault());
                    ignore |= n.tickerText != null && n.tickerText.toString()
                            .toLowerCase(Locale.getDefault()).contains(kw);
                    for (CharSequence notifText : notifTexts) {
                        ignore |= notifText.toString().toLowerCase(Locale.getDefault()).contains(kw);
                    }
                }
                if (ModLedControl.DEBUG) ModLedControl.log("QH ignore list contains keyword?: " + ignore);
                return (ignore ? defaultIgnoreResult : (quietHoursActive() || (interactive && userPresent)));
            }
        } else {
            return (quietHoursActive() || (interactive && userPresent));
        }
    }

    public boolean quietHoursActive() {
        if (uncLocked || !enabled) return false;

        if (mode != Mode.AUTO) {
            return (mode == Mode.ON || mode == Mode.WEAR);
        } else {
            return (getActiveRange() != null);
        }
    }

    public Range getActiveRange() {
        if (uncLocked || !enabled || mode != Mode.AUTO) return null;

        Calendar c = new GregorianCalendar();
        c.setTimeInMillis(System.currentTimeMillis());
        int curMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int curDay = c.get(Calendar.DAY_OF_WEEK);
        int prevDay = (curDay == 1 ? 7 : curDay - 1);

        for (Range range : ranges) {
            boolean active = false;
            if (range.endsNextDay()) {
                active = (curMin >= range.startTime && range.days.contains(String.valueOf(curDay)) ||
                    (curMin < range.endTime && range.days.contains(String.valueOf(prevDay))));
            } else {
                active = range.days.contains(String.valueOf(curDay));
            }
            if (active && Utils.isTimeOfDayInRange(c.getTimeInMillis(), range.startTime, range.endTime)) {
                return range;
            }
        }

        return null;
    }

    public boolean shouldMuteLed() {
        if (mode == Mode.AUTO) {
            Range r = getActiveRange();
            if (r != null) {
                return r.muteLED;
            }
        }
        return muteLED;
     }

    public boolean shouldMuteVibe() {
        if (mode == Mode.AUTO) {
            Range r = getActiveRange();
            if (r != null) {
                return r.muteVibe;
            }
        }
        return muteVibe;
    }

    public boolean shouldMuteSystemVibe() {
        if (mode == Mode.AUTO) {
            Range r = getActiveRange();
            if (r != null) {
                return r.muteSystemVibe;
            }
        }
        return muteSystemVibe;
    }

    public boolean isSystemSoundMuted(String systemSound) {
        if (mode == Mode.AUTO) {
            Range r = getActiveRange();
            if (r != null) {
                return r.muteSystemSounds.contains(systemSound);
            }
        }
        return muteSystemSounds.contains(systemSound) && quietHoursActive();
    }

    public Set<String> getRingerWhitelist() {
        if (mode == Mode.AUTO) {
            Range r = getActiveRange();
            if (r != null) {
                return r.ringerWhitelist;
            }
        }
        return ringerWhitelist;
    }

    private List<CharSequence> getNotificationTexts(Notification notification) {
        List<CharSequence> texts = new ArrayList<>();

        for (String extra : NOTIF_TEXT_FIELDS) {
            CharSequence cs = notification.extras.getCharSequence(extra);
            if (cs != null) texts.add(cs);
        }

        if (ModLedControl.DEBUG) {
            for (CharSequence text : texts) {
                ModLedControl.log("Notif text: " + text);
            }
        }

        return texts;
    }
}
