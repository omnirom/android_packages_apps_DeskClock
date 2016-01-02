/*
 *  Copyright (C) 2015-2016 The OmniROM Project
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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;

import com.android.deskclock.alarms.TestAlarmKlaxon;
import com.android.deskclock.provider.Alarm;

public class AlarmTestDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    private static final String KEY_PREALARM = "preAlarm";
    private static final String KEY_ALARM = "alarm";

    public Alarm mAlarm;
    public boolean mPreAlarm;
    private TextView mTitle;
    private ProgressBar mProgressCircle;
    private TextView mProgressText;
    private LinearLayout mProgress;
    private Handler mHandler = new Handler();

    public static AlarmTestDialog newInstance(Alarm alarm, boolean preAlarm) {
        AlarmTestDialog fragment = new AlarmTestDialog();
        Bundle args = new Bundle();
        args.putParcelable(KEY_ALARM, alarm);
        args.putBoolean(KEY_PREALARM, preAlarm);
        fragment.setArguments(args);
        return fragment;
    }

    public AlarmTestDialog() {
        super();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        mAlarm = bundle.getParcelable(KEY_ALARM);
        mPreAlarm = bundle.getBoolean(KEY_PREALARM);

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
    public void onDismiss(DialogInterface dialog) {
        TestAlarmKlaxon.stopTest(mAlarm);
    }

    @Override
    public void onStart() {
        super.onStart();
        new AsyncTask<Void,Void,Void>(){
            @Override
            protected Void doInBackground(final Void... params){
                TestAlarmKlaxon.test(getActivity().getApplicationContext(), mAlarm, mPreAlarm,
                    new TestAlarmKlaxon.ErrorHandler() {
                        @Override
                        public void onError(final String msg) {
                            mHandler.post(new Runnable() {
                                public void run() {
                                    mTitle.setText(msg);
                                }
                            });
                        }
                        @Override
                        public void onInfo(final String msg) {
                            mHandler.post(new Runnable() {
                                public void run() {
                                    mTitle.setText(msg);
                                }
                            });
                        }
                        @Override
                        public void startProgress() {
                            mHandler.post(new Runnable() {
                                public void run() {
                                    mTitle.setVisibility(View.GONE);
                                    mProgress.setVisibility(View.VISIBLE);
                                    mProgressText.setText(getActivity().getResources().getString(R.string.folders_scanning));
                                    mProgressCircle.setProgress(1);
                                }
                            });
                        }
                        @Override
                        public void stopProgress() {
                            mHandler.post(new Runnable() {
                                public void run() {
                                    mProgress.setVisibility(View.GONE);
                                    mTitle.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    });
                return null;
            }

            @Override
            protected void onPostExecute(final Void result){
            }
        }.execute();
    }

    private View createDialogView() {
        final Activity activity = getActivity();
        final LayoutInflater inflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.dialog_alarm_test, null);
        mTitle = (TextView) view.findViewById(R.id.title);
        mProgress = (LinearLayout) view.findViewById(R.id.progress);
        mProgressCircle = (ProgressBar) view.findViewById(R.id.progress_circle);
        mProgressCircle.setIndeterminate(true);
        mProgressText = (TextView) view.findViewById(R.id.progress_text);
        mProgress.setVisibility(View.GONE);

        return view;
    }
}
