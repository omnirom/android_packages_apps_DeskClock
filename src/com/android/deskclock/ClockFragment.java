/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextClock;

import com.android.deskclock.worldclock.CitiesActivity;
import com.android.deskclock.worldclock.WorldClockAdapter;

/**
 * Fragment that shows  the clock (analog or digital), the next alarm info and the world clock.
 */
public class ClockFragment extends DeskClockFragment implements OnSharedPreferenceChangeListener {

    private final static String TAG = "ClockFragment";

    private View mDigitalClock, mAnalogClock, mClockFrame;
    private WorldClockAdapter mAdapter;
    private ListView mList;
    private SharedPreferences mPrefs;
    private String mDateFormat;
    private String mDateFormatForAccessibility;
    private String mDefaultClockStyle;
    private String mClockStyle;

    private static final ViewOutlineProvider MAIN_CLOCK_OUTLINE_PROVIDER = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRect(0, 0, view.getWidth(), view.getHeight());
        }
    };
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean changed = action.equals(Intent.ACTION_TIME_CHANGED)
                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                    || action.equals(Intent.ACTION_LOCALE_CHANGED);
            if (changed) {
                Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mClockFrame);
                if (mAdapter != null) {
                    // *CHANGED may modify the need for showing the Home City
                    if (mAdapter.hasHomeCity() != mAdapter.needHomeCity()) {
                        mAdapter.reloadData(context);
                    } else {
                        mAdapter.notifyDataSetChanged();
                    }
                    // Locale change: update digital clock format and
                    // reload the cities list with new localized names
                    if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                        if (mDigitalClock != null) {
                            Utils.setTimeFormat(
                                    (TextClock) (mDigitalClock.findViewById(R.id.digital_clock)),
                                    (int) context.getResources().
                                            getDimension(R.dimen.main_ampm_font_size)
                            );
                        }
                        mAdapter.loadCitiesDb(context);
                        mAdapter.notifyDataSetChanged();
                    }
                }
                Utils.setQuarterHourUpdater(mHandler, mQuarterHourUpdater);
            }
            if (changed || action.equals(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
                Utils.refreshAlarm(getActivity(), mClockFrame);
            }
        }
    };

    private final Handler mHandler = new Handler();

    // Thread that runs on every quarter-hour and refreshes the date.
    private final Runnable mQuarterHourUpdater = new Runnable() {
        @Override
        public void run() {
            // Update the main and world clock dates
            Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mClockFrame);
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            Utils.setQuarterHourUpdater(mHandler, mQuarterHourUpdater);
        }
    };

    public ClockFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle icicle) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.clock_fragment, container, false);
        mList = (ListView) v.findViewById(R.id.cities);
        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startActivity(new Intent(getActivity(), CitiesActivity.class));
            }
        });

        mClockFrame = v.findViewById(R.id.main_clock);
        mClockFrame.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(getActivity(), ScreensaverActivity.class));
                return true;
            }
        });
        mClockFrame.setOutlineProvider(MAIN_CLOCK_OUTLINE_PROVIDER);

        mDigitalClock = mClockFrame.findViewById(R.id.digital_clock);
        mAnalogClock = mClockFrame.findViewById(R.id.analog_clock);
        Utils.setTimeFormat((TextClock) (mDigitalClock.findViewById(R.id.digital_clock)),
                (int) getResources().getDimension(R.dimen.main_ampm_font_size));
        View footerView = inflater.inflate(R.layout.blank_footer_view, mList, false);
        mList.addFooterView(footerView, null, false);

        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (!isLandscape) {
            View headerView = inflater.inflate(R.layout.blank_header_view, mList, false);
            mList.addHeaderView(headerView, null, false);
        }
        mAdapter = new WorldClockAdapter(getActivity());
        mList.setAdapter(mAdapter);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mDefaultClockStyle = getActivity().getResources().getString(R.string.default_clock_style);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        final DeskClock activity = (DeskClock) getActivity();
        setFabAppearance();
        setLeftRightButtonAppearance();

        mPrefs.registerOnSharedPreferenceChangeListener(this);
        mDateFormat = getString(R.string.abbrev_wday_month_day_no_year);
        mDateFormatForAccessibility = getString(R.string.full_wday_month_day_no_year);

        Utils.setQuarterHourUpdater(mHandler, mQuarterHourUpdater);
        // Besides monitoring when quarter-hour changes, monitor other actions that
        // effect clock time
        IntentFilter filter = new IntentFilter();
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        activity.registerReceiver(mIntentReceiver, filter);

        // Resume can invoked after changing the cities list or a change in locale
        if (mAdapter != null) {
            mAdapter.loadCitiesDb(activity);
            mAdapter.reloadData(activity);
        }
        // Resume can invoked after changing the clock style.
        View clockView = Utils.setClockStyle(activity, mDigitalClock, mAnalogClock,
                SettingsActivity.KEY_CLOCK_STYLE);
        mClockStyle = (clockView == mDigitalClock ?
                Utils.CLOCK_TYPE_DIGITAL : Utils.CLOCK_TYPE_ANALOG);

        if (mAdapter.getCount() == 0) {
            mList.setVisibility(View.GONE);
        } else {
            mList.setVisibility(View.VISIBLE);
        }
        mAdapter.notifyDataSetChanged();

        Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mClockFrame);
        Utils.refreshAlarm(activity, mClockFrame);
    }

    @Override
    public void onPause() {
        super.onPause();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        Utils.cancelQuarterHourUpdater(mHandler, mQuarterHourUpdater);
        Activity activity = getActivity();
        activity.unregisterReceiver(mIntentReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == SettingsActivity.KEY_CLOCK_STYLE) {
            mClockStyle = prefs.getString(SettingsActivity.KEY_CLOCK_STYLE, mDefaultClockStyle);
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFabClick(View view) {
        final Activity activity = getActivity();
        startActivity(new Intent(activity, CitiesActivity.class));
    }

    @Override
    public void setFabAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mFab == null || !activity.isClockTab()) {
            return;
        }
        mFab.setImageResource(R.drawable.ic_globe);
        mFab.setContentDescription(getString(R.string.button_cities));

        final AnimatorSet animatorSet = getFabButtonTransition(true);
        if (animatorSet != null) {
            animatorSet.start();
        }
    }

    @Override
    public void setLeftRightButtonAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mLeftButton == null || mRightButton == null ||
                !activity.isClockTab()) {
            return;
        }

        boolean leftVisible = false;
        boolean rightVisible = false;
        final AnimatorSet animatorSet = getButtonTransition(leftVisible, rightVisible);
        if (animatorSet != null) {
            animatorSet.start();
        }
    }
}
