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
import android.app.Service;
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
import android.os.IBinder;
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

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class CustomAppWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "CustomAppWidgetProvider";

    // there is no other way to use ACTION_TIME_TICK then this
    public static class ClockUpdateService extends Service {
        private final BroadcastReceiver mClockChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DigitalAppWidgetService.LOGGING) {
                    Log.i(TAG, "ClockUpdateService:onReceive: " + action);
                }
                updateAllClocks(context);
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_TIME_TICK);
            registerReceiver(mClockChangedReceiver, intentFilter);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(mClockChangedReceiver);
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "ClockUpdateService:onDestroy");
            }
        }
    }

    public CustomAppWidgetProvider() {
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        context.startService(new Intent(context, ClockUpdateService.class));
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        context.stopService(new Intent(context, ClockUpdateService.class));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (int id : appWidgetIds) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onDeleted: " + id);
            }
            CustomAppWidgetConfigure.clearPrefs(context, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)) {
            if (DigitalAppWidgetService.LOGGING) {
                Log.i(TAG, "onReceive: " + action);
            }
            updateAllClocks(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onUpdate");
        }
        for (int appWidgetId : appWidgetIds) {
            float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId, false);
            updateClock(context, appWidgetManager, appWidgetId, ratio);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        context.startService(new Intent(context, ClockUpdateService.class));
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        // scale the fonts of the clock to fit inside the new size
        float ratio = WidgetUtils.getScaleRatio(context, newOptions, appWidgetId, false);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "onAppWidgetOptionsChanged = " + ratio);
        }
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        updateClock(context, widgetManager, appWidgetId, ratio);
    }

    public static void updateAfterConfigure(Context context, int appWidgetId) {
        float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId, false);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateAfterConfigure = " + ratio);
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateClock(context, appWidgetManager, appWidgetId, ratio);
    }

    public static void updateAllClocks(Context context) {
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClocks at = " + new Date());
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetManager != null) {
            ComponentName componentName = new ComponentName(context, CustomAppWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            for (int appWidgetId : appWidgetIds) {
                float ratio = WidgetUtils.getScaleRatio(context, null, appWidgetId, false);
                updateClock(context, appWidgetManager, appWidgetId, ratio);
            }
        }
    }

    private static void updateClock(
            Context context, AppWidgetManager appWidgetManager, int appWidgetId, float ratio) {
        boolean showAlarm = WidgetUtils.isShowingAlarm(context, appWidgetId);
        boolean showDate = WidgetUtils.isShowingDate(context, appWidgetId);
        Typeface clockFont = WidgetUtils.getClockFont(context, appWidgetId);
        int clockColor = WidgetUtils.getClockColor(context, appWidgetId);
        boolean clockShadow = WidgetUtils.isClockShadow(context, appWidgetId);

        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "updateClock " + appWidgetId + " " + ratio);
        }
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.custom_appwidget);

        // Launch clock when clicking on the time in the widget only if not a lock screen widget
        Bundle newOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
        if (newOptions != null &&
                newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
                != AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
            widget.setOnClickPendingIntent(R.id.digital_appwidget,
                    PendingIntent.getActivity(context, 0, new Intent(context, DeskClock.class), 0));
        }

        widget.setViewVisibility(R.id.the_date_image, showDate ? View.VISIBLE : View.GONE);
        widget.setViewVisibility(R.id.nextAlarm, showAlarm ? View.VISIBLE : View.GONE);

        //widget.setTextColor(R.id.nextAlarm, clockColor);

        CharSequence timeFormat = DateFormat.is24HourFormat(context) ?
                Utils.get24ModeFormat() :
                Utils.get12ModeFormat(0/*no am/pm*/);
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "timeFormat " + timeFormat);
        }
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat.toString(), Locale.getDefault());
        String currTime = sdf.format(new Date());
        float fontSize = context.getResources().getDimension(R.dimen.widget_custom_font_size);
        final Bitmap textBitmap = WidgetUtils.createTextBitmap(currTime,
                clockFont, fontSize * ratio, clockColor, clockShadow, -1);
        widget.setImageViewBitmap(R.id.the_clock_image, textBitmap);

        final String nextAlarm = Utils.getNextAlarm(context);
        boolean hasAlarm = !TextUtils.isEmpty(nextAlarm);

        if (showAlarm) {
            refreshAlarm(context, widget);
        }
        if (showDate) {
            updateDate(context, widget, clockColor, clockShadow, showAlarm, hasAlarm);
        }
        appWidgetManager.updateAppWidget(appWidgetId, widget);
    }

    private static void updateDate(Context context, RemoteViews widget, int clockColor, boolean clockShadow, boolean showAlarm, boolean hasAlarm) {
        CharSequence dateFormat = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                 context.getString((showAlarm && hasAlarm) ? R.string.abbrev_wday_month_day_no_year :
                 R.string.full_wday_month_day_no_year));
        if (DigitalAppWidgetService.LOGGING) {
            Log.i(TAG, "dateFormat " + dateFormat);
        }

        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat.toString(), Locale.getDefault());
        String currDate = sdf.format(new Date()).toUpperCase();
        float fontSize = context.getResources().getDimension(R.dimen.custom_widget_label_font_size);
        Typeface dateFont = Typeface.create("sans-serif", Typeface.NORMAL);
        final Bitmap textBitmap = WidgetUtils.createTextBitmap(currDate,
                dateFont, fontSize, clockColor, clockShadow, 0.15f);
        widget.setImageViewBitmap(R.id.the_date_image, textBitmap);
    }

    protected static void refreshAlarm(Context context, RemoteViews widget) {
        final String nextAlarm = Utils.getNextAlarm(context).toUpperCase();
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
}
