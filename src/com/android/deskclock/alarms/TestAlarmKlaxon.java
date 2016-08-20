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

package com.android.deskclock.alarms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.res.AssetFileDescriptor;
import android.preference.PreferenceManager;

import com.android.deskclock.LogUtils;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.SettingsActivity;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.R;
import com.android.deskclock.Utils;

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
        public void onInfo(String msg);
        public void startProgress();
        public void stopProgress();
    };

    private static int getAudioStream(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String stream = prefs.getString(SettingsActivity.KEY_AUDIO_STREAM, "0");
        int streamInt = Integer.decode(stream).intValue();
        return streamInt == 0 ? AudioManager.STREAM_MUSIC : AudioManager.STREAM_ALARM;
    }

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
        sSavedVolume = sAudioManager.getStreamVolume(getAudioStream(context));
        if (preAlarm) {
            sMaxVolume = instance.preAlarmVolume;
        } else {
            sMaxVolume = instance.alarmVolume;
        }
        sRandomPlayback = instance.getRandomMode(preAlarm);
        if (sMaxVolume == -1){
            // calc from current alarm volume
            sMaxVolume = calcMusicVolumeFromCurrentAlarm(context);
        }

        sAudioManager.setStreamVolume(getAudioStream(context), sMaxVolume, 0);
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
                // can fail if no external storage permissions
                try {
                    collectFiles(context, alarmNoise);
                    if (mSongs.size() != 0) {
                        sErrorHandler.onInfo("Scanned files: " + mSongs.size());
                        alarmNoise = mSongs.get(0);
                    } else {
                        sError = true;
                        sErrorHandler.onError("Empty folder");
                        return;
                    }
                 } catch (Exception ex) {
                    sError = true;
                    sErrorHandler.onError("Error accessing folder");
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
        if (alarmNoise != null && sTestStarted) {
            playTestAlarm(context, instance, alarmNoise);
        }
    }

    public static void stopTest(Context context, Alarm instance) {
        if (!sTestStarted) {
            return;
        }

        // reset to default from before
        sAudioManager.setStreamVolume(getAudioStream(context),
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
                sErrorHandler.onError("Error playing alarm sound");
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
            LogUtils.v("Using audio stream " + (getAudioStream(context) == AudioManager.STREAM_MUSIC ? "Music" : "Alarm"));

            sMediaPlayer.setAudioStreamType(getAudioStream(context));
            if (!sMultiFileMode) {
                sMediaPlayer.setLooping(true);
            }
            sMediaPlayer.prepare();
            sAudioManager.requestAudioFocus(null, getAudioStream(context),
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            sMediaPlayer.start();
        } catch (Exception ex) {
            sError = true;
            sErrorHandler.onError("Error playing alarm sound");
        }
    }

    private static int calcMusicVolumeFromCurrentAlarm(Context context) {
        int maxMusicVolume = sAudioManager.getStreamMaxVolume(getAudioStream(context));
        int alarmVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int maxAlarmVolume = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);

        if (alarmVolume == 0) {
            return 0;
        }
        return (int)(((float)alarmVolume / (float)maxAlarmVolume) * (float)maxMusicVolume);
    }

    private static void collectFiles(Context context, Uri folderUri) {
        mSongs.clear();
        sErrorHandler.startProgress();
        File folder = new File(folderUri.getPath());
        if (folder.exists() && folder.isDirectory()) {
            for (final File fileEntry : folder.listFiles()) {
                if (!fileEntry.isDirectory()) {
                    if (Utils.isValidAudioFile(fileEntry.getName())) {
                        mSongs.add(Uri.fromFile(fileEntry));
                    }
                } else {
                    collectSub(context, fileEntry);
                }
            }
            if (sRandomPlayback) {
                Collections.shuffle(mSongs);
            }
        }
        sErrorHandler.stopProgress();
    }

    private static void collectSub(Context context, File folder) {
        if (!sTestStarted) {
            return;
        }
        if (folder.exists() && folder.isDirectory()) {
            for (final File fileEntry : folder.listFiles()) {
                if (!fileEntry.isDirectory()) {
                    if (Utils.isValidAudioFile(fileEntry.getName())) {
                        mSongs.add(Uri.fromFile(fileEntry));
                    }
                } else {
                    collectSub(context, fileEntry);
                }
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
