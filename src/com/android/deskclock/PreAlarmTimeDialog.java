/*
 *  Copyright (C) 2014 The OmniROM Project
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

package com.android.deskclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.android.deskclock.provider.Alarm;

public class PreAlarmTimeDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    private static final String KEY_ALARM = "alarm";
    private static final String KEY_TAG = "tag";

    private Alarm mAlarm;
    private NumberPicker mNumberPickerView;
    private TextView mNumberPickerMinutesView;
    private String mTag;

    public static PreAlarmTimeDialog newInstance(Alarm alarm, String tag) {
        PreAlarmTimeDialog fragment = new PreAlarmTimeDialog();
        Bundle args = new Bundle();
        args.putParcelable(KEY_ALARM, alarm);
        args.putString(KEY_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    public PreAlarmTimeDialog() {
        super();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        mAlarm = bundle.getParcelable(KEY_ALARM);
        mTag = bundle.getString(KEY_TAG);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.prealarm_time_select)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, null)
                .setView(createDialogView());

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mNumberPickerView.clearFocus();
            mAlarm.preAlarmTime = mNumberPickerView.getValue();

            Fragment frag = getFragmentManager().findFragmentByTag(mTag);
            if (frag instanceof AlarmClockFragment) {
                ((AlarmClockFragment) frag).onFinishOk(mAlarm);
            }
        }
    }

    private View createDialogView() {
        final Activity activity = getActivity();
        final LayoutInflater inflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.dialog_prealarm_time, null);

        mNumberPickerMinutesView = (TextView) view.findViewById(R.id.title);
        mNumberPickerView = (NumberPicker) view.findViewById(R.id.minutes_picker);
        mNumberPickerView.setMinValue(1);
        mNumberPickerView.setMaxValue(30);
        mNumberPickerView.setValue(mAlarm.preAlarmTime);
        updateMinutes();
        mNumberPickerView.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                updateMinutes();
            }
        });

        return view;
    }

    private void updateMinutes() {
        mNumberPickerMinutesView.setText(String.format(getActivity().getApplicationContext().getResources()
                .getQuantityText(R.plurals.snooze_picker_label, mNumberPickerView.getValue())
                .toString()));
    }
}
