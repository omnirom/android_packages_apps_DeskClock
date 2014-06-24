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
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;

import com.android.deskclock.provider.Alarm;

public class AlarmVolumeDialog extends DialogFragment implements
        DialogInterface.OnClickListener,
        SeekBar.OnSeekBarChangeListener {

    public static AlarmClockFragment mParent;
    public static Alarm mAlarm;
    public static boolean mPreAlarm;
    private CheckBox mEnabledCheckbox;
    private SeekBar mMaxVolumeSeekBar;

    public static AlarmVolumeDialog newInstance(AlarmClockFragment parent,
            Alarm alarm, boolean preAlarm) {
        AlarmVolumeDialog fragment = new AlarmVolumeDialog();
        fragment.mParent = parent;
        fragment.mAlarm = alarm;
        fragment.mPreAlarm = preAlarm;
        return fragment;
    }

    public AlarmVolumeDialog() {
        super();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alarm_volume_select)
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
            if (mPreAlarm) {
                if (mEnabledCheckbox.isChecked()) {
                    mAlarm.preAlarmVolume = -1; 
                } else {                   
                    mAlarm.preAlarmVolume = mMaxVolumeSeekBar.getProgress();
                }
            } else {
                if (mEnabledCheckbox.isChecked()) {
                    mAlarm.alarmVolume = -1; 
                } else {
                    mAlarm.alarmVolume = mMaxVolumeSeekBar.getProgress();
                }
            }
            mParent.onFinishOk(mAlarm);
        }
    }

    private View createDialogView() {
        final Activity activity = getActivity();
        final LayoutInflater inflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.dialog_alarm_volume, null);

        AudioManager audioManager = (AudioManager) getActivity().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);

        mEnabledCheckbox = (CheckBox) view.findViewById(R.id.alarm_volume_enable);
        mMaxVolumeSeekBar = (SeekBar) view.findViewById(R.id.alarm_volume);
        mMaxVolumeSeekBar.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM));
        mEnabledCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMaxVolumeSeekBar.setEnabled(!mEnabledCheckbox.isChecked());
            }
        });

        if (mPreAlarm) {
            if (mAlarm.preAlarmVolume == -1) {
                mEnabledCheckbox.setChecked(true);
                mMaxVolumeSeekBar.setEnabled(false);
            } else {
                mEnabledCheckbox.setChecked(false);
                mMaxVolumeSeekBar.setProgress(mAlarm.preAlarmVolume);
            }
        } else {
            if (mAlarm.alarmVolume == -1) {
                mEnabledCheckbox.setChecked(true);
                mMaxVolumeSeekBar.setEnabled(false);
            } else {
                mEnabledCheckbox.setChecked(false);
                mMaxVolumeSeekBar.setProgress(mAlarm.alarmVolume);
            }
        }
        return view;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && progress == 0){
            // dont allow value 0
            seekBar.setProgress(1);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
