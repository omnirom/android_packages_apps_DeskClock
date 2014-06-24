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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.widget.CheckBox;
import android.widget.TextView;
import android.database.Cursor;
import android.provider.OpenableColumns;

import com.android.deskclock.provider.Alarm;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public class AlarmRingtoneDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    private static final int REQUEST_CODE_RINGTONE = 1;
    private static final int REQUEST_CODE_MEDIA = 2;

    public AlarmClockFragment mParent;
    public Alarm mAlarm;
    private TextView ringtone;
    private TextView mediaAlert;
    private CheckBox mediaStart;
    private List<Uri> mAlarms;

    public interface AlarmRingtoneDialogListener {
        void onFinishOk(Alarm alarm);
    }

    public static AlarmRingtoneDialog newInstance(AlarmClockFragment parent,
            Alarm alarm) {
        AlarmRingtoneDialog fragment = new AlarmRingtoneDialog();
        fragment.mParent = parent;
        fragment.mAlarm = alarm;
        return fragment;
    }

    public AlarmRingtoneDialog() {
        super();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alarm_tone_select)
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
            mParent.onFinishOk(mAlarm);
        }
    }

    private View createDialogView() {
        final Activity activity = getActivity();
        final LayoutInflater inflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.dialog_alarm_ringtone, null);

        ringtone = (TextView) view.findViewById(R.id.choose_ringtone);
        mediaAlert = (TextView) view.findViewById(R.id.choose_mediaAlert);
        mediaStart = (CheckBox) view.findViewById(R.id.media_start);

        ringtone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchRingTonePicker(mAlarm);
            }
        });

        mediaAlert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchAlarmMediaPicker(mAlarm);
            }
        });

        mediaStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean checked = ((CheckBox) v).isChecked();
                mAlarm.mediaStart = checked;
            }
        });

        cacheAlarmTones();
        updateValues();
        return view;
    }

    private void launchRingTonePicker(Alarm alarm) {
        Uri oldRingtone = Alarm.NO_RINGTONE_URI.equals(alarm.alert) ? null
                : alarm.alert;
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                oldRingtone);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
        startActivityForResult(intent, REQUEST_CODE_RINGTONE);
    }

    private void launchAlarmMediaPicker(Alarm alarm) {
        Intent intent = new Intent();
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(
                Intent.createChooser(intent,
                        getResources().getString(R.string.pick_media)),
                REQUEST_CODE_MEDIA);
    }

    private void saveRingtoneUri(Intent intent) {
        Uri uri = intent
                .getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) {
            uri = Alarm.NO_RINGTONE_URI;
        }
        mAlarm.alert = uri;
        mAlarm.mediaStart = false;
    }

    private void saveMediaUri(Intent intent) {
        Uri uri = intent.getData();
        mAlarm.alert = uri;
        mAlarm.mediaStart = false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
            case REQUEST_CODE_RINGTONE:
                saveRingtoneUri(data);
                updateValues();
                break;
            case REQUEST_CODE_MEDIA:
                saveMediaUri(data);
                updateValues();
                break;
            default:
                Log.w("Unhandled request code in onActivityResult: "
                        + requestCode);
            }
        }
    }

    private String getRingToneTitle(Uri uri) {
        Ringtone ringTone = RingtoneManager.getRingtone(getActivity()
                .getApplicationContext(), uri);
        return ringTone.getTitle(getActivity().getApplicationContext());
    }

    private String getMediaTitle(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getActivity().getApplicationContext().getContentResolver()
                    .query(uri, null, null, null, null);
            int nameIndex = cursor
                    .getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            return cursor.getString(nameIndex);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void cacheAlarmTones() {
        mAlarms = new ArrayList<Uri>();

        Cursor alarmsCursor = null;
        try {
            RingtoneManager ringtoneMgr = new RingtoneManager(getActivity()
                    .getApplicationContext());
            ringtoneMgr.setType(RingtoneManager.TYPE_ALARM);

            alarmsCursor = ringtoneMgr.getCursor();
            int alarmsCount = alarmsCursor.getCount();
            if (alarmsCount == 0 && !alarmsCursor.moveToFirst()) {
                return;
            }

            while (!alarmsCursor.isAfterLast() && alarmsCursor.moveToNext()) {
                int currentPosition = alarmsCursor.getPosition();
                mAlarms.add(ringtoneMgr.getRingtoneUri(currentPosition));
            }
            mAlarms.add(RingtoneManager
                    .getDefaultUri(RingtoneManager.TYPE_ALARM));
        } finally {
            if (alarmsCursor != null) {
                alarmsCursor.close();
            }
        }
    }

    private void updateValues() {
        boolean mediaAlertEnabled = false;
        if (mAlarm.alert != null) {
            if (!Alarm.NO_RINGTONE_URI.equals(mAlarm.alert)) {
                if (!mAlarms.contains(mAlarm.alert)) {
                    mediaAlertEnabled = true;
                }
            }
        }

        if (mediaAlertEnabled) {
            ringtone.setText(getResources().getString(
                    R.string.ringtone_disabled));
        } else {
            final String ringtoneTitle;
            if (Alarm.NO_RINGTONE_URI.equals(mAlarm.alert)) {
                ringtoneTitle = getResources().getString(
                        R.string.silent_alarm_summary);
            } else {
                ringtoneTitle = getRingToneTitle(mAlarm.alert);
            }

            ringtone.setText(ringtoneTitle);
            ringtone.setContentDescription(getResources().getString(
                    R.string.ringtone_description)
                    + " " + ringtone);
        }

        if (mediaAlertEnabled) {
            mediaAlert.setText(getMediaTitle(mAlarm.alert));
        } else {
            mediaAlert.setText(getResources().getString(
                    R.string.ringtone_disabled));
        }
    }
}
