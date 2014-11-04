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

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.deskclock.alarms.AlarmStateManager;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.stopwatch.StopwatchFragment;
import com.android.deskclock.stopwatch.StopwatchService;
import com.android.deskclock.stopwatch.Stopwatches;
import com.android.deskclock.timer.TimerFragment;
import com.android.deskclock.timer.TimerObj;
import com.android.deskclock.timer.Timers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

/**
 * DeskClock clock view for desk docks.
 */
public class DeskClock extends Activity implements LabelDialogFragment.TimerLabelDialogHandler,
        LabelDialogFragment.AlarmLabelDialogHandler {
    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "DeskClock";
    // Alarm action for midnight (so we can update the date display).
    private static final String KEY_SELECTED_TAB = "selected_tab";
    private static final String KEY_LAST_HOUR_COLOR = "last_hour_color";
    // Check whether to change background every minute
    private static final long BACKGROUND_COLOR_CHECK_DELAY_MILLIS = DateUtils.MINUTE_IN_MILLIS;
    private static final int BACKGROUND_COLOR_INITIAL_ANIMATION_DURATION_MILLIS = 3000;
    // The depth of fab, use it to create shadow
    private static final float FAB_DEPTH = 20f;
    private static final int UNKNOWN_COLOR_ID = 0;

    private boolean mIsFirstLaunch = true;
    private ActionBar mActionBar;
    private Tab mAlarmTab;
    private Tab mClockTab;
    private Tab mTimerTab;
    private Tab mStopwatchTab;
    private Menu mMenu;
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;
    private Handler mHander;
    private ImageButton mFab;
    private ImageButton mLeftButton;
    private ImageButton mRightButton;
    private int mSelectedTab;
    private int mLastHourColor = UNKNOWN_COLOR_ID;
    private final Runnable mBackgroundColorChanger = new Runnable() {
        @Override
        public void run() {
            setBackgroundColor();
            mHander.postDelayed(this, BACKGROUND_COLOR_CHECK_DELAY_MILLIS);
        }
    };

    public static final int ALARM_TAB_INDEX = 0;
    public static final int CLOCK_TAB_INDEX = 1;
    public static final int TIMER_TAB_INDEX = 2;
    public static final int STOPWATCH_TAB_INDEX = 3;
    // Tabs indices are switched for right-to-left since there is no
    // native support for RTL in the ViewPager.
    public static final int RTL_ALARM_TAB_INDEX = 3;
    public static final int RTL_CLOCK_TAB_INDEX = 2;
    public static final int RTL_TIMER_TAB_INDEX = 1;
    public static final int RTL_STOPWATCH_TAB_INDEX = 0;
    public static final String SELECT_TAB_INTENT_EXTRA = "deskclock.select.tab";

    // TODO(rachelzhang): adding a broadcast receiver to adjust color when the timezone/time
    // changes in the background.

    @Override
    protected void onStart() {
        super.onStart();
        if (mHander == null) {
            mHander = new Handler();
        }
        mHander.postDelayed(mBackgroundColorChanger, BACKGROUND_COLOR_CHECK_DELAY_MILLIS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHander.removeCallbacks(mBackgroundColorChanger);
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        if (DEBUG) Log.d(LOG_TAG, "onNewIntent with intent: " + newIntent);

        // update our intent so that we can consult it to determine whether or
        // not the most recent launch was via a dock event
        setIntent(newIntent);

        // Timer receiver may ask to go to the timers fragment if a timer expired.
        int tab = newIntent.getIntExtra(SELECT_TAB_INTENT_EXTRA, -1);
        if (tab != -1) {
            if (mActionBar != null) {
                mActionBar.setSelectedNavigationItem(tab);
            }
        }
    }

    private static final ViewOutlineProvider OVAL_OUTLINE_PROVIDER = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
    };

    private void initViews() {
        setContentView(R.layout.desk_clock);
        mFab = (ImageButton) findViewById(R.id.fab);
        mFab.setOutlineProvider(OVAL_OUTLINE_PROVIDER);
        mFab.setTranslationZ(FAB_DEPTH);
        mLeftButton = (ImageButton) findViewById(R.id.left_button);
        mRightButton = (ImageButton) findViewById(R.id.right_button);
        if (mTabsAdapter == null) {
            mViewPager = (ViewPager) findViewById(R.id.desk_clock_pager);
            // Keep all four tabs to minimize jank.
            mViewPager.setOffscreenPageLimit(3);
            mTabsAdapter = new TabsAdapter(this, mViewPager);
            createTabs(mSelectedTab);
        }

        mFab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getSelectedFragment().onFabClick(view);
            }
        });
        mLeftButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getSelectedFragment().onLeftButtonClick(view);
            }
        });
        mRightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getSelectedFragment().onRightButtonClick(view);
            }
        });

        mActionBar.setSelectedNavigationItem(mSelectedTab);
    }

    private DeskClockFragment getSelectedFragment() {
        return (DeskClockFragment) mTabsAdapter.getItem(getRtlPosition(mSelectedTab));
    }

    private void createTabs(int selectedIndex) {
        mActionBar = getActionBar();

        if (mActionBar != null) {
            mActionBar.setDisplayOptions(0);
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            mAlarmTab = mActionBar.newTab();
            mAlarmTab.setIcon(R.drawable.alarm_tab);
            mAlarmTab.setContentDescription(R.string.menu_alarm);
            mTabsAdapter.addTab(mAlarmTab, AlarmClockFragment.class, ALARM_TAB_INDEX);

            mClockTab = mActionBar.newTab();
            mClockTab.setIcon(R.drawable.clock_tab);
            mClockTab.setContentDescription(R.string.menu_clock);
            mTabsAdapter.addTab(mClockTab, ClockFragment.class, CLOCK_TAB_INDEX);

            mTimerTab = mActionBar.newTab();
            mTimerTab.setIcon(R.drawable.timer_tab);
            mTimerTab.setContentDescription(R.string.menu_timer);
            mTabsAdapter.addTab(mTimerTab, TimerFragment.class, TIMER_TAB_INDEX);

            mStopwatchTab = mActionBar.newTab();
            mStopwatchTab.setIcon(R.drawable.stopwatch_tab);
            mStopwatchTab.setContentDescription(R.string.menu_stopwatch);
            mTabsAdapter.addTab(mStopwatchTab, StopwatchFragment.class, STOPWATCH_TAB_INDEX);

            mActionBar.setSelectedNavigationItem(selectedIndex);
            mTabsAdapter.notifySelectedPage(selectedIndex);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mIsFirstLaunch = (icicle == null);
        getWindow().setBackgroundDrawable(null);

        mIsFirstLaunch = true;
        mSelectedTab = CLOCK_TAB_INDEX;
        if (icicle != null) {
            mSelectedTab = icicle.getInt(KEY_SELECTED_TAB, CLOCK_TAB_INDEX);
            mLastHourColor = icicle.getInt(KEY_LAST_HOUR_COLOR, UNKNOWN_COLOR_ID);
            if (mLastHourColor != UNKNOWN_COLOR_ID) {
                getWindow().getDecorView().setBackgroundColor(mLastHourColor);
            }
        }

        // Timer receiver may ask the app to go to the timer fragment if a timer expired
        Intent i = getIntent();
        if (i != null) {
            int tab = i.getIntExtra(SELECT_TAB_INTENT_EXTRA, -1);
            if (tab != -1) {
                mSelectedTab = tab;
            }
        }
        initViews();
        setHomeTimeZone();

        // We need to update the system next alarm time on app startup because the
        // user might have clear our data.
        AlarmStateManager.updateNextAlarm(this);
        ExtensionsFactory.init(getAssets());
    }

    @Override
    protected void onResume() {
        super.onResume();

        setBackgroundColor();

        // We only want to show notifications for stopwatch/timer when the app is closed so
        // that we don't have to worry about keeping the notifications in perfect sync with
        // the app.
        Intent stopwatchIntent = new Intent(getApplicationContext(), StopwatchService.class);
        stopwatchIntent.setAction(Stopwatches.KILL_NOTIF);
        startService(stopwatchIntent);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Timers.NOTIF_APP_OPEN, true);
        editor.apply();
        Intent timerIntent = new Intent();
        timerIntent.setAction(Timers.NOTIF_IN_USE_CANCEL);
        sendBroadcast(timerIntent);
    }

    @Override
    public void onPause() {
        Intent intent = new Intent(getApplicationContext(), StopwatchService.class);
        intent.setAction(Stopwatches.SHOW_NOTIF);
        startService(intent);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Timers.NOTIF_APP_OPEN, false);
        editor.apply();
        Utils.showInUseNotifications(this);

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, mActionBar.getSelectedNavigationIndex());
        outState.putInt(KEY_LAST_HOUR_COLOR, mLastHourColor);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // We only want to show it as a menu in landscape, and only for clock/alarm fragment.
        mMenu = menu;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (mActionBar.getSelectedNavigationIndex() == ALARM_TAB_INDEX ||
                    mActionBar.getSelectedNavigationIndex() == CLOCK_TAB_INDEX) {
                // Clear the menu so that it doesn't get duplicate items in case onCreateOptionsMenu
                // was called multiple times.
                menu.clear();
                getMenuInflater().inflate(R.menu.desk_clock_menu, menu);
            }
            // Always return true for landscape, regardless of whether we've inflated the menu, so
            // that when we switch tabs this method will get called and we can inflate the menu.
            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenu(menu);
        return true;
    }

    private void updateMenu(Menu menu) {
        // Hide "help" if we don't have a URI for it.
        MenuItem help = menu.findItem(R.id.menu_item_help);
        if (help != null) {
            Utils.prepareHelpMenuItem(this, help);
        }

        // Hide "lights out" for timer.
        MenuItem nightMode = menu.findItem(R.id.menu_item_night_mode);
        if (mActionBar.getSelectedNavigationIndex() == ALARM_TAB_INDEX) {
            nightMode.setVisible(false);
        } else if (mActionBar.getSelectedNavigationIndex() == CLOCK_TAB_INDEX) {
            nightMode.setVisible(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (processMenuClick(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean processMenuClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_settings:
                startActivity(new Intent(DeskClock.this, SettingsActivity.class));
                return true;
            case R.id.menu_item_help:
                Intent i = item.getIntent();
                if (i != null) {
                    try {
                        startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        // No activity found to match the intent - ignore
                    }
                }
                return true;
            case R.id.menu_item_night_mode:
                startActivity(new Intent(DeskClock.this, ScreensaverActivity.class));
            default:
                break;
        }
        return true;
    }

    /**
     * Insert the local time zone as the Home Time Zone if one is not set
     */
    private void setHomeTimeZone() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String homeTimeZone = prefs.getString(SettingsActivity.KEY_HOME_TZ, "");
        if (!homeTimeZone.isEmpty()) {
            return;
        }
        homeTimeZone = TimeZone.getDefault().getID();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SettingsActivity.KEY_HOME_TZ, homeTimeZone);
        editor.apply();
        Log.v(LOG_TAG, "Setting home time zone to " + homeTimeZone);
    }

    public void registerPageChangedListener(DeskClockFragment frag) {
        if (mTabsAdapter != null) {
            mTabsAdapter.registerPageChangedListener(frag);
        }
    }

    public void unregisterPageChangedListener(DeskClockFragment frag) {
        if (mTabsAdapter != null) {
            mTabsAdapter.unregisterPageChangedListener(frag);
        }
    }

    private void setBackgroundColor() {
        final int duration;
        if (mLastHourColor == UNKNOWN_COLOR_ID) {
            mLastHourColor = getResources().getColor(R.color.default_background);
            duration = BACKGROUND_COLOR_INITIAL_ANIMATION_DURATION_MILLIS;
        } else {
            duration = getResources().getInteger(android.R.integer.config_longAnimTime);
        }
        final int currHourColor = Utils.getCurrentHourColor();
        if (mLastHourColor != currHourColor) {
            final ObjectAnimator animator = ObjectAnimator.ofInt(getWindow().getDecorView(),
                    "backgroundColor", mLastHourColor, currHourColor);
            animator.setDuration(duration);
            animator.setEvaluator(new ArgbEvaluator());
            animator.start();
            mLastHourColor = currHourColor;
        }
    }

    /**
     * Adapter for wrapping together the ActionBar's tab with the ViewPager
     */
    private class TabsAdapter extends FragmentPagerAdapter
            implements ActionBar.TabListener, ViewPager.OnPageChangeListener {

        private static final String KEY_TAB_POSITION = "tab_position";

        final class TabInfo {
            private final Class<?> clss;
            private final Bundle args;

            TabInfo(Class<?> _class, int position) {
                clss = _class;
                args = new Bundle();
                args.putInt(KEY_TAB_POSITION, position);
            }

            public int getPosition() {
                return args.getInt(KEY_TAB_POSITION, 0);
            }
        }

        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();
        ActionBar mMainActionBar;
        Context mContext;
        ViewPager mPager;
        // Used for doing callbacks to fragments.
        HashSet<String> mFragmentTags = new HashSet<String>();

        public TabsAdapter(Activity activity, ViewPager pager) {
            super(activity.getFragmentManager());
            mContext = activity;
            mMainActionBar = activity.getActionBar();
            mPager = pager;
            mPager.setAdapter(this);
            mPager.setOnPageChangeListener(this);
        }

        @Override
        public Fragment getItem(int position) {
            // Because this public method is called outside many times,
            // check if it exits first before creating a new one.
            final String name = makeFragmentName(R.id.desk_clock_pager, position);
            Fragment fragment = getFragmentManager().findFragmentByTag(name);
            if (fragment == null) {
                TabInfo info = mTabs.get(getRtlPosition(position));
                fragment = Fragment.instantiate(mContext, info.clss.getName(), info.args);
                if (fragment instanceof TimerFragment) {
                    ((TimerFragment) fragment).setFabAppearance();
                    ((TimerFragment) fragment).setLeftRightButtonAppearance();
                }
            }
            return fragment;
        }

        /**
         * Copied from:
         * android/frameworks/support/v13/java/android/support/v13/app/FragmentPagerAdapter.java#94
         * Create unique name for the fragment so fragment manager knows it exist.
         */
        private String makeFragmentName(int viewId, int index) {
            return "android:switcher:" + viewId + ":" + index;
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        public void addTab(ActionBar.Tab tab, Class<?> clss, int position) {
            TabInfo info = new TabInfo(clss, position);
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mMainActionBar.addTab(tab);
            notifyDataSetChanged();
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // Do nothing
        }

        @Override
        public void onPageSelected(int position) {
            // Set the page before doing the menu so that onCreateOptionsMenu knows what page it is.
            mMainActionBar.setSelectedNavigationItem(getRtlPosition(position));
            notifyPageChanged(position);

            // Only show the overflow menu for alarm and world clock.
            if (mMenu != null) {
                // Make sure the menu's been initialized.
                if (position == ALARM_TAB_INDEX || position == CLOCK_TAB_INDEX) {
                    mMenu.setGroupVisible(R.id.menu_items, true);
                    onCreateOptionsMenu(mMenu);
                } else {
                    mMenu.setGroupVisible(R.id.menu_items, false);
                }
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            // Do nothing
        }

        @Override
        public void onTabReselected(Tab arg0, FragmentTransaction arg1) {
            // Do nothing
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            final TabInfo info = (TabInfo) tab.getTag();
            final int position = info.getPosition();
            final int rtlSafePosition = getRtlPosition(position);
            mSelectedTab = position;

            if (mIsFirstLaunch && isClockTab(rtlSafePosition)) {
                mLeftButton.setVisibility(View.INVISIBLE);
                mRightButton.setVisibility(View.INVISIBLE);
                mFab.setVisibility(View.VISIBLE);
                mFab.setImageResource(R.drawable.ic_globe);
                mFab.setContentDescription(getString(R.string.button_cities));
                mIsFirstLaunch = false;
            } else {
                DeskClockFragment f = (DeskClockFragment) getItem(rtlSafePosition);
                f.setFabAppearance();
                f.setLeftRightButtonAppearance();
            }
            mPager.setCurrentItem(rtlSafePosition);
        }

        @Override
        public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {
            // Do nothing
        }

        private boolean isClockTab(int rtlSafePosition) {
            final int clockTabIndex = isRtl() ? RTL_CLOCK_TAB_INDEX : CLOCK_TAB_INDEX;
            return rtlSafePosition == clockTabIndex;
        }

        public void notifySelectedPage(int page) {
            notifyPageChanged(page);
        }

        private void notifyPageChanged(int newPage) {
            for (String tag : mFragmentTags) {
                final FragmentManager fm = getFragmentManager();
                DeskClockFragment f = (DeskClockFragment) fm.findFragmentByTag(tag);
                if (f != null) {
                    f.onPageChanged(newPage);
                }
            }
        }

        public void registerPageChangedListener(DeskClockFragment frag) {
            String tag = frag.getTag();
            if (mFragmentTags.contains(tag)) {
                Log.wtf(LOG_TAG, "Trying to add an existing fragment " + tag);
            } else {
                mFragmentTags.add(frag.getTag());
            }
            // Since registering a listener by the fragment is done sometimes after the page
            // was already changed, make sure the fragment gets the current page
            frag.onPageChanged(mMainActionBar.getSelectedNavigationIndex());
        }

        public void unregisterPageChangedListener(DeskClockFragment frag) {
            mFragmentTags.remove(frag.getTag());
        }

    }

    public static abstract class OnTapListener implements OnTouchListener {
        private float mLastTouchX;
        private float mLastTouchY;
        private long mLastTouchTime;
        private final TextView mMakePressedTextView;
        private final int mPressedColor, mGrayColor;
        private final float MAX_MOVEMENT_ALLOWED = 20;
        private final long MAX_TIME_ALLOWED = 500;

        public OnTapListener(Activity activity, TextView makePressedView) {
            mMakePressedTextView = makePressedView;
            mPressedColor = activity.getResources().getColor(Utils.getPressedColorId());
            mGrayColor = activity.getResources().getColor(Utils.getGrayColorId());
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case (MotionEvent.ACTION_DOWN):
                    mLastTouchTime = Utils.getTimeNow();
                    mLastTouchX = e.getX();
                    mLastTouchY = e.getY();
                    if (mMakePressedTextView != null) {
                        mMakePressedTextView.setTextColor(mPressedColor);
                    }
                    break;
                case (MotionEvent.ACTION_UP):
                    float xDiff = Math.abs(e.getX() - mLastTouchX);
                    float yDiff = Math.abs(e.getY() - mLastTouchY);
                    long timeDiff = (Utils.getTimeNow() - mLastTouchTime);
                    if (xDiff < MAX_MOVEMENT_ALLOWED && yDiff < MAX_MOVEMENT_ALLOWED
                            && timeDiff < MAX_TIME_ALLOWED) {
                        if (mMakePressedTextView != null) {
                            v = mMakePressedTextView;
                        }
                        processClick(v);
                        resetValues();
                        return true;
                    }
                    resetValues();
                    break;
                case (MotionEvent.ACTION_MOVE):
                    xDiff = Math.abs(e.getX() - mLastTouchX);
                    yDiff = Math.abs(e.getY() - mLastTouchY);
                    if (xDiff >= MAX_MOVEMENT_ALLOWED || yDiff >= MAX_MOVEMENT_ALLOWED) {
                        resetValues();
                    }
                    break;
                default:
                    resetValues();
            }
            return false;
        }

        private void resetValues() {
            mLastTouchX = -1 * MAX_MOVEMENT_ALLOWED + 1;
            mLastTouchY = -1 * MAX_MOVEMENT_ALLOWED + 1;
            mLastTouchTime = -1 * MAX_TIME_ALLOWED + 1;
            if (mMakePressedTextView != null) {
                mMakePressedTextView.setTextColor(mGrayColor);
            }
        }

        protected abstract void processClick(View v);
    }

    /**
     * Called by the LabelDialogFormat class after the dialog is finished. *
     */
    @Override
    public void onDialogLabelSet(TimerObj timer, String label, String tag) {
        Fragment frag = getFragmentManager().findFragmentByTag(tag);
        if (frag instanceof TimerFragment) {
            ((TimerFragment) frag).setLabel(timer, label);
        }
    }

    /**
     * Called by the LabelDialogFormat class after the dialog is finished. *
     */
    @Override
    public void onDialogLabelSet(Alarm alarm, String label, String tag) {
        Fragment frag = getFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setLabel(alarm, label);
        }
    }

    public int getSelectedTab() {
        return mSelectedTab;
    }

    private boolean isRtl() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL;
    }

    private int getRtlPosition(int position) {
        if (isRtl()) {
            switch (position) {
                case TIMER_TAB_INDEX:
                    return RTL_TIMER_TAB_INDEX;
                case CLOCK_TAB_INDEX:
                    return RTL_CLOCK_TAB_INDEX;
                case STOPWATCH_TAB_INDEX:
                    return RTL_STOPWATCH_TAB_INDEX;
                case ALARM_TAB_INDEX:
                    return RTL_ALARM_TAB_INDEX;
                default:
                    break;
            }
        }
        return position;
    }

    public ImageButton getFab() {
        return mFab;
    }

    public ImageButton getLeftButton() {
        return mLeftButton;
    }

    public ImageButton getRightButton() {
        return mRightButton;
    }
}
