/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.alarmclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.android.deskclock.DeskClock;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.alarms.AlarmNotifications;
import com.android.deskclock.worldclock.Cities;
import com.android.deskclock.worldclock.CitiesActivity;

import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class DigitalAppWidgetProviderNew extends AppWidgetProvider {
    private static final String TAG = "DigitalAppWidgetProviderNew";

    /**
     * Intent to be used for checking if a world clock's date has changed. Must be every fifteen
     * minutes because not all time zones are hour-locked.
     **/
    public static final String ACTION_ON_QUARTER_HOUR = "com.android.deskclock.ON_QUARTER_HOUR";

    // Lazily creating this intent to use with the AlarmManager
    private PendingIntent mPendingIntent;
    // Lazily creating this name to use with the AppWidgetManager
    private ComponentName mComponentName;
    private Handler mHandler=new Handler();

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            if (appWidgetManager != null) {
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(getComponentName(context));
                for (int appWidgetId : appWidgetIds) {
                    float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId);
                    updateClock(context, appWidgetManager, appWidgetId, ratio);
                }
            }
        }
    };

    public DigitalAppWidgetProviderNew() {
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        startAlarmOnQuarterHour(context);
        registerReceiver(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        cancelAlarmOnQuarterHour(context);
        unregisterReceiver(context);
    }

    private void registerReceiver(Context context) {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        context.registerReceiver(mIntentReceiver, filter, null, mHandler);
    }

    private void unregisterReceiver(Context context) {
        context.unregisterReceiver(mIntentReceiver);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (int id : appWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onDeleted: " + id);
            }
            DigitalAppWidgetConfigure.clearPrefs(context, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onReceive: " + action);
        }
        super.onReceive(context, intent);

        if (ACTION_ON_QUARTER_HOUR.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            if (appWidgetManager != null) {
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(getComponentName(context));
                for (int appWidgetId : appWidgetIds) {
                    appWidgetManager.
                            notifyAppWidgetViewDataChanged(appWidgetId,
                                    R.id.digital_appwidget_listview);
                    float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId);
                    updateClock(context, appWidgetManager, appWidgetId, ratio);
                }
            }
            if(!ACTION_ON_QUARTER_HOUR.equals(action)) {
                cancelAlarmOnQuarterHour(context);
            }
            startAlarmOnQuarterHour(context);
        } else if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)) {
            // Refresh the next alarm
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            if (appWidgetManager != null) {
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(getComponentName(context));
                for (int appWidgetId : appWidgetIds) {
                    float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId);
                    updateClock(context, appWidgetManager, appWidgetId, ratio);
                }
            }
        } else if (Cities.WORLDCLOCK_UPDATE_INTENT.equals(action)) {
            // Refresh the world cities list
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            if (appWidgetManager != null) {
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(getComponentName(context));
                for (int appWidgetId : appWidgetIds) {
                    appWidgetManager.
                            notifyAppWidgetViewDataChanged(appWidgetId,
                                    R.id.digital_appwidget_listview);
                }
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onUpdate");
        }
        for (int appWidgetId : appWidgetIds) {
            float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId);
            updateClock(context, appWidgetManager, appWidgetId, ratio);
        }
        startAlarmOnQuarterHour(context);
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        // scale the fonts of the clock to fit inside the new size
        float ratio = WidgetUtils.getScaleRatio(context, newOptions, appWidgetId);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onAppWidgetOptionsChanged = " + ratio);
        }
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        updateClock(context, widgetManager, appWidgetId, ratio);
    }

    public static void updateAfterConfigure(Context context, int appWidgetId) {
        float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateAfterConfigure = " + ratio);
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateClock(context, appWidgetManager, appWidgetId, ratio);
    }

    private static void updateClock(
            Context context, AppWidgetManager appWidgetManager, int appWidgetId, float ratio) {
        boolean showWorldClock = WidgetUtils.isShowingWorldClockList(context, appWidgetId);
        boolean showAlarm = WidgetUtils.isShowingAlarm(context, appWidgetId);
        boolean showDate = WidgetUtils.isShowingDate(context, appWidgetId);
        Typeface clockFont = WidgetUtils.getClockFont(context, appWidgetId);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClock " + appWidgetId + " " + ratio);
        }
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.digital_appwidget_new);

        // Launch clock when clicking on the time in the widget only if not a lock screen widget
        Bundle newOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
        if (newOptions != null &&
                newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
                != AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
            widget.setOnClickPendingIntent(R.id.digital_appwidget,
                    PendingIntent.getActivity(context, 0, new Intent(context, DeskClock.class), 0));
        }

        widget.setViewVisibility(R.id.date, showDate ? View.VISIBLE : View.GONE);
        widget.setViewVisibility(R.id.nextAlarm, showAlarm ? View.VISIBLE : View.GONE);

        // Setup alarm text clock's format and font sizes
        refreshAlarm(context, widget, appWidgetId);
        if (clockFont == null) {
            clockFont = Typeface.create("sans-serif", Typeface.NORMAL);
        }

        // Set today's date format
        CharSequence dateFormat = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                context.getString(R.string.abbrev_wday_month_day_no_year));
        widget.setCharSequence(R.id.date, "setFormat12Hour", dateFormat);
        widget.setCharSequence(R.id.date, "setFormat24Hour", dateFormat);

        SimpleDateFormat sdf = new SimpleDateFormat(DateFormat.is24HourFormat(context) ?
                Utils.get24ModeFormat().toString() :
                Utils.get12ModeFormat(0/*no am/pm*/).toString(), Locale.getDefault());
        String currTime = sdf.format(new Date());
        float fontSize = context.getResources().getDimension(R.dimen.widget_big_font_size);
        final Bitmap textBitmap = WidgetUtils.createTextBitmap(currTime,
                clockFont, fontSize * ratio, Color.WHITE);
        widget.setImageViewBitmap(R.id.the_clock_image, textBitmap);

        if (showWorldClock) {
            // Set up R.id.digital_appwidget_listview to use a remote views adapter
            // That remote views adapter connects to a RemoteViewsService through intent.
            final Intent intent = new Intent(context, DigitalAppWidgetService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            widget.setRemoteAdapter(R.id.digital_appwidget_listview, intent);

            // Set up the click on any world clock to start the Cities Activity
            //TODO: Should this be in the options guard above?
            widget.setPendingIntentTemplate(R.id.digital_appwidget_listview,
                    PendingIntent.
                            getActivity(context, 0, new Intent(context, CitiesActivity.class), 0));

            // Refresh the widget
            appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId, R.id.digital_appwidget_listview);
        } else {
            widget.setViewVisibility(R.id.digital_appwidget_listview, View.GONE);
        }
        refreshAlarm(context, widget, appWidgetId);
        appWidgetManager.updateAppWidget(appWidgetId, widget);
    }

    protected static void refreshAlarm(Context context, RemoteViews widget, int appWidgetId) {
        boolean showAlarm = WidgetUtils.isShowingAlarm(context, appWidgetId);
        if (!showAlarm) {
            return;
        }
        final String nextAlarm = Utils.getNextAlarm(context);
        if (!TextUtils.isEmpty(nextAlarm)) {
            widget.setTextViewText(R.id.nextAlarm,
                    context.getString(R.string.control_set_alarm_with_existing, nextAlarm));
            widget.setViewVisibility(R.id.nextAlarm, View.VISIBLE);
            if (DigitalAppWidgetService.LOGGING) {
                Log.v(TAG, "DigitalWidget sets next alarm string to " + nextAlarm);
            }
        } else  {
            widget.setViewVisibility(R.id.nextAlarm, View.GONE);
            if (DigitalAppWidgetService.LOGGING) {
                Log.v(TAG, "DigitalWidget sets next alarm string to null");
            }
        }
    }

    /**
     * Start an alarm that fires on the next quarter hour to update the world clock city
     * day when the local time or the world city crosses midnight.
     *
     * @param context The context in which the PendingIntent should perform the broadcast.
     */
    private void startAlarmOnQuarterHour(Context context) {
        if (context != null) {
            long onQuarterHour = Utils.getAlarmOnQuarterHour();
            PendingIntent quarterlyIntent = getOnQuarterHourPendingIntent(context);
            AlarmManager alarmManager = ((AlarmManager) context
                    .getSystemService(Context.ALARM_SERVICE));
            if (Utils.isKitKatOrLater()) {
                alarmManager.setExact(AlarmManager.RTC, onQuarterHour, quarterlyIntent);
            } else {
                alarmManager.set(AlarmManager.RTC, onQuarterHour, quarterlyIntent);
            }
            if (DigitalAppWidgetService.LOGGING) {
                Log.v(TAG, "startAlarmOnQuarterHour " + context.toString());
            }
        }
    }


    /**
     * Remove the alarm for the quarter hour update.
     *
     * @param context The context in which the PendingIntent was started to perform the broadcast.
     */
    public void cancelAlarmOnQuarterHour(Context context) {
        if (context != null) {
            PendingIntent quarterlyIntent = getOnQuarterHourPendingIntent(context);
            if (DigitalAppWidgetService.LOGGING) {
                Log.v(TAG, "cancelAlarmOnQuarterHour " + context.toString());
            }
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(
                    quarterlyIntent);
        }
    }

    /**
     * Create the pending intent that is broadcast on the quarter hour.
     *
     * @param context The Context in which this PendingIntent should perform the broadcast.
     * @return a pending intent with an intent unique to DigitalAppWidgetProvider
     */
    private PendingIntent getOnQuarterHourPendingIntent(Context context) {
        if (mPendingIntent == null) {
            mPendingIntent = PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_ON_QUARTER_HOUR), PendingIntent.FLAG_CANCEL_CURRENT);
        }
        return mPendingIntent;
    }

    /**
     * Create the component name for this class
     *
     * @param context The Context in which the widgets for this component are created
     * @return the ComponentName unique to DigitalAppWidgetProvider
     */
    private ComponentName getComponentName(Context context) {
        if (mComponentName == null) {
            mComponentName = new ComponentName(context, getClass());
        }
        return mComponentName;
    }
}
