/*
 *  Copyright (C) 2016 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.alarmclock;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.deskclock.R;
import com.android.deskclock.preference.FontPreference;

public class CustomAppWidgetConfigure extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    public static final String KEY_SHOW_WORLD_CLOCK = "show_world_clock";
    public static final String KEY_SHOW_ALARM = "show_alarm";
    public static final String KEY_SHOW_DATE = "show_date";
    public static final String KEY_CLOCK_FONT = "clock_font";
    private FontPreference mClockFont;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, 
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID,
        // finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        addPreferencesFromResource(R.xml.custom_appwidget_configure);
        initPreference(KEY_SHOW_WORLD_CLOCK);
        initPreference(KEY_SHOW_ALARM);
        initPreference(KEY_SHOW_DATE);

        FontPreference mClockFont = (FontPreference) findPreference(KEY_CLOCK_FONT);
        mClockFont.setKey(KEY_CLOCK_FONT + "_" + String.valueOf(mAppWidgetId));
        mClockFont.setOnPreferenceChangeListener(this);
    }

    public void handleOkClick(View v) {
        CustomAppWidgetProvider.updateAfterConfigure(this, mAppWidgetId);
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    private void initPreference(String key) {
        CheckBoxPreference b = (CheckBoxPreference) findPreference(key);
        b.setKey(key + "_" + String.valueOf(mAppWidgetId));
        b.setDefaultValue(true);
        b.setChecked(true);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(b.getKey(), true).commit();
    }

    public static void clearPrefs(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove(KEY_SHOW_WORLD_CLOCK + "_" + id).commit();
        prefs.edit().remove(KEY_SHOW_ALARM + "_" + id).commit();
        prefs.edit().remove(KEY_SHOW_DATE + "_" + id).commit();
        prefs.edit().remove(KEY_CLOCK_FONT + "_" + id).commit();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getContentResolver();
        if (preference == mClockFont) {
            String value = (String) newValue;
            int valueIndex = mClockFont.findIndexOfValue(value);
            mClockFont.setSummary(mClockFont.getEntries()[valueIndex]);
        }
        return true;
    }
}
