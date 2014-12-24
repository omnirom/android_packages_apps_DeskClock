/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.deskclock.alarms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.SettingsActivity;
import com.android.deskclock.provider.AlarmInstance;

/**
 * Manages playing ringtone and vibrating the device.
 */
public class AlarmKlaxon {
    private static final long[] sVibratePattern = new long[] { 500, 500 };

    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build();

    private static final int INCREASING_VOLUME_START = 1;
    private static final int INCREASING_VOLUME_DELTA = 1;

    private static boolean sStarted = false;
    private static AudioManager sAudioManager = null;
    private static MediaPlayer sMediaPlayer = null;
    private static boolean sPreAlarmMode = false;
    private static boolean sMultiFileMode = false;
    private static List<Uri> mSongs = new ArrayList<Uri>();
    private static Uri mCurrentTone;
    private static int sCurrentIndex;
    private static int sCurrentVolume = INCREASING_VOLUME_START;
    private static int sSavedVolume;
    private static int sMaxVolume;
    private static boolean sIncreasingVolume;
    private static boolean sRandomPlayback;
    private static long sVolumeIncreaseSpeed;
    private static boolean sIncreasingVolumeDone;
    private static boolean sFirstFile;

    // Internal messages
    private static final int INCREASING_VOLUME = 1001;

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case INCREASING_VOLUME:
                if (sStarted) {
                    sCurrentVolume += INCREASING_VOLUME_DELTA;
                    if (sCurrentVolume <= sMaxVolume) {
                        LogUtils.v("Increasing alarm volume to " + sCurrentVolume);
                        sAudioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC, sCurrentVolume, 0);
                        sHandler.sendEmptyMessageDelayed(INCREASING_VOLUME,
                                sVolumeIncreaseSpeed);
                    }
                }
                break;
            }
        }
    };

    public static void stop(Context context) {
        if (sStarted) {
            LogUtils.v("AlarmKlaxon.stop()");

            sStarted = false;
            sHandler.removeMessages(INCREASING_VOLUME);
            // reset to default from before
            sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        sSavedVolume, 0);

            // Stop audio playing
            if (sMediaPlayer != null) {
                sMediaPlayer.stop();
                sMediaPlayer.release();
                sMediaPlayer = null;
                sAudioManager.abandonAudioFocus(null);
                sAudioManager = null;
            }
            sPreAlarmMode = false;

            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE))
                    .cancel();
        }
    }

    private static Uri getDefaultAlarm(Context context) {
        Uri alarmNoise = RingtoneManager.getActualDefaultRingtoneUri(context,
                    RingtoneManager.TYPE_ALARM);
        if (alarmNoise == null) {
            alarmNoise = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        return alarmNoise;
    }

    public static void start(final Context context, AlarmInstance instance,
            boolean inTelephoneCall) {
        // Make sure we are stop before starting
        stop(context);

        LogUtils.v("AlarmKlaxon.start() " + instance);

        sPreAlarmMode = false;
        if (instance.mAlarmState == AlarmInstance.PRE_ALARM_STATE) {
            sPreAlarmMode = true;
        }

        sVolumeIncreaseSpeed = getVolumeChangeDelay(context);
        LogUtils.v("Volume increase interval " + sVolumeIncreaseSpeed);

        final Context appContext = context.getApplicationContext();
        sAudioManager = (AudioManager) appContext
                .getSystemService(Context.AUDIO_SERVICE);
        // save current value
        sSavedVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        sMaxVolume = sSavedVolume;
        sIncreasingVolume = instance.getIncreasingVolume(sPreAlarmMode);
        sRandomPlayback = instance.getRandomMode(sPreAlarmMode);
        sFirstFile = true;

        if (sPreAlarmMode) {
            sMaxVolume = instance.mPreAlarmVolume;
        } else {
            sMaxVolume = instance.mAlarmVolume;
        }
        if (sMaxVolume == -1){
            // calc from current alarm volume
            sMaxVolume = calcMusicVolumeFromCurrentAlarm();
        }

        Uri alarmNoise = null;
        sMultiFileMode = false;
        sCurrentIndex = 0;
        if (sPreAlarmMode) {
            alarmNoise = instance.mPreAlarmRingtone;
        } else {
            alarmNoise = instance.mRingtone;
        }
        if (alarmNoise != null) {
            File folder = new File(alarmNoise.getPath());
            if (folder.exists() && folder.isDirectory()) {
                sMultiFileMode = true;
            }

            if (inTelephoneCall) {
                sMultiFileMode = false;
            }

            if (sMultiFileMode) {
                collectFiles(context, alarmNoise);
                if (mSongs.size() != 0) {
                    alarmNoise = mSongs.get(0);
                } else {
                    alarmNoise = null;
                    sMultiFileMode = false;
                }
            }
        }
        if (alarmNoise == null) {
            // no ringtone == default
            alarmNoise = getDefaultAlarm(context);
        } else if (AlarmInstance.NO_RINGTONE_URI.equals(alarmNoise)) {
            // silent
            alarmNoise = null;
        }

        if (alarmNoise != null) {
            playAlarm(context, instance, inTelephoneCall, alarmNoise);
        }

        if (instance.mVibrate) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(sVibratePattern, 0, VIBRATION_ATTRIBUTES);
        }
        sStarted = true;
    }

    private static void playAlarm(final Context context,
            final AlarmInstance instance, final boolean inTelephoneCall, final Uri alarmNoise) {

        sMediaPlayer = new MediaPlayer();
        sMediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                LogUtils.e("Error playing " + alarmNoise);
                if (sMultiFileMode) {
                    LogUtils.e("Skipping file");
                    mSongs.remove(alarmNoise);
                    nextSong(context, instance, inTelephoneCall);
                } else {
                    LogUtils.e("Using the fallback ringtone");
                    playAlarm(context, instance, inTelephoneCall, getDefaultAlarm(context));
                }
                return true;
            }
        });

        if (sMultiFileMode) {
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    nextSong(context, instance, inTelephoneCall);
                }
            });
        }

        try {
            // Check if we are in a call. If we are, use the in-call alarm
            // resource at a low volume to not disrupt the call.
            if (inTelephoneCall) {
                LogUtils.v("Using the in-call alarm");
                sIncreasingVolume = false;
                sMediaPlayer.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME);
                setDataSourceFromResource(context, sMediaPlayer,
                        R.raw.in_call_alarm);
            } else {
                sMediaPlayer.setDataSource(context, alarmNoise);
                mCurrentTone = alarmNoise;
                LogUtils.v("next song:" + mCurrentTone);
            }
            startAlarm(context, sMediaPlayer, instance);
        } catch (Exception ex) {
            LogUtils.e("Error playing " + alarmNoise, ex);
            if (sMultiFileMode) {
                LogUtils.e("Skipping file");
                mSongs.remove(alarmNoise);
                nextSong(context, instance, inTelephoneCall);
            } else {
                LogUtils.e("Using the fallback ringtone");
                // The alarmNoise may be on the sd card which could be busy right
                // now. Use the fallback ringtone.
                try {
                    // Must reset the media player to clear the error state.
                    sMediaPlayer.reset();
                    setDataSourceFromResource(context, sMediaPlayer, R.raw.fallbackring);
                    startAlarm(context, sMediaPlayer, instance);
                } catch (Exception ex2) {
                    // At this point we just don't play anything.
                    LogUtils.e("Failed to play fallback ringtone", ex2);
                }
            }
        }
    }

    // Do the common stuff when starting the alarm.
    private static void startAlarm(Context context, MediaPlayer player,
            AlarmInstance instance) throws IOException {
        // do not play alarms if alarm volume is 0
        // this can only happen if "use system alarm volume" is used
        if (sMaxVolume != 0) {
            // only start volume handling on the first invocation
            if (sFirstFile) {
                if (sIncreasingVolume) {
                    sCurrentVolume = INCREASING_VOLUME_START;
                    sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                            sCurrentVolume, 0);
                    LogUtils.v("Starting alarm volume " + sCurrentVolume
                            + " max volume " + sMaxVolume);

                    if (sCurrentVolume < sMaxVolume) {
                        sHandler.sendEmptyMessageDelayed(INCREASING_VOLUME,
                                sVolumeIncreaseSpeed);
                    }
                } else {
                    sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                            sMaxVolume, 0);
                    LogUtils.v("Alarm volume " + sMaxVolume);
                }
                sFirstFile = false;
            }

            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if (!sMultiFileMode) {
                player.setLooping(true);
            }
            player.prepare();
            sAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            player.start();
        }
    }

    private static void setDataSourceFromResource(Context context,
            MediaPlayer player, int res) throws IOException {
        AssetFileDescriptor afd = context.getResources().openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
        }
    }

    private static long getVolumeChangeDelay(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String speed = prefs.getString(SettingsActivity.KEY_VOLUME_INCREASE_SPEED, "5");
        int speedInt = Integer.decode(speed).intValue();
        return speedInt * 1000;
    }

    private static int calcMusicVolumeFromCurrentAlarm() {
        int maxMusicVolume = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int alarmVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int maxAlarmVolume = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);

        if (alarmVolume == 0) {
            return 0;
        }
        return (int)(((float)alarmVolume / (float)maxAlarmVolume) * (float)maxMusicVolume);
    }

    private static void nextSong(final Context context,
            AlarmInstance instance, boolean inTelephoneCall) {
        if (mSongs.size() == 0) {
            sMultiFileMode = false;
            // something bad happend to our play list
            // just fall back to the default
            LogUtils.e("Using the fallback ringtone");
            playAlarm(context, instance, inTelephoneCall, getDefaultAlarm(context));
            return;
        }
        sCurrentIndex++;
        // restart if on end
        if (sCurrentIndex >= mSongs.size()) {
            sCurrentIndex = 0;
            if (sRandomPlayback) {
                Collections.shuffle(mSongs);
            }
        }
        Uri song = mSongs.get(sCurrentIndex);
        playAlarm(context, instance, inTelephoneCall, song);
    }

    private static void collectFiles(Context context, Uri folderUri) {
        mSongs.clear();

        File folder = new File(folderUri.getPath());
        if (folder.exists() && folder.isDirectory()) {
            for (final File fileEntry : folder.listFiles()) {
                if (!fileEntry.isDirectory()) {
                    mSongs.add(Uri.fromFile(fileEntry));
                }
            }
            if (sRandomPlayback) {
                Collections.shuffle(mSongs);
            }
        }
    }
}
