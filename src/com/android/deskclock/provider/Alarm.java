/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.deskclock.provider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.deskclock.R;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public final class Alarm implements Parcelable, ClockContract.AlarmsColumns {
    /**
     * Alarms start with an invalid id when it hasn't been saved to the database.
     */
    public static final long INVALID_ID = -1;

    /**
     * The default sort order for this table
     */
    private static final String DEFAULT_SORT_ORDER =
            HOUR + ", " +
            MINUTES + " ASC" + ", " +
            _ID + " DESC";

    private static final String[] QUERY_COLUMNS = {
            _ID,
            HOUR,
            MINUTES,
            DAYS_OF_WEEK,
            ENABLED,
            VIBRATE,
            LABEL,
            RINGTONE,
            DELETE_AFTER_USE,
            INCREASING_VOLUME,
            PRE_ALARM,
            ALARM_VOLUME,
            PRE_ALARM_VOLUME,
            PRE_ALARM_TIME,
            PRE_ALARM_RINGTONE,
            RANDOM_MODE
    };

    /**
     * These save calls to cursor.getColumnIndexOrThrow()
     * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
     */
    private static final int ID_INDEX = 0;
    private static final int HOUR_INDEX = 1;
    private static final int MINUTES_INDEX = 2;
    private static final int DAYS_OF_WEEK_INDEX = 3;
    private static final int ENABLED_INDEX = 4;
    private static final int VIBRATE_INDEX = 5;
    private static final int LABEL_INDEX = 6;
    private static final int RINGTONE_INDEX = 7;
    private static final int DELETE_AFTER_USE_INDEX = 8;
    private static final int INCREASING_VOLUME_INDEX = 9;
    private static final int PRE_ALARM_INDEX = 10;
    private static final int ALARM_VOLUME_INDEX = 11;
    private static final int PRE_ALARM_VOLUME_INDEX = 12;
    private static final int PRE_ALARM_TIME_INDEX = 13;
    private static final int PRE_ALARM_RINGTONE_INDEX = 14;
    private static final int RANDOM_MODE_INDEX = 15;
    private static final int COLUMN_COUNT = RANDOM_MODE_INDEX + 1;

    public static ContentValues createContentValues(Alarm alarm) {
        ContentValues values = new ContentValues(COLUMN_COUNT);
        if (alarm.id != INVALID_ID) {
            values.put(ClockContract.AlarmsColumns._ID, alarm.id);
        }

        values.put(ENABLED, alarm.enabled ? 1 : 0);
        values.put(HOUR, alarm.hour);
        values.put(MINUTES, alarm.minutes);
        values.put(DAYS_OF_WEEK, alarm.daysOfWeek.getBitSet());
        values.put(VIBRATE, alarm.vibrate ? 1 : 0);
        values.put(LABEL, alarm.label);
        values.put(DELETE_AFTER_USE, alarm.deleteAfterUse);
        values.put(INCREASING_VOLUME, alarm.increasingVolume);

        if (alarm.alert == null) {
            // We want to put null, so default alarm changes
            values.putNull(RINGTONE);
        } else {
            values.put(RINGTONE, alarm.alert.toString());
        }
        values.put(PRE_ALARM, alarm.preAlarm ? 1 : 0);
        values.put(ALARM_VOLUME, alarm.alarmVolume);
        values.put(PRE_ALARM_VOLUME, alarm.preAlarmVolume);
        values.put(PRE_ALARM_TIME, alarm.preAlarmTime);
        if (alarm.preAlarmAlert == null) {
            // We want to put null, so default alarm changes
            values.putNull(PRE_ALARM_RINGTONE);
        } else {
            values.put(PRE_ALARM_RINGTONE, alarm.preAlarmAlert.toString());
        }
        values.put(RANDOM_MODE, alarm.randomMode);
        return values;
    }

    public static Intent createIntent(String action, long alarmId) {
        return new Intent(action).setData(getUri(alarmId));
    }

    public static Intent createIntent(Context context, Class<?> cls, long alarmId) {
        return new Intent(context, cls).setData(getUri(alarmId));
    }

    public static Uri getUri(long alarmId) {
        return ContentUris.withAppendedId(CONTENT_URI, alarmId);
    }

    public static long getId(Uri contentUri) {
        return ContentUris.parseId(contentUri);
    }

    /**
     * Get alarm cursor loader for all alarms.
     *
     * @param context to query the database.
     * @return cursor loader with all the alarms.
     */
    public static CursorLoader getAlarmsCursorLoader(Context context) {
        return new CursorLoader(context, ClockContract.AlarmsColumns.CONTENT_URI,
                QUERY_COLUMNS, null, null, DEFAULT_SORT_ORDER);
    }

    /**
     * Get alarm by id.
     *
     * @param contentResolver to perform the query on.
     * @param alarmId for the desired alarm.
     * @return alarm if found, null otherwise
     */
    public static Alarm getAlarm(ContentResolver contentResolver, long alarmId) {
        Cursor cursor = contentResolver.query(getUri(alarmId), QUERY_COLUMNS, null, null, null);
        Alarm result = null;
        if (cursor == null) {
            return result;
        }

        try {
            if (cursor.moveToFirst()) {
                result = new Alarm(cursor);
            }
        } finally {
            cursor.close();
        }

        return result;
    }

    /**
     * Get all alarms given conditions.
     *
     * @param contentResolver to perform the query on.
     * @param selection A filter declaring which rows to return, formatted as an
     *         SQL WHERE clause (excluding the WHERE itself). Passing null will
     *         return all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *         replaced by the values from selectionArgs, in the order that they
     *         appear in the selection. The values will be bound as Strings.
     * @return list of alarms matching where clause or empty list if none found.
     */
    public static List<Alarm> getAlarms(ContentResolver contentResolver,
            String selection, String ... selectionArgs) {
        Cursor cursor  = contentResolver.query(CONTENT_URI, QUERY_COLUMNS,
                selection, selectionArgs, null);
        List<Alarm> result = new LinkedList<Alarm>();
        if (cursor == null) {
            return result;
        }

        try {
            if (cursor.moveToFirst()) {
                do {
                    result.add(new Alarm(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        return result;
    }

    public static Alarm addAlarm(ContentResolver contentResolver, Alarm alarm) {
        ContentValues values = createContentValues(alarm);
        Uri uri = contentResolver.insert(CONTENT_URI, values);
        alarm.id = getId(uri);
        return alarm;
    }

    public static boolean updateAlarm(ContentResolver contentResolver, Alarm alarm) {
        if (alarm.id == Alarm.INVALID_ID) return false;
        ContentValues values = createContentValues(alarm);
        long rowsUpdated = contentResolver.update(getUri(alarm.id), values, null, null);
        return rowsUpdated == 1;
    }

    public static boolean deleteAlarm(ContentResolver contentResolver, long alarmId) {
        if (alarmId == INVALID_ID) return false;
        int deletedRows = contentResolver.delete(getUri(alarmId), "", null);
        return deletedRows == 1;
    }

    public static final Parcelable.Creator<Alarm> CREATOR = new Parcelable.Creator<Alarm>() {
        public Alarm createFromParcel(Parcel p) {
            return new Alarm(p);
        }

        public Alarm[] newArray(int size) {
            return new Alarm[size];
        }
    };

    // Public fields
    // TODO: Refactor instance names
    public long id;
    public boolean enabled;
    public int hour;
    public int minutes;
    public DaysOfWeek daysOfWeek;
    public boolean vibrate;
    public String label;
    public Uri alert;
    public boolean deleteAfterUse;
    private int increasingVolume;
    public boolean preAlarm;
    public int alarmVolume;
    public int preAlarmVolume;
    public int preAlarmTime;
    public Uri preAlarmAlert;
    private int randomMode;

    // Creates a default alarm at the current time.
    public Alarm() {
        this(0, 0);
    }

    public Alarm(int hour, int minutes) {
        this.id = INVALID_ID;
        this.hour = hour;
        this.minutes = minutes;
        this.vibrate = true;
        this.daysOfWeek = new DaysOfWeek(0);
        this.label = "";
        this.alert = null;
        this.deleteAfterUse = false;
        this.increasingVolume = 0;
        this.preAlarm = false;
        this.alarmVolume = -1;
        this.preAlarmVolume = -1;
        this.preAlarmTime = AlarmInstance.DEFAULT_PRE_ALARM_TIME;
        this.preAlarmAlert = null;
        this.randomMode = 0;
    }

    public Alarm(Cursor c) {
        id = c.getLong(ID_INDEX);
        enabled = c.getInt(ENABLED_INDEX) == 1;
        hour = c.getInt(HOUR_INDEX);
        minutes = c.getInt(MINUTES_INDEX);
        daysOfWeek = new DaysOfWeek(c.getInt(DAYS_OF_WEEK_INDEX));
        vibrate = c.getInt(VIBRATE_INDEX) == 1;
        label = c.getString(LABEL_INDEX);
        deleteAfterUse = c.getInt(DELETE_AFTER_USE_INDEX) == 1;
        increasingVolume = c.getInt(INCREASING_VOLUME_INDEX);
        if (!c.isNull(RINGTONE_INDEX)) {
            String r = c.getString(RINGTONE_INDEX);
            alert = Uri.parse(r);
        }
        preAlarm = c.getInt(PRE_ALARM_INDEX) == 1;
        alarmVolume = c.getInt(ALARM_VOLUME_INDEX);
        preAlarmVolume = c.getInt(PRE_ALARM_VOLUME_INDEX);
        preAlarmTime = c.getInt(PRE_ALARM_TIME_INDEX);
        if (!c.isNull(PRE_ALARM_RINGTONE_INDEX)) {
            String r = c.getString(PRE_ALARM_RINGTONE_INDEX);
            preAlarmAlert = Uri.parse(r);
        }
        randomMode = c.getInt(RANDOM_MODE_INDEX);
    }

    Alarm(Parcel p) {
        id = p.readLong();
        enabled = p.readInt() == 1;
        hour = p.readInt();
        minutes = p.readInt();
        daysOfWeek = new DaysOfWeek(p.readInt());
        vibrate = p.readInt() == 1;
        label = p.readString();
        alert = (Uri) p.readParcelable(null);
        deleteAfterUse = p.readInt() == 1;
        increasingVolume = p.readInt();
        preAlarm = p.readInt() == 1;
        alarmVolume = p.readInt();
        preAlarmVolume = p.readInt();
        preAlarmTime = p.readInt();
        preAlarmAlert = (Uri) p.readParcelable(null);
        randomMode = p.readInt();
    }

    public String getLabelOrDefault(Context context) {
        if (label == null || label.length() == 0) {
            return context.getString(R.string.default_label);
        }
        return label;
    }

    public void writeToParcel(Parcel p, int flags) {
        p.writeLong(id);
        p.writeInt(enabled ? 1 : 0);
        p.writeInt(hour);
        p.writeInt(minutes);
        p.writeInt(daysOfWeek.getBitSet());
        p.writeInt(vibrate ? 1 : 0);
        p.writeString(label);
        p.writeParcelable(alert, flags);
        p.writeInt(deleteAfterUse ? 1 : 0);
        p.writeInt(increasingVolume);
        p.writeInt(preAlarm ? 1 : 0);
        p.writeInt(alarmVolume);
        p.writeInt(preAlarmVolume);
        p.writeInt(preAlarmTime);
        p.writeParcelable(preAlarmAlert, flags);
        p.writeInt(randomMode);
    }

    public int describeContents() {
        return 0;
    }

    public AlarmInstance createInstanceAfter(Calendar time) {
        Calendar nextInstanceTime = Calendar.getInstance();
        nextInstanceTime.set(Calendar.YEAR, time.get(Calendar.YEAR));
        nextInstanceTime.set(Calendar.MONTH, time.get(Calendar.MONTH));
        nextInstanceTime.set(Calendar.DAY_OF_MONTH, time.get(Calendar.DAY_OF_MONTH));
        nextInstanceTime.set(Calendar.HOUR_OF_DAY, hour);
        nextInstanceTime.set(Calendar.MINUTE, minutes);
        nextInstanceTime.set(Calendar.SECOND, 0);
        nextInstanceTime.set(Calendar.MILLISECOND, 0);

        // If we are still behind the passed in time, then add a day
        if (nextInstanceTime.getTimeInMillis() <= time.getTimeInMillis()) {
            nextInstanceTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        // The day of the week might be invalid, so find next valid one
        int addDays = daysOfWeek.calculateDaysToNextAlarm(nextInstanceTime);
        if (addDays > 0) {
            nextInstanceTime.add(Calendar.DAY_OF_WEEK, addDays);
        }

        AlarmInstance result = new AlarmInstance(nextInstanceTime, id);
        result.mVibrate = vibrate;
        result.mLabel = label;
        result.mRingtone = alert;
        result.setIncreasingVolume(increasingVolume);
        result.mPreAlarm = preAlarm;
        result.mAlarmVolume = alarmVolume;
        result.mPreAlarmVolume = preAlarmVolume;
        result.mPreAlarmTime = preAlarmTime;
        result.mPreAlarmRingtone = preAlarmAlert;
        result.setRandomMode(randomMode);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Alarm)) return false;
        final Alarm other = (Alarm) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(id).hashCode();
    }

    @Override
    public String toString() {
        return "Alarm{" +
                "alert=" + alert +
                ", id=" + id +
                ", enabled=" + enabled +
                ", hour=" + hour +
                ", minutes=" + minutes +
                ", daysOfWeek=" + daysOfWeek +
                ", vibrate=" + vibrate +
                ", label='" + label + '\'' +
                ", deleteAfterUse=" + deleteAfterUse +
                ", increasingVolume=" + increasingVolume +
                ", preAlarm=" + preAlarm +
                ", alarmVolume=" + alarmVolume +
                ", preAlarmVolume=" + preAlarmVolume +
                ", preAlarmTime=" + preAlarmTime +
                ", preAlarmAlert=" + preAlarmAlert +
                ", randomMode=" + randomMode +
                '}';
    }

    public boolean getIncreasingVolume(boolean preAlarm) {
        if (preAlarm) {
            return increasingVolume == AlarmInstance.ALARM_OPTION_PREALARM_ONLY
            || increasingVolume == AlarmInstance.ALARM_OPTION_BOTH;
        } else {
            return increasingVolume == AlarmInstance.ALARM_OPTION_MAIN_ONLY
            || increasingVolume == AlarmInstance.ALARM_OPTION_BOTH;
        }
    }

    public void setIncreasingVolume(int value) {
        increasingVolume = value;
    }

    public void setIncreasingVolume(boolean preAlarm, boolean value) {
        if (preAlarm) {
            if (value) {
                if (getIncreasingVolume(false)){
                    increasingVolume = AlarmInstance.ALARM_OPTION_BOTH;
                } else {
                    increasingVolume = AlarmInstance.ALARM_OPTION_PREALARM_ONLY;
                }
            } else {
                if (getIncreasingVolume(false)){
                    increasingVolume = AlarmInstance.ALARM_OPTION_MAIN_ONLY;
                } else {
                    increasingVolume = AlarmInstance.ALARM_OPTION_OFF;
                }
            }
        } else {
            if (value) {
                if (getIncreasingVolume(true)){
                    increasingVolume = AlarmInstance.ALARM_OPTION_BOTH;
                } else {
                    increasingVolume = AlarmInstance.ALARM_OPTION_MAIN_ONLY;
                }
            } else {
                if (getIncreasingVolume(true)){
                    increasingVolume = AlarmInstance.ALARM_OPTION_PREALARM_ONLY;
                } else {
                    increasingVolume = AlarmInstance.ALARM_OPTION_OFF;
                }
            }
        }
    }

    public boolean getRandomMode(boolean preAlarm) {
        if (preAlarm) {
            return randomMode == AlarmInstance.ALARM_OPTION_PREALARM_ONLY
            || randomMode == AlarmInstance.ALARM_OPTION_BOTH;
        } else {
            return randomMode == AlarmInstance.ALARM_OPTION_MAIN_ONLY
            || randomMode == AlarmInstance.ALARM_OPTION_BOTH;
        }
    }

    public void setRandomMode(boolean preAlarm, boolean value) {
        if (preAlarm) {
            if (value) {
                if (getRandomMode(false)){
                    randomMode = AlarmInstance.ALARM_OPTION_BOTH;
                } else {
                    randomMode = AlarmInstance.ALARM_OPTION_PREALARM_ONLY;
                }
            } else {
                if (getRandomMode(false)){
                    randomMode = AlarmInstance.ALARM_OPTION_MAIN_ONLY;
                } else {
                    randomMode = AlarmInstance.ALARM_OPTION_OFF;
                }
            }
        } else {
            if (value) {
                if (getRandomMode(true)){
                    randomMode = AlarmInstance.ALARM_OPTION_BOTH;
                } else {
                    randomMode = AlarmInstance.ALARM_OPTION_MAIN_ONLY;
                }
            } else {
                if (getRandomMode(true)){
                    randomMode = AlarmInstance.ALARM_OPTION_PREALARM_ONLY;
                } else {
                    randomMode = AlarmInstance.ALARM_OPTION_OFF;
                }
            }
        }
    }

    public boolean isSilentAlarm() {
        boolean silentAlarm = alert != null && Alarm.NO_RINGTONE_URI.equals(alert);
        return silentAlarm;
    }

    public void setSilentAlarm() {
        alert = Alarm.NO_RINGTONE_URI;
        alarmVolume = -1;
    }

    public void setDefaultAlarm(Context context) {
        alert = getDefaultAlarmUri(context);
    }

    public void setDefaultPreAlarm(Context context) {
        preAlarmAlert = getDefaultAlarmUri(context);
        preAlarm = true;
    }

    public void disablePreAlarm() {
        preAlarmAlert = Alarm.NO_RINGTONE_URI;
        preAlarmVolume = -1;
        preAlarm = false;
    }

    private Uri getDefaultAlarmUri(Context context) {
        Uri alert = RingtoneManager.getActualDefaultRingtoneUri(context,
                    RingtoneManager.TYPE_ALARM);
        if (alert == null) {
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        return alert;
    }

    private String getRingToneTitle(Context context, boolean preAlarm) {
        Ringtone ringTone = RingtoneManager.getRingtone(context, preAlarm ? preAlarmAlert : alert);
        if (ringTone != null) {
            return ringTone.getTitle(context);
        }
        return null;
    }

    public boolean isFallbackRingtone(Context context, boolean preAlarm) {
        String unknownRingToneStr = context.getString(R.string.ringtone_unknown);
        String title = getRingToneTitle(context, preAlarm);
        return title == null || title.contains(unknownRingToneStr);
    }
}
