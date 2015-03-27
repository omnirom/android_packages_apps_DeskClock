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

package com.android.deskclock.timer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.deskclock.R;
import com.android.deskclock.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TimerObj implements Parcelable {

    public static final String KEY_NEXT_TIMER_ID = "next_timer_id";

    private static final String TAG = "TimerObj";
    // Max timer length is 9 hours + 99 minutes + 9 seconds
    public static final long MAX_TIMER_LENGTH = (9 * 3600 + 99 * 60  + 99) * 1000;
    public static final long MINUTE_IN_MILLIS = 60 * 1000;

    public int mTimerId;             // Unique id
    public long mStartTime;          // With mTimeLeft , used to calculate the correct time
    public long mTimeLeft;           // in the timer.
    public long mOriginalLength;     // length set at start of timer and by +1 min after times up
    public long mSetupLength;        // length set at start of timer
    public TimerListItem mView;
    public int mState;
    public String mLabel;
    public boolean mDeleteAfterUse;

    public static final int STATE_RUNNING = 1;
    public static final int STATE_STOPPED = 2;
    public static final int STATE_TIMESUP = 3;
    public static final int STATE_DONE = 4;
    public static final int STATE_RESTART = 5;
    public static final int STATE_DELETED = 6;

    private static final String PREF_TIMER_ID = "timer_id_";
    private static final String PREF_START_TIME  = "timer_start_time_";
    private static final String PREF_TIME_LEFT = "timer_time_left_";
    private static final String PREF_ORIGINAL_TIME = "timer_original_timet_";
    private static final String PREF_SETUP_TIME = "timer_setup_timet_";
    private static final String PREF_STATE = "timer_state_";
    private static final String PREF_LABEL = "timer_label_";
    private static final String PREF_DELETE_AFTER_USE = "delete_after_use_";

    private static final String PREF_TIMERS_LIST = "timers_list";

    public static final Parcelable.Creator<TimerObj> CREATOR = new Parcelable.Creator<TimerObj>() {
        @Override
        public TimerObj createFromParcel(Parcel p) {
            return new TimerObj(p);
        }

        @Override
        public TimerObj[] newArray(int size) {
            return new TimerObj[size];
        }
    };

    public void writeToSharedPref(SharedPreferences prefs) {
        final SharedPreferences.Editor editor = prefs.edit();
        final String id = Integer.toString(mTimerId);
        editor.putInt(PREF_TIMER_ID + id, mTimerId);
        editor.putLong(PREF_START_TIME + id, mStartTime);
        editor.putLong (PREF_TIME_LEFT + id, mTimeLeft);
        editor.putLong (PREF_ORIGINAL_TIME + id, mOriginalLength);
        editor.putLong (PREF_SETUP_TIME + id, mSetupLength);
        editor.putInt(PREF_STATE + id, mState);
        final Set <String> timersList = prefs.getStringSet(PREF_TIMERS_LIST, new HashSet<String>());
        timersList.add(id);
        editor.putStringSet(PREF_TIMERS_LIST, timersList);
        editor.putString(PREF_LABEL + id, mLabel);
        editor.putBoolean(PREF_DELETE_AFTER_USE + id, mDeleteAfterUse);
        editor.apply();
    }

    public void readFromSharedPref(SharedPreferences prefs) {
        String id = Integer.toString(mTimerId);
        String key = PREF_START_TIME + id;
        mStartTime = prefs.getLong(key, 0);
        key = PREF_TIME_LEFT + id;
        mTimeLeft = prefs.getLong(key, 0);
        key = PREF_ORIGINAL_TIME + id;
        mOriginalLength = prefs.getLong(key, 0);
        key = PREF_SETUP_TIME + id;
        mSetupLength = prefs.getLong(key, 0);
        key = PREF_STATE + id;
        mState = prefs.getInt(key, 0);
        key = PREF_LABEL + id;
        mLabel = prefs.getString(key, "");
        key = PREF_DELETE_AFTER_USE + id;
        mDeleteAfterUse = prefs.getBoolean(key, false);
    }

    public void deleteFromSharedPref(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        String key = PREF_TIMER_ID + Integer.toString(mTimerId);
        String id = Integer.toString(mTimerId);
        editor.remove (key);
        key = PREF_START_TIME + id;
        editor.remove (key);
        key = PREF_TIME_LEFT + id;
        editor.remove (key);
        key = PREF_ORIGINAL_TIME + id;
        editor.remove (key);
        key = PREF_SETUP_TIME + id;
        editor.remove (key);
        key = PREF_STATE + id;
        editor.remove (key);
        Set <String> timersList = prefs.getStringSet(PREF_TIMERS_LIST, new HashSet<String>());
        timersList.remove(id);
        editor.putStringSet(PREF_TIMERS_LIST, timersList);
        key = PREF_LABEL + id;
        editor.remove(key);
        key = PREF_DELETE_AFTER_USE + id;
        editor.remove(key);
        if (timersList.isEmpty()) {
            editor.remove(KEY_NEXT_TIMER_ID);
        }
        editor.commit();
        //dumpTimersFromSharedPrefs(prefs);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTimerId);
        dest.writeLong(mStartTime);
        dest.writeLong(mTimeLeft);
        dest.writeLong(mOriginalLength);
        dest.writeLong(mSetupLength);
        dest.writeInt(mState);
        dest.writeString(mLabel);
    }

    public TimerObj(Parcel p) {
        mTimerId = p.readInt();
        mStartTime = p.readLong();
        mTimeLeft = p.readLong();
        mOriginalLength = p.readLong();
        mSetupLength = p.readLong();
        mState = p.readInt();
        mLabel = p.readString();
    }

    private TimerObj() {
        this(0 /* timerLength */, 0 /* timerId */);
    }

    public TimerObj(long timerLength, int timerId) {
      init(timerLength, timerId);
    }

    public TimerObj(long timerLength, Context context) {
        init(timerLength, getNextTimerId(context));
    }

    public TimerObj(long length, String label, Context context) {
        this(length, context);
        mLabel = label != null ? label : "";
    }

    private void init (long length, int timerId) {
        /* TODO: mTimerId must avoid StopwatchService.NOTIFICATION_ID,
         * TimerReceiver.IN_USE_NOTIFICATION_ID, and alarm ID's (which seem to be 1, 2, ..)
         */
        mTimerId = timerId;
        mStartTime = Utils.getTimeNow();
        mTimeLeft = mOriginalLength = mSetupLength = length;
        mLabel = "";
    }

    private int getNextTimerId(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int nextTimerId;
        synchronized (TimerObj.class) {
            nextTimerId = prefs.getInt(KEY_NEXT_TIMER_ID, 0);
            prefs.edit().putInt(KEY_NEXT_TIMER_ID, nextTimerId + 1).apply();
        }
        return nextTimerId;
    }

    public long updateTimeLeft(boolean forceUpdate) {
        if (isTicking() || forceUpdate) {
            long millis = Utils.getTimeNow();
            mTimeLeft = mOriginalLength - (millis - mStartTime);
        }
        return mTimeLeft;
    }

    public String getLabelOrDefault(Context context) {
        return (mLabel == null || mLabel.length() == 0) ? context.getString(
                R.string.timer_notification_label)
                : mLabel;
    }

    public boolean isTicking() {
        return mState == STATE_RUNNING || mState == STATE_TIMESUP;
    }

    public boolean isInUse() {
        return mState == STATE_RUNNING || mState == STATE_STOPPED;
    }

    public void addTime(long time) {
        mTimeLeft = mOriginalLength - (Utils.getTimeNow() - mStartTime);
        if (mTimeLeft < MAX_TIMER_LENGTH - time) {
                mOriginalLength += time;
        }
    }

    public boolean getDeleteAfterUse() {
        return mDeleteAfterUse;
    }

    public long getTimesupTime() {
        return mStartTime + mOriginalLength;
    }


    public static void getTimersFromSharedPrefs(
            SharedPreferences prefs, ArrayList<TimerObj> timers) {
        Object[] timerStrings =
                prefs.getStringSet(PREF_TIMERS_LIST, new HashSet<String>()).toArray();
        if (timerStrings.length > 0) {
            for (int i = 0; i < timerStrings.length; i++) {
                TimerObj t = new TimerObj();
                t.mTimerId = Integer.parseInt((String)timerStrings[i]);
                t.readFromSharedPref(prefs);
                timers.add(t);
            }
            Collections.sort(timers, new Comparator<TimerObj>() {
                @Override
                public int compare(TimerObj timerObj1, TimerObj timerObj2) {
                   return timerObj1.mTimerId - timerObj2.mTimerId;
                }
            });
        }
    }

    public static void getTimersFromSharedPrefs(
            SharedPreferences prefs, ArrayList<TimerObj> timers, int match) {
        Object[] timerStrings = prefs.getStringSet(PREF_TIMERS_LIST, new HashSet<String>())
                .toArray();
        if (timerStrings.length > 0) {
            for (int i = 0; i < timerStrings.length; i++) {
                TimerObj t = new TimerObj();
                t.mTimerId = Integer.parseInt((String) timerStrings[i]);
                t.readFromSharedPref(prefs);
                if (t.mState == match) {
                    timers.add(t);
                }
            }
        }
    }

    public static void putTimersInSharedPrefs(
            SharedPreferences prefs, ArrayList<TimerObj> timers) {
        if (timers.size() > 0) {
            for (int i = 0; i < timers.size(); i++) {
                timers.get(i).writeToSharedPref(prefs);
            }
        }
    }

    public static void dumpTimersFromSharedPrefs(
            SharedPreferences prefs) {
        Object[] timerStrings =
                prefs.getStringSet(PREF_TIMERS_LIST, new HashSet<String>()).toArray();
        Log.v(TAG,"--------------------- timers list in shared prefs");
        if (timerStrings.length > 0) {
            for (int i = 0; i < timerStrings.length; i++) {
                int id = Integer.parseInt((String)timerStrings[i]);
                Log.v(TAG,"---------------------timer  " + (i + 1) + ": id - " + id);
            }
        }
    }

    public static void resetTimersInSharedPrefs(SharedPreferences prefs) {
        ArrayList<TimerObj> timers = new  ArrayList<TimerObj>();
        getTimersFromSharedPrefs(prefs, timers);
        Iterator<TimerObj> i = timers.iterator();
        while(i.hasNext()) {
            TimerObj t = i.next();
            t.mState = TimerObj.STATE_RESTART;
            t.mTimeLeft = t. mOriginalLength = t.mSetupLength;
            t.writeToSharedPref(prefs);
        }
    }

}
