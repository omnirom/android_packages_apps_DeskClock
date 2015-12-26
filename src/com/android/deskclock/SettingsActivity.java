/*
 * Copyright (C) 2009 The Android Open Source Project
 *
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

package com.android.deskclock;

import android.Manifest;
import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;

import com.android.deskclock.worldclock.Cities;
import com.android.deskclock.alarms.AlarmStateManager;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.text.DateFormatSymbols;

/**
 * Settings for the Alarm Clock.
 */
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    private static final int ALARM_STREAM_TYPE_BIT =
            1 << AudioManager.STREAM_ALARM;

    public static final String KEY_ALARM_IN_SILENT_MODE =
            "alarm_in_silent_mode";
    public static final String KEY_ALARM_SNOOZE =
            "snooze_duration_new";
    public static final String KEY_VOLUME_BEHAVIOR =
            "volume_button_setting";
    public static final String KEY_AUTO_SILENCE =
            "auto_silence";
    public static final String KEY_CLOCK_STYLE =
            "clock_style";
    public static final String KEY_HOME_TZ =
            "home_time_zone";
    public static final String KEY_AUTO_HOME_CLOCK =
            "automatic_home_clock";
    public static final String KEY_VOLUME_BUTTONS =
            "volume_button_setting";
    public static final String KEY_FLIP_ACTION =
            "flip_action_setting";
    public static final String KEY_ALARM_SNOOZE_COUNT =
            "snooze_count";
    public static final String KEY_SHAKE_ACTION =
            "shake_action_setting";
    public static final String KEY_KEEP_SCREEN_ON =
            "keep_screen_on";
    public static final String KEY_VOLUME_INCREASE_SPEED =
            "volume_increase_speed";
    public static final String KEY_PRE_ALARM_DISMISS_ALL =
            "pre_alarm_dismiss_all";
    public static final String KEY_FULLSCREEN_ALARM =
            "fullscreen_alarm";
    public static final String KEY_TIMER_ALARM =
            "timer_alarm";
    public static final String KEY_TIMER_ALARM_CUSTOM =
            "timer_alarm_custom";
    public static final String KEY_WEEK_START =
            "week_start";
    public static final String KEY_FULLSCREEN_ALARM_SETTINGS =
            "fullscreen_alarm_settings";

    // default action for alarm action
    public static final String DEFAULT_ALARM_ACTION = "0";

    // constants for no action/snooze/dismiss
    public static final String ALARM_NO_ACTION = "0";
    public static final String ALARM_SNOOZE = "1";
    public static final String ALARM_DISMISS = "2";

    private static final int PERMISSIONS_REQUEST_WRITE_SETTINGS = 0;

    private static CharSequence[][] mTimezones;
    private long mTime;
    private RingtonePreference mTimerAlarmPref;
    private final Handler mHandler = new Handler();
    private CheckBoxPreference mFullscreenAlarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        // We don't want to reconstruct the timezone list every single time
        // onResume() is called so we do it once in onCreate
        ListPreference listPref;
        listPref = (ListPreference) findPreference(KEY_HOME_TZ);
        if (mTimezones == null) {
            mTime = System.currentTimeMillis();
            mTimezones = getAllTimezones();
        }

        listPref.setEntryValues(mTimezones[0]);
        listPref.setEntries(mTimezones[1]);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        mTimerAlarmPref = (RingtonePreference) findPreference(KEY_TIMER_ALARM);
        mTimerAlarmPref.setOnPreferenceChangeListener(this);
        addSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        lookupRingtoneNames();
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (KEY_AUTO_SILENCE.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            String delay = (String) newValue;
            updateAutoSnoozeSummary(listPref, delay);
        } else if (KEY_CLOCK_STYLE.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_HOME_TZ.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
            notifyHomeTimeZoneChanged();
        } else if (KEY_AUTO_HOME_CLOCK.equals(pref.getKey())) {
            boolean state =((CheckBoxPreference) pref).isChecked();
            Preference homeTimeZone = findPreference(KEY_HOME_TZ);
            homeTimeZone.setEnabled(!state);
            notifyHomeTimeZoneChanged();
        } else if (KEY_VOLUME_BUTTONS.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_ALARM_SNOOZE.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_ALARM_SNOOZE_COUNT.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_FLIP_ACTION.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_SHAKE_ACTION.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_VOLUME_INCREASE_SPEED.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_FULLSCREEN_ALARM.equals(pref.getKey())) {
            boolean state =(Boolean) newValue;
            Settings.System.putInt(this.getContentResolver(),
	                Settings.System.SHOW_ALARM_FULLSCREEN, state ? 1 : 0);
        } else if (KEY_WEEK_START.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        }
        return true;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        // Exported activity but no headers we support.
        return false;
    }

    private void updateAutoSnoozeSummary(ListPreference listPref,
            String delay) {
        int i = Integer.parseInt(delay);
        if (i == -1) {
            listPref.setSummary(R.string.auto_silence_never);
        } else {
            listPref.setSummary(getString(R.string.auto_silence_summary, i));
        }
    }

    private void notifyHomeTimeZoneChanged() {
        Intent i = new Intent(Cities.WORLDCLOCK_UPDATE_INTENT);
        sendBroadcast(i);
    }

    private void addSettings() {
        ListPreference listPref = (ListPreference) findPreference(KEY_AUTO_SILENCE);
        String delay = listPref.getValue();
        updateAutoSnoozeSummary(listPref, delay);
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_CLOCK_STYLE);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        Preference pref = findPreference(KEY_AUTO_HOME_CLOCK);
        boolean state =((CheckBoxPreference) pref).isChecked();
        pref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference)findPreference(KEY_HOME_TZ);
        listPref.setEnabled(state);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_VOLUME_BUTTONS);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        final SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        final boolean hasAccelSensor = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size() >= 1;
        final boolean hasOrientationSensor = sensorManager.getSensorList(Sensor.TYPE_ORIENTATION).size() >= 1;
        final PreferenceCategory alarmCategory = (PreferenceCategory) findPreference(
                        KEY_FULLSCREEN_ALARM_SETTINGS);

        listPref = (ListPreference) findPreference(KEY_FLIP_ACTION);
        if (hasOrientationSensor) {
            listPref.setSummary(listPref.getEntry());
            listPref.setOnPreferenceChangeListener(this);
        } else {
            alarmCategory.removePreference(listPref);
        }

        listPref = (ListPreference) findPreference(KEY_SHAKE_ACTION);
        if (hasAccelSensor) {
            listPref.setSummary(listPref.getEntry());
            listPref.setOnPreferenceChangeListener(this);
        } else {
            alarmCategory.removePreference(listPref);
        }

        listPref = (ListPreference) findPreference(KEY_ALARM_SNOOZE);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_ALARM_SNOOZE_COUNT);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_VOLUME_INCREASE_SPEED);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        mFullscreenAlarm = (CheckBoxPreference) findPreference(KEY_FULLSCREEN_ALARM);
        mFullscreenAlarm.setChecked(Settings.System.getInt(this.getContentResolver(),
	            Settings.System.SHOW_ALARM_FULLSCREEN, 0) == 1);
        mFullscreenAlarm.setOnPreferenceChangeListener(this);
        mFullscreenAlarm.setEnabled(false);
        checkWriteSettings();

        listPref = (ListPreference) findPreference(KEY_WEEK_START);
        listPref.setEntries(getWeekdays());
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);
    }

    private class TimeZoneRow implements Comparable<TimeZoneRow> {
        private static final boolean SHOW_DAYLIGHT_SAVINGS_INDICATOR = false;

        public final String mId;
        public final String mDisplayName;
        public final int mOffset;

        public TimeZoneRow(String id, String name) {
            mId = id;
            TimeZone tz = TimeZone.getTimeZone(id);
            boolean useDaylightTime = tz.useDaylightTime();
            mOffset = tz.getOffset(mTime);
            mDisplayName = buildGmtDisplayName(id, name, useDaylightTime);
        }

        @Override
        public int compareTo(TimeZoneRow another) {
            return mOffset - another.mOffset;
        }

        public String buildGmtDisplayName(String id, String displayName, boolean useDaylightTime) {
            int p = Math.abs(mOffset);
            StringBuilder name = new StringBuilder("(GMT");
            name.append(mOffset < 0 ? '-' : '+');

            name.append(p / DateUtils.HOUR_IN_MILLIS);
            name.append(':');

            int min = p / 60000;
            min %= 60;

            if (min < 10) {
                name.append('0');
            }
            name.append(min);
            name.append(") ");
            name.append(displayName);
            if (useDaylightTime && SHOW_DAYLIGHT_SAVINGS_INDICATOR) {
                name.append(" \u2600"); // Sun symbol
            }
            return name.toString();
        }
    }

    /**
     * Returns an array of ids/time zones. This returns a double indexed array
     * of ids and time zones for Calendar. It is an inefficient method and
     * shouldn't be called often, but can be used for one time generation of
     * this list.
     *
     * @return double array of tz ids and tz names
     */
    public CharSequence[][] getAllTimezones() {
        Resources resources = this.getResources();
        String[] ids = resources.getStringArray(R.array.timezone_values);
        String[] labels = resources.getStringArray(R.array.timezone_labels);
        int minLength = ids.length;
        if (ids.length != labels.length) {
            minLength = Math.min(minLength, labels.length);
            LogUtils.e("Timezone ids and labels have different length!");
        }
        List<TimeZoneRow> timezones = new ArrayList<TimeZoneRow>();
        for (int i = 0; i < minLength; i++) {
            timezones.add(new TimeZoneRow(ids[i], labels[i]));
        }
        Collections.sort(timezones);

        CharSequence[][] timeZones = new CharSequence[2][timezones.size()];
        int i = 0;
        for (TimeZoneRow row : timezones) {
            timeZones[0][i] = row.mId;
            timeZones[1][i++] = row.mDisplayName;
        }
        return timeZones;
    }

    private void setTimerAlarmSummary() {
        Uri defaultAlarmNoise = RingtoneManager.getActualDefaultRingtoneUri(this,
                RingtoneManager.TYPE_ALARM);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String soundValue = prefs.getString(KEY_TIMER_ALARM, null);
        Uri soundUri = TextUtils.isEmpty(soundValue) ? defaultAlarmNoise : Uri.parse(soundValue);
        final Ringtone tone = soundUri != null ? RingtoneManager.getRingtone(this, soundUri) : null;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTimerAlarmPref.setSummary(tone != null ? tone.getTitle(SettingsActivity.this) : "");
            }
        });
    }

    private void lookupRingtoneNames() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                setTimerAlarmSummary();
            }
        });
    }

    private String[] getWeekdays() {
        DateFormatSymbols dfs = new DateFormatSymbols();
        List<String> weekDayList = new ArrayList<String>();
        weekDayList.addAll(Arrays.asList(dfs.getWeekdays()));
        weekDayList.set(0, getResources().getString(R.string.default_week_start));
        return weekDayList.toArray(new String[weekDayList.size()]);
    }

    private void checkWriteSettings() {
        if (checkSelfPermission(Manifest.permission.WRITE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.WRITE_SETTINGS },
                    PERMISSIONS_REQUEST_WRITE_SETTINGS);
        } else {
            mFullscreenAlarm.setEnabled(true);
        }
   }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_SETTINGS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mFullscreenAlarm.setEnabled(true);
                }
            }
            return;
        }
    }
}
