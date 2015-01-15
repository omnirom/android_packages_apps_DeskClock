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
 * limitations under the License
 */

package com.android.deskclock.timer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.deskclock.R;


public class TimerView extends LinearLayout {

    private TextView mHoursOnes, mMinutesOnes;
    private TextView mHoursTens, mMinutesTens;
    private TextView mSeconds;
    private final Typeface mAndroidClockMonoThin;
    private Typeface mOriginalHoursTypeface;
    private Typeface mOriginalMinutesTypeface;
    private float mGapPadding;

    @SuppressWarnings("unused")
    public TimerView(Context context) {
        this(context, null);
    }

    public TimerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mAndroidClockMonoThin =
                Typeface.createFromAsset(context.getAssets(), "fonts/AndroidClockMono-Thin.ttf");

        Resources resources = context.getResources();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mGapPadding = 0.55f;

        mHoursTens = (TextView) findViewById(R.id.hours_tens);
        mHoursOnes = (TextView) findViewById(R.id.hours_ones);
        if (mHoursOnes != null) {
            mOriginalHoursTypeface = mHoursOnes.getTypeface();
        }
        mMinutesTens = (TextView) findViewById(R.id.minutes_tens);
        if (mHoursOnes != null && mMinutesTens != null) {
            addStartPadding(mMinutesTens);
        }
        mMinutesOnes = (TextView) findViewById(R.id.minutes_ones);
        if (mMinutesOnes != null) {
            mOriginalMinutesTypeface = mMinutesOnes.getTypeface();
        }
        mSeconds = (TextView) findViewById(R.id.seconds);
        if (mSeconds != null) {
            addStartPadding(mSeconds);
        }
    }

    /**
     * Measure the text and add a start padding to the view
     * @param textView view to measure and onb to which add start padding
     */
    private void addStartPadding(TextView textView) {
        // allDigits will contain ten digits: "0123456789" in the default locale
        String allDigits = String.format("%010d", 123456789);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textView.getTextSize());
        paint.setTypeface(textView.getTypeface());

        float widths[] = new float[allDigits.length()];
        int ll = paint.getTextWidths(allDigits, widths);
        int largest = 0;
        for (int ii = 1; ii < ll; ii++) {
            if (widths[ii] > widths[largest]) {
                largest = ii;
            }
        }
        // Add left padding to the view - Note: layout inherits LTR
        textView.setPadding((int) (mGapPadding * widths[largest]), 0, 0, 0);
    }


    public void setTime(int hoursTensDigit, int hoursOnesDigit, int minutesTensDigit,
                        int minutesOnesDigit, int seconds) {
        // if mHoursTens is non-empty, decrease total padding by width of one digit
        if (hoursTensDigit != 0) {
            mGapPadding = 0.05f;
        } else {
            mGapPadding = 0.55f;
        }

        if (mHoursTens != null) {
            if (hoursTensDigit == -1) {
                mHoursTens.setText("-");
                mHoursTens.setTypeface(mAndroidClockMonoThin);
            } else {
                mHoursTens.setText(hoursTensDigit == 0 ? "" : String.format("%d", hoursTensDigit));
                mHoursTens.setTypeface(mOriginalHoursTypeface);
            }
        }

        if (mHoursOnes != null) {
            if (hoursOnesDigit == -1) {
                mHoursOnes.setText("-");
                mHoursOnes.setTypeface(mAndroidClockMonoThin);
            } else {
                mHoursOnes.setText(String.format("%d", hoursOnesDigit));
                mHoursOnes.setTypeface(mOriginalHoursTypeface);
            }
        }

        if (mMinutesTens != null) {
            if (minutesTensDigit == -1) {
                mMinutesTens.setText("-");
                mMinutesTens.setTypeface(mAndroidClockMonoThin);
            } else {
                mMinutesTens.setText(String.format("%d", minutesTensDigit));
                mMinutesTens.setTypeface(mOriginalMinutesTypeface);
            }
            addStartPadding(mMinutesTens);
        }
        if (mMinutesOnes != null) {
            if (minutesOnesDigit == -1) {
                mMinutesOnes.setText("-");
                mMinutesOnes.setTypeface(mAndroidClockMonoThin);
            } else {
                mMinutesOnes.setText(String.format("%d", minutesOnesDigit));
                mMinutesOnes.setTypeface(mOriginalMinutesTypeface);
            }
        }

        if (mSeconds != null) {
            mSeconds.setText(String.format("%02d", seconds));
            addStartPadding(mSeconds);
        }
    }
}
