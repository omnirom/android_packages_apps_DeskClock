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

package com.android.alarmclock;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.text.TextPaint;
import android.widget.RemoteViews;
import android.widget.RemoteViews.RemoteView;
import android.widget.TextClock;

import com.android.deskclock.R;
import com.android.deskclock.Utils;

public class WidgetUtils {
    static final String TAG = "WidgetUtils";

    public static void setClockSize(Context context, RemoteViews clock, float scale) {
        float fontSize = context.getResources().getDimension(R.dimen.widget_big_font_size);
        clock.setTextViewTextSize(
                R.id.the_clock, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
    }

    // Calculate the scale factor of the fonts in the widget
    public static float getScaleRatio(Context context, Bundle options, int id, boolean showWorldClock) {
        if (options == null) {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
            if (widgetManager == null) {
                // no manager , do no scaling
                return 1f;
            }
            options = widgetManager.getAppWidgetOptions(id);
        }
        if (options != null) {
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            if (minWidth == 0) {
                // No data , do no scaling
                return 1f;
            }
            boolean timeOnly = !isShowingAlarm(context, id) &&  !isShowingDate(context, id) && !showWorldClock;
            Resources res = context.getResources();
            float density = res.getDisplayMetrics().density;
            // Check if the height could introduce a font size constraint
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            float ratio = 1;
            if (minHeight > 0 && (density * minHeight)
                    < res.getDimension(timeOnly ? R.dimen.min_custom_widget_height : R.dimen.min_digital_widget_height)) {
                ratio = Math.min(ratio, getHeightScaleRatio(context, options, id, showWorldClock));
            }
            return (ratio > 1) ? 1 : ratio;
        }
        return 1;
    }

    // Calculate the scale factor of the fonts in the list of  the widget using the widget height
    private static float getHeightScaleRatio(Context context, Bundle options, int id, boolean showWorldClock) {
        if (options == null) {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
            if (widgetManager == null) {
                // no manager , do no scaling
                return 1f;
            }
            options = widgetManager.getAppWidgetOptions(id);
        }
        if (options != null) {
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            if (minHeight == 0) {
                // No data , do no scaling
                return 1f;
            }

            boolean notLabel = !isShowingAlarm(context, id) &&  !isShowingDate(context, id);
            boolean timeOnly = !notLabel && !showWorldClock;
            Resources res = context.getResources();
            float density = res.getDisplayMetrics().density;
            // Estimate height of date text box - 1.35 roughly approximates the text box padding
            float lblBox = notLabel ? 0 :  (1.35f * res.getDimension(R.dimen.label_font_size));
            // Ensure divisor for ratio is positive number
            if (res.getDimension(R.dimen.min_digital_widget_height) - lblBox > 0) {
                float ratio = ((density * minHeight) - lblBox)
                        / (res.getDimension(timeOnly ? R.dimen.min_custom_widget_height : R.dimen.min_digital_widget_height) - lblBox);
                return (ratio > 1) ? 1 : Math.max(ratio, 0.4f);
            }
        }
        return 1;
    }


    // Decide if to show the list of world clock.
    // Check to see if the widget size is big enough, if it is return true.
    public static boolean showList(Context context, int id, float scale) {
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        if (widgetManager == null) {
            // no manager to make the calculation, show the list anyway
            return true;
        }
        Bundle options = widgetManager.getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return true;
        }
        Resources res = context.getResources();
        String whichHeight = res.getConfiguration().orientation ==
                Configuration.ORIENTATION_PORTRAIT
                ? AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT;
        int height = options.getInt(whichHeight);
        if (height == 0) {
            // no data to make the calculation, show the list anyway
            return true;
        }
        float density = res.getDisplayMetrics().density;
        // Estimate height of date text box
        float lblBox = 1.35f * res.getDimension(R.dimen.label_font_size);
        float neededSize = res.getDimension(R.dimen.digital_widget_list_min_fixed_height) +
                2 * lblBox +
                scale * res.getDimension(R.dimen.digital_widget_list_min_scaled_height);
        return ((density * height) > neededSize);
    }

    /***
     * Set the format of the time on the clock accrding to the locale
     * @param clock - view to format
     * @param amPmFontSize - size of am/pm label, zero size means no am/om label
     * @param clockId - id of TextClock view as defined in the clock's layout.
     */
    public static void setTimeFormat(RemoteViews clock, int amPmFontSize, int clockId) {
        if (clock != null) {
            // Set the best format for 12 hours mode according to the locale
            clock.setCharSequence(clockId, "setFormat12Hour", Utils.get12ModeFormat(amPmFontSize));
            // Set the best format for 24 hours mode according to the locale
            clock.setCharSequence(clockId, "setFormat24Hour", Utils.get24ModeFormat());
        }
    }

    /*public static boolean isShowingWorldClockList(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(CustomAppWidgetConfigure.KEY_SHOW_WORLD_CLOCK + "_" + id, true);
    }*/

    public static boolean isShowingAlarm(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(CustomAppWidgetConfigure.KEY_SHOW_ALARM + "_" + id, true);
    }

    public static boolean isShowingDate(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(CustomAppWidgetConfigure.KEY_SHOW_DATE + "_" + id, true);
    }

    public static boolean isClockShadow(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(CustomAppWidgetConfigure.KEY_CLOCK_SHADOW + "_" + id, true);
    }

    public static Bitmap createTextBitmap(final String text, final Typeface typeface, final float textSizePixels, final int textColor, boolean shadow, float letterSpacing) {
        final TextPaint textPaint = new TextPaint();
        textPaint.setTypeface(typeface);
        textPaint.setTextSize(textSizePixels);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (shadow) {
            textPaint.setShadowLayer(5.0f, 0.0f, 0.0f, Color.BLACK);
        }
        if (letterSpacing != -1) {
            textPaint.setLetterSpacing(letterSpacing);
        }
        int textHeight = (int)(textPaint.descent() - textPaint.ascent());
        int textOffset = (int)((textHeight / 2) - textPaint.descent());
        Bitmap myBitmap = Bitmap.createBitmap((int) textPaint.measureText(text), (int) textSizePixels, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        myCanvas.drawText(text, myBitmap.getWidth() / 2, myBitmap.getHeight() / 2 + textOffset, textPaint);
        return myBitmap;
    }

    public static Typeface getClockFont(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String font = prefs.getString(CustomAppWidgetConfigure.KEY_CLOCK_FONT + "_" + id, null);
        if (font != null) {
            return Typeface.createFromFile(font);
        }
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    public static int getClockColor(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(CustomAppWidgetConfigure.KEY_CLOCK_COLOR + "_" + id, Color.WHITE);
    }
}

