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

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.media.IAudioService;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.ServiceManager;
import android.hardware.input.InputManager;
import android.view.KeyEvent;

import com.android.deskclock.Log;
import com.android.deskclock.R;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.provider.Alarm;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * for testing alarm tones
 */
public class TestAlarmKlaxon {

    private static AudioManager sAudioManager = null;
    private static MediaPlayer sMediaPlayer = null;
    private static boolean sTestStarted = false;
    private static int sAlarmVolumeSetting;
    private static int sMaxAlarmVolumeSetting;

    private static Uri getDefaultAlarm(Context context) {
        Uri alarmNoise = RingtoneManager.getActualDefaultRingtoneUri(context,
                    RingtoneManager.TYPE_ALARM);
        if (alarmNoise == null) {
            alarmNoise = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        return alarmNoise;
    }

    public static void test(final Context context, Alarm instance, boolean preAlarm) {
        sTestStarted = true;

        final Context appContext = context.getApplicationContext();
        sAudioManager = (AudioManager) appContext
                .getSystemService(Context.AUDIO_SERVICE);

        // save current value
        sAlarmVolumeSetting = sAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (preAlarm) {
            sMaxAlarmVolumeSetting = instance.preAlarmVolume;
        } else {
            sMaxAlarmVolumeSetting = instance.alarmVolume;
        }
        if (sMaxAlarmVolumeSetting == -1){
            // calc from current alarm volume
            int alarmVolume = sAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            sMaxAlarmVolumeSetting = calcMusicVolumeFromCurrentAlarm();
            Log.d("alarm volume " + alarmVolume + " music volume " + sMaxAlarmVolumeSetting);
        }

        sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sMaxAlarmVolumeSetting, 0);

        if (instance.mediaStart) {
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else {
            Uri alarmNoise = null;
            if (preAlarm) {
                alarmNoise = instance.preAlarmAlert;
            } else {
                alarmNoise = instance.alert;
            }
            if (alarmNoise == null) {
                // no ringtone == default
                alarmNoise = getDefaultAlarm(context);
            } else if (AlarmInstance.NO_RINGTONE_URI.equals(alarmNoise)) {
                // silent
                alarmNoise = null;
            }
            if (alarmNoise != null) {
                Ringtone ringTone = RingtoneManager.getRingtone(context, alarmNoise);
                if (ringTone != null) {
                    Log.v("Using ringtone: " + ringTone.getTitle(context));
                }
                playTestAlarm(context, alarmNoise);
            }
        }
    }

    public static void stopTest(Alarm instance) {
        if (!sTestStarted) {
            return;
        }
        // reset to default from before
        sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        sAlarmVolumeSetting, 0);

        if (instance.mediaStart) {
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else {
            if (sMediaPlayer != null) {
                sMediaPlayer.stop();
                sMediaPlayer.release();
                sMediaPlayer = null;
                sAudioManager.abandonAudioFocus(null);
                sAudioManager = null;
            }
        }
        sTestStarted = false;
    }

    private static void playTestAlarm(final Context context, Uri alarmNoise) {
        sMediaPlayer = new MediaPlayer();
        sMediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e("Error occurred while playing audio. Stopping AlarmKlaxon.");
                sMediaPlayer.stop();
                return true;
            }
        });

        try {
            sMediaPlayer.setDataSource(context, alarmNoise);
            sMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            sMediaPlayer.setLooping(false);
            sMediaPlayer.prepare();
            sAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            sMediaPlayer.start();
        } catch (Exception ex) {
        }
    }

    private static IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub
                .asInterface(ServiceManager.checkService(Context.AUDIO_SERVICE));
        if (audioService == null) {
            Log.w("Unable to find IAudioService interface.");
        }
        return audioService;
    }

    private static void dispatchMediaKeyWithWakeLockToAudioService(int keycode) {
        IAudioService audioService = getAudioService();
        if (audioService != null) {
            try {
                KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
                        keycode, 0);
                audioService.dispatchMediaKeyEventUnderWakelock(event);
                event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
                audioService.dispatchMediaKeyEventUnderWakelock(event);
            } catch (RemoteException e) {
                Log.e("dispatchMediaKeyEvent threw exception " + e);
            }
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
}
