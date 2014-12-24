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
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.io.IOException;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.content.res.AssetFileDescriptor;

import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.R;

/**
 * for testing alarm tones
 */
public class TestAlarmKlaxon {

    private static AudioManager sAudioManager = null;
    private static MediaPlayer sMediaPlayer = null;
    private static boolean sTestStarted = false;
    private static boolean sMultiFileMode = false;
    private static int sSavedVolume;
    private static int sMaxVolume;
    private static List<Uri> mSongs = new ArrayList<Uri>();
    private static Uri mCurrentTone;
    private static int sCurrentIndex;
    private static boolean sRandomPlayback;
    private static ErrorHandler sErrorHandler;
    private static boolean sError;
    private static boolean sFallbackRingtone;

    public interface ErrorHandler {
        public void onError(String msg);
    };

    private static Uri getDefaultAlarm(Context context) {
        Uri alarmNoise = RingtoneManager.getActualDefaultRingtoneUri(context,
                    RingtoneManager.TYPE_ALARM);
        if (alarmNoise == null) {
            alarmNoise = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        return alarmNoise;
    }

    public static void test(final Context context, Alarm instance, boolean preAlarm, ErrorHandler errorHandler) {
        sTestStarted = true;
        sError = false;
        sErrorHandler = errorHandler;

        final Context appContext = context.getApplicationContext();
        sAudioManager = (AudioManager) appContext
                .getSystemService(Context.AUDIO_SERVICE);

        // save current value
        sSavedVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (preAlarm) {
            sMaxVolume = instance.preAlarmVolume;
        } else {
            sMaxVolume = instance.alarmVolume;
        }
        sRandomPlayback = instance.getRandomMode(preAlarm);
        if (sMaxVolume == -1){
            // calc from current alarm volume
            int alarmVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            sMaxVolume = calcMusicVolumeFromCurrentAlarm();
        }

        sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sMaxVolume, 0);
        sFallbackRingtone = instance.isFallbackRingtone(context, preAlarm);

        Uri alarmNoise = null;
        sMultiFileMode = false;
        sCurrentIndex = 0;

        if (preAlarm) {
            alarmNoise = instance.preAlarmAlert;
        } else {
            alarmNoise = instance.alert;
        }
        if (alarmNoise != null) {
            File folder = new File(alarmNoise.getPath());
            if (folder.exists() && folder.isDirectory()) {
                sMultiFileMode = true;
            }
            if (sMultiFileMode) {
                collectFiles(context, alarmNoise);
                if (mSongs.size() != 0) {
                    alarmNoise = mSongs.get(0);
                } else {
                    sError = true;
                    sErrorHandler.onError("Empty folder");
                    return;
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
            playTestAlarm(context, instance, alarmNoise);
        }
    }

    public static void stopTest(Alarm instance) {
        if (!sTestStarted) {
            return;
        }
        // reset to default from before
        sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        sSavedVolume, 0);

        if (sMediaPlayer != null) {
            sMediaPlayer.stop();
            sMediaPlayer.release();
            sMediaPlayer = null;
            sAudioManager.abandonAudioFocus(null);
            sAudioManager = null;
        }

        sTestStarted = false;
    }

    private static void playTestAlarm(final Context context, final Alarm instance, final Uri alarmNoise) {
        sMediaPlayer = new MediaPlayer();
        sMediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                sError = true;
                sErrorHandler.onError("Error playing " + alarmNoise);
                return true;
            }
        });
        if (sMultiFileMode) {
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    nextSong(context, instance);
                }
            });
        }

        try {
            mCurrentTone = alarmNoise;

            if (sFallbackRingtone) {
                setDataSourceFromResource(context, sMediaPlayer, R.raw.fallbackring);
            } else {
                sMediaPlayer.setDataSource(context, alarmNoise);
            }
            sMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if (!sMultiFileMode) {
                sMediaPlayer.setLooping(true);
            }
            sMediaPlayer.prepare();
            sAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            sMediaPlayer.start();
        } catch (Exception ex) {
            sError = true;
            sErrorHandler.onError("Error playing " + alarmNoise);
        }
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

    private static void nextSong(Context context, Alarm instance) {
        if (sError) {
            return;
        }
        if (mSongs.size() == 0) {
            // some thing bad happend to our play list
            sError = true;
            sErrorHandler.onError("Empty folder");
            return;
        }
        sCurrentIndex++;
        // restart if on end
        if (sCurrentIndex >= mSongs.size()) {
            if (sRandomPlayback) {
                Collections.shuffle(mSongs);
            }
            sCurrentIndex = 0;
        }
        Uri song = mSongs.get(sCurrentIndex);
        playTestAlarm(context, instance, song);
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
}
