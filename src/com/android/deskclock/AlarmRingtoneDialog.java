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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.deskclock.provider.Alarm;

public class AlarmRingtoneDialog extends DialogFragment implements
        DialogInterface.OnClickListener,
        SeekBar.OnSeekBarChangeListener,
        DirectoryChooserDialog.ChosenDirectoryListener {

    private static final int REQUEST_CODE_RINGTONE = 1;
    private static final int REQUEST_CODE_MEDIA = 2;

    private static final int ALARM_TYPE_ALARM = 0;
    private static final int ALARM_TYPE_RINGTONE = 1;
    private static final int ALARM_TYPE_MUSIC = 2;
    private static final int ALARM_TYPE_FOLDER = 3;

    private static final String KEY_MEDIA_TYPE = "mediaType";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_INCREASING_VOLUME = "increasingVolume";
    private static final String KEY_RANDOM_MODE = "randomMode";
    private static final String KEY_RINGTONE = "ringtone";
    private static final String KEY_PREALARM = "preAlarm";
    private static final String KEY_ALARM = "alarm";
    private static final String KEY_TAG = "tag";

    private String mTag;
    private Alarm mAlarm;
    private boolean mPreAlarm;

    private TextView ringtone;
    private Spinner mMediaTypeSelect;
    private View mRingtoneSection;
    private int mCurrentMediaType;
    private List<Uri> mAlarms;
    private List<Uri> mRingtones;
    private Uri mRingtone;
    private int mVolume = -1;
    private boolean mIncreasingVolumeValue;
    private boolean mRandomModeValue;
    private CheckBox mEnabledCheckbox;
    private SeekBar mMaxVolumeSeekBar;
    private CheckBox mIncreasingVolume;
    private TextView mMinVolumeText;
    private TextView mMaxVolumeText;
    private CheckBox mRandomMode;

    public interface AlarmRingtoneDialogListener {
        void onFinishOk(Alarm alarm);
    }

    public static AlarmRingtoneDialog newInstance(Alarm alarm, boolean preAlarm, String tag) {
        AlarmRingtoneDialog fragment = new AlarmRingtoneDialog();
        Bundle args = new Bundle();
        args.putParcelable(KEY_ALARM, alarm);
        args.putString(KEY_TAG, tag);
        args.putBoolean(KEY_PREALARM, preAlarm);
        fragment.setArguments(args);
        return fragment;
    }

    public AlarmRingtoneDialog() {
        super();
    }

//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        boolean isLandscape = getResources().getConfiguration().orientation
//                == Configuration.ORIENTATION_LANDSCAPE;
//        if (isLandscape) {
//            setStyle(DialogFragment.STYLE_NO_TITLE, 0);
//        }
//    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        mAlarm = bundle.getParcelable(KEY_ALARM);
        mTag = bundle.getString(KEY_TAG);
        mPreAlarm = bundle.getBoolean(KEY_PREALARM);

        if (savedInstanceState != null) {
            mCurrentMediaType = savedInstanceState.getInt(KEY_MEDIA_TYPE);
            mVolume = savedInstanceState.getInt(KEY_VOLUME);
            mIncreasingVolumeValue = savedInstanceState.getBoolean(KEY_INCREASING_VOLUME);
            mRandomModeValue = savedInstanceState.getBoolean(KEY_RANDOM_MODE);
            mRingtone = savedInstanceState.getParcelable(KEY_RINGTONE);
            mAlarm = savedInstanceState.getParcelable(KEY_ALARM);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
        //.setTitle(mPreAlarm ? R.string.prealarm_tone_dialog_title : R.string.alarm_tone_dialog_title)
        .setPositiveButton(android.R.string.ok, this)
        .setNeutralButton(R.string.alarm_test_button, null)
        .setNegativeButton(android.R.string.cancel, null)
        .setView(createDialogView());

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_MEDIA_TYPE, mCurrentMediaType);
        outState.putInt(KEY_VOLUME, mVolume);
        outState.putBoolean(KEY_INCREASING_VOLUME, mIncreasingVolumeValue);
        outState.putBoolean(KEY_RANDOM_MODE, mRandomModeValue);
        outState.putParcelable(KEY_RINGTONE, mRingtone);
        outState.putParcelable(KEY_ALARM, mAlarm);
    }

    @Override
    public void onStart() {
        super.onStart();

        AlertDialog d = (AlertDialog)getDialog();
        if(d != null) {
            Button testButton = (Button) d.getButton(Dialog.BUTTON_NEUTRAL);
            testButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // save into temp for testing
                        Alarm testAlarm = new Alarm();
                        saveChanges(testAlarm);
                        showAlarmTestDialog(testAlarm, mPreAlarm);
                    }
                });
         }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            saveChanges(mAlarm);
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
                .inflate(R.layout.dialog_alarm_ringtone, null);
        final AudioManager audioManager = (AudioManager) getActivity().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        final int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        if (mPreAlarm) {
            mRingtone = mAlarm.preAlarmAlert;
            mVolume = mAlarm.preAlarmVolume;
        } else {
            mRingtone = mAlarm.alert;
            mVolume = mAlarm.alarmVolume;
        }
        mIncreasingVolumeValue = mAlarm.getIncreasingVolume(mPreAlarm);
        mRandomModeValue = mAlarm.getRandomMode(mPreAlarm);

        mMinVolumeText = (TextView) view.findViewById(R.id.alarm_volume_min);
        // must not be 0 if enabled
        mMinVolumeText.setText(String.valueOf(1));
        mMaxVolumeText = (TextView) view.findViewById(R.id.alarm_volume_max);
        mMaxVolumeText.setText(String.valueOf(maxVol));

        mRandomMode = (CheckBox) view.findViewById(R.id.random_mode_enable);
        mRandomMode.setChecked(mRandomModeValue);
        mRandomMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRandomModeValue = mRandomMode.isChecked();
            }
        });

        mIncreasingVolume = (CheckBox) view.findViewById(R.id.increasing_volume_onoff);
        mIncreasingVolume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIncreasingVolumeValue = mIncreasingVolume.isChecked();
            }
        });
        mEnabledCheckbox = (CheckBox) view.findViewById(R.id.alarm_volume_enable);
        mMaxVolumeSeekBar = (SeekBar) view.findViewById(R.id.alarm_volume);
        mMaxVolumeSeekBar.setMax(maxVol - 1);
        mEnabledCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMaxVolumeSeekBar.setEnabled(!mEnabledCheckbox.isChecked());
            }
        });

        mMediaTypeSelect = (Spinner) view.findViewById(R.id.alarm_type_select);
        if (mPreAlarm) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    getActivity().getApplicationContext(), R.array.prealarm_type_entries,
                    R.layout.alarm_type_item);
            mMediaTypeSelect.setAdapter(adapter);
        } else {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    getActivity().getApplicationContext(), R.array.alarm_type_entries,
                    R.layout.alarm_type_item);
            mMediaTypeSelect.setAdapter(adapter);
        }
        mMediaTypeSelect.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // On selecting a spinner item
                mCurrentMediaType = position;
                mRandomMode.setVisibility(mCurrentMediaType == ALARM_TYPE_FOLDER ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }});

        mRingtoneSection = (View) view.findViewById(R.id.ringtone_section);

        ringtone = (TextView) view.findViewById(R.id.choose_ringtone);
        ringtone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectRingtone(mCurrentMediaType);
            }
        });

        cacheAlarmTones();
        cacheRingtones();
        updateValues();
        return view;
    }

    private void launchRingTonePicker(int mediaType) {
        Log.d("launchRingTonePicker");
        Uri oldRingtone = Alarm.NO_RINGTONE_URI.equals(mRingtone) ? null : mRingtone;
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                oldRingtone);
        if (mediaType == ALARM_TYPE_ALARM) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM);
        } else if ( mediaType == ALARM_TYPE_RINGTONE) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_RINGTONE);
        }
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
        startActivityForResult(intent, REQUEST_CODE_RINGTONE);
    }

    private void launchAlarmMediaPicker() {
        Intent intent = new Intent();
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(
                Intent.createChooser(intent,
                        getResources().getString(R.string.pick_media)),
                REQUEST_CODE_MEDIA);
    }

    private void launchFolderPicker() {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        final Fragment prev = getFragmentManager().findFragmentByTag("choose_folder");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        final DirectoryChooserDialog fragment = DirectoryChooserDialog.newInstance(getTag());
        fragment.show(getFragmentManager(), "choose_folder");
    }

    private void saveRingtoneUri(Intent intent) {
        Uri uri = intent
                .getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) {
            uri = Alarm.NO_RINGTONE_URI;
        }
        mRingtone = uri;
    }

    private void saveMediaUri(Intent intent) {
        Uri uri = intent.getData();
        mRingtone = uri;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
            case REQUEST_CODE_RINGTONE:
                saveRingtoneUri(data);
                updateRingtoneName();
                break;
            case REQUEST_CODE_MEDIA:
                saveMediaUri(data);
                updateRingtoneName();
                break;
            default:
                Log.w("Unhandled request code in onActivityResult: "
                        + requestCode);
            }
        } else {
        }
    }

    private String getRingToneTitle(Uri uri) {
        Ringtone ringTone = RingtoneManager.getRingtone(getActivity()
                .getApplicationContext(), uri);
        return ringTone.getTitle(getActivity().getApplicationContext());
    }

    private String getMediaTitle(Uri uri) {
        if (uri == null) {
            uri = getDefaultAlarmUri();
        }
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

    private void cacheRingtones() {
        mRingtones = new ArrayList<Uri>();

        Cursor alarmsCursor = null;
        try {
            RingtoneManager ringtoneMgr = new RingtoneManager(getActivity()
                    .getApplicationContext());
            ringtoneMgr.setType(RingtoneManager.TYPE_RINGTONE);

            alarmsCursor = ringtoneMgr.getCursor();
            int alarmsCount = alarmsCursor.getCount();
            if (alarmsCount == 0 && !alarmsCursor.moveToFirst()) {
                return;
            }

            while (!alarmsCursor.isAfterLast() && alarmsCursor.moveToNext()) {
                int currentPosition = alarmsCursor.getPosition();
                mRingtones.add(ringtoneMgr.getRingtoneUri(currentPosition));
            }

        } finally {
            if (alarmsCursor != null) {
                alarmsCursor.close();
            }
        }
    }

    private void setRingtoneName() {
        Uri ringtoneUri = mRingtone;
        boolean mediaAlertEnabled = false;
        mCurrentMediaType = ALARM_TYPE_ALARM;

        if (ringtoneUri != null) {
            if (!Alarm.NO_RINGTONE_URI.equals(ringtoneUri)) {
                boolean found = false;
                if (mAlarms.contains(ringtoneUri)){
                    found = true;
                } else if (mRingtones.contains(ringtoneUri)){
                    found = true;
                    mCurrentMediaType = ALARM_TYPE_RINGTONE;
                }
                if (!found) {
                    mediaAlertEnabled = true;
                }
            }
        }

        if (mediaAlertEnabled) {
            File f = new File(ringtoneUri.getPath());
            if (f.exists() && f.isDirectory()) {
                mCurrentMediaType = ALARM_TYPE_FOLDER;
            } else {
                mCurrentMediaType = ALARM_TYPE_MUSIC;
            }
        }
        updateRingtoneName();
    }

    private void updateRingtoneName() {
        Uri ringtoneUri = mRingtone;
        String ringtoneTitle = "";

        if (Alarm.NO_RINGTONE_URI.equals(ringtoneUri)) {
            ringtoneTitle = getResources().getString(R.string.silent_alarm_summary);
        } else {
            if (mCurrentMediaType == ALARM_TYPE_MUSIC) {
                ringtoneTitle = getMediaTitle(ringtoneUri);
            } else if (mCurrentMediaType == ALARM_TYPE_ALARM || mCurrentMediaType == ALARM_TYPE_RINGTONE) {
                ringtoneTitle = getRingToneTitle(ringtoneUri);
            } else if (mCurrentMediaType == ALARM_TYPE_FOLDER) {
                ringtoneTitle = ringtoneUri.getPath();
            }
        }

        ringtone.setText(ringtoneTitle);
        ringtone.setContentDescription(getResources().getString(
                R.string.ringtone_description)
                + " " + ringtone);
    }

    private void updateValues() {
        setRingtoneName();

        if (mVolume == -1) {
            mEnabledCheckbox.setChecked(true);
            mMaxVolumeSeekBar.setEnabled(false);
        } else {
            mEnabledCheckbox.setChecked(false);
            mMaxVolumeSeekBar.setProgress(mVolume - 1);
        }

        mRingtoneSection.setVisibility(View.VISIBLE);
        mMediaTypeSelect.setSelection(mCurrentMediaType);
        mIncreasingVolume.setChecked(mIncreasingVolumeValue);
        mRandomMode.setVisibility(mCurrentMediaType == ALARM_TYPE_FOLDER ? View.VISIBLE : View.GONE);
    }

    private Uri getDefaultAlarmUri() {
        Uri alert = RingtoneManager.getActualDefaultRingtoneUri(getActivity(),
                    RingtoneManager.TYPE_ALARM);
        if (alert == null) {
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        return alert;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (progress == 0){
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

    private void showAlarmTestDialog(Alarm alarm, boolean preAlarm) {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        final Fragment prev = getFragmentManager().findFragmentByTag("alarm_test");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        final AlarmTestDialog fragment = AlarmTestDialog.newInstance(alarm, preAlarm);
        fragment.show(getFragmentManager(), "alarm_test");
    }

    private void saveChanges(Alarm alarm) {
        if (mPreAlarm){
            alarm.preAlarmAlert = mRingtone;
            alarm.preAlarmVolume = mVolume;
            if (mEnabledCheckbox.isChecked()) {
                alarm.preAlarmVolume = -1;
            } else {
                alarm.preAlarmVolume = mMaxVolumeSeekBar.getProgress() + 1;
            }
        } else {
            alarm.alert = mRingtone;
            alarm.mediaStart = false;
            alarm.alarmVolume = mVolume;
            if (mEnabledCheckbox.isChecked()) {
                alarm.alarmVolume = -1;
            } else {
                alarm.alarmVolume = mMaxVolumeSeekBar.getProgress() + 1;
            }
        }
        alarm.setIncreasingVolume(mPreAlarm, mIncreasingVolumeValue);
        alarm.setRandomMode(mPreAlarm, mRandomModeValue);
    }

    private void selectRingtone(int mediaType) {
        if (mediaType == ALARM_TYPE_ALARM || mediaType == ALARM_TYPE_RINGTONE) {
            launchRingTonePicker(mediaType);
        } else if (mediaType == ALARM_TYPE_MUSIC) {
            launchAlarmMediaPicker();
        } else if (mediaType == ALARM_TYPE_FOLDER) {
            launchFolderPicker();
        }
    }

    @Override
    public void onChooseDirOk(Uri chosenDir) {
        mRingtone = chosenDir;
        updateRingtoneName();
    }

    @Override
    public void onChooseDirCancel() {
    }
}
