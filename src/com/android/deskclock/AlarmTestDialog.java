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

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.alarms.TestAlarmKlaxon;

public class AlarmTestDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    public AlarmClockFragment mParent;
    public Alarm mAlarm;
    public boolean mPreAlarm;
    private TextView mTitle;

    public static AlarmTestDialog newInstance(AlarmClockFragment parent,
            Alarm alarm, boolean preAlarm) {
        AlarmTestDialog fragment = new AlarmTestDialog();
        fragment.mParent = parent;
        fragment.mAlarm = alarm;
        fragment.mPreAlarm = preAlarm;
        return fragment;
    }

    public AlarmTestDialog() {
        super();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alarm_test_dialog_title)
                .setNegativeButton(android.R.string.cancel, null)
                .setView(createDialogView());

        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        TestAlarmKlaxon.stopTest(mAlarm);
    }

    @Override
    public void onDismiss (DialogInterface dialog) {
        TestAlarmKlaxon.stopTest(mAlarm);
        super.onDismiss(dialog);
    }

    @Override
    public void onStart() {
        super.onStart();
        TestAlarmKlaxon.test(getActivity().getApplicationContext(), mAlarm, mPreAlarm,
                new TestAlarmKlaxon.ErrorHandler() {
            @Override
            public void onError(String msg) {
                mTitle.setText(R.string.play_error_title);
            }
        });
    }

    private View createDialogView() {
        final Activity activity = getActivity();
        final LayoutInflater inflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.dialog_alarm_test, null);
        setRetainInstance(true);
        mTitle = (TextView) view.findViewById(R.id.title);
        return view;
    }
}
