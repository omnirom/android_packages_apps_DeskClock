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
 * Manages playing ringtone and vibrating the device.
 */
public class AlarmKlaxon {
    private static final long[] sVibratePattern = new long[] { 500, 500 };

    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;

    // 5sec * 7 volume levels = 30sec till max volume
    private static final long INCREASING_VOLUME_DELAY = 5000;
    // 10sec * 7 volume levels = 60sec till max volume
    private static final long INCREASING_VOLUME_DELAY_SLOW = 15000;
    private static final int INCREASING_VOLUME_START = 1;
    private static final int INCREASING_VOLUME_DELTA = 1;

    private static boolean sStarted = false;
    private static AudioManager sAudioManager = null;
    private static MediaPlayer sMediaPlayer = null;
    private static boolean sMediaStarted = false;
    private static boolean sPreFiredMode = false;
    private static boolean sTestStarted = false;
    
    private static int sCurrentVolume = INCREASING_VOLUME_START;
    private static int sAlarmVolumeSetting;
    private static int sMaxAlarmVolumeSetting;

    // Internal messages
    private static final int INCREASING_VOLUME = 1001;

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case INCREASING_VOLUME:
                if (sStarted) {
                    sCurrentVolume += INCREASING_VOLUME_DELTA;
                    Log.d("Increasing alarm volume to " + sCurrentVolume);
                    if (sMediaStarted) {
                        sAudioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC, sCurrentVolume, 0);
                    } else {
                        sAudioManager.setStreamVolume(
                                AudioManager.STREAM_ALARM, sCurrentVolume, 0);
                    }
                    if (sCurrentVolume <= sMaxAlarmVolumeSetting) {
                        sHandler.sendEmptyMessageDelayed(INCREASING_VOLUME,
                                getVolumeChangeDelay());
                    }
                }
                break;
            }
        }
    };

    public static void stop(Context context) {
        Log.v("AlarmKlaxon.stop()");

        if (sStarted) {
            sStarted = false;
            sHandler.removeMessages(INCREASING_VOLUME);
            if (sMediaStarted) {
                dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                sMediaStarted = false;
                // reset to default from before
                sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        sAlarmVolumeSetting, 0);
            } else {
                // Stop audio playing
                if (sMediaPlayer != null) {
                    sMediaPlayer.stop();
                    sMediaPlayer.release();
                    sMediaPlayer = null;

                    // reset to default from before
                    sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                            sAlarmVolumeSetting, 0);
                    sAudioManager.abandonAudioFocus(null);
                    sAudioManager = null;
                }
                sPreFiredMode = false;
            }
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
        Log.v("AlarmKlaxon.start() " + instance);
        // Make sure we are stop before starting
        stop(context);

        sPreFiredMode = false;
        if (instance.mAlarmState == AlarmInstance.PRE_FIRED_STATE) {
            sPreFiredMode = true;
        }

        final Context appContext = context.getApplicationContext();
        sAudioManager = (AudioManager) appContext
                .getSystemService(Context.AUDIO_SERVICE);
        // save current value
        if (!sPreFiredMode && instance.mMediaStart) {
            sAlarmVolumeSetting = sAudioManager
                    .getStreamVolume(AudioManager.STREAM_MUSIC);
        } else {
            sAlarmVolumeSetting = sAudioManager
                    .getStreamVolume(AudioManager.STREAM_ALARM);
        }
        
        if (sPreFiredMode ) {
            sMaxAlarmVolumeSetting = instance.mPreAlarmVolume;
            if (sMaxAlarmVolumeSetting == -1){
                sMaxAlarmVolumeSetting = sAlarmVolumeSetting;
            }
        } else if (!instance.mMediaStart) {
            sMaxAlarmVolumeSetting = instance.mAlarmVolume;
            if (sMaxAlarmVolumeSetting == -1){
                sMaxAlarmVolumeSetting = sAlarmVolumeSetting;
            }
        }
        
        sMediaStarted = false;

        if (!sPreFiredMode && instance.mMediaStart) {
            // do not play alarms if stream volume is 0 (typically because
            // ringer mode is silent).
            if (sAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != 0) {
                sMediaStarted = true;
                if (instance.mIncreasingVolume) {
                    sCurrentVolume = INCREASING_VOLUME_START;
                    sAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                            sCurrentVolume, 0);
                    Log.d("Starting alarm volume " + sCurrentVolume
                            + " max volume " + sAlarmVolumeSetting);

                    if (sCurrentVolume < sAlarmVolumeSetting) {
                        sHandler.sendEmptyMessageDelayed(INCREASING_VOLUME,
                                INCREASING_VOLUME_DELAY);
                    }
                }
                dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            }
        } else {
            Uri alarmNoise = null;
            if (sPreFiredMode) {
                alarmNoise = instance.mPreAlarmRingtone;
            } else {
                alarmNoise = instance.mRingtone;
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
                playAlarm(context, instance, inTelephoneCall, alarmNoise);
            }
        }
        if (instance.mVibrate) {
            Vibrator vibrator = (Vibrator) context
                    .getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(sVibratePattern, 0);
        }
        sStarted = true;
        sTestStarted = false;
    }

    public static void test(final Context context, Alarm instance, boolean preAlarm) {
        Log.v("AlarmKlaxon.test()");
        if (sStarted) {
            return;
        }

        sTestStarted = true;

        final Context appContext = context.getApplicationContext();
        sAudioManager = (AudioManager) appContext
                .getSystemService(Context.AUDIO_SERVICE);

        // save current value
        if (!instance.mediaStart) {
            sAlarmVolumeSetting = sAudioManager
                    .getStreamVolume(AudioManager.STREAM_ALARM);
        
            if (preAlarm) {
                sMaxAlarmVolumeSetting = instance.preAlarmVolume;
                if (sMaxAlarmVolumeSetting == -1){
                    sMaxAlarmVolumeSetting = sAlarmVolumeSetting;
                }
            } else {
                sMaxAlarmVolumeSetting = instance.alarmVolume;
                if (sMaxAlarmVolumeSetting == -1){
                    sMaxAlarmVolumeSetting = sAlarmVolumeSetting;
                }
            }
        }

        if (instance.mediaStart) {
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else {
            sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    sMaxAlarmVolumeSetting, 0);

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
        if (instance.mediaStart) {
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else {
            // reset to default from before
            if (sAudioManager != null) {
                sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                        sAlarmVolumeSetting, 0);
            }
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
            sMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            sMediaPlayer.setLooping(false);
            sMediaPlayer.prepare();
            sAudioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            sMediaPlayer.start();
        } catch (Exception ex) {
        }
    }

    private static void playAlarm(final Context context,
            AlarmInstance instance, boolean inTelephoneCall, Uri alarmNoise) {

        // TODO: Reuse mMediaPlayer instead of creating a new one and/or use
        // RingtoneManager.
        sMediaPlayer = new MediaPlayer();
        sMediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e("Error occurred while playing audio. Stopping AlarmKlaxon.");
                AlarmKlaxon.stop(context);
                return true;
            }
        });

        try {
            // Check if we are in a call. If we are, use the in-call alarm
            // resource at a low volume to not disrupt the call.
            if (inTelephoneCall) {
                Log.v("Using the in-call alarm");
                sMediaPlayer.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME);
                setDataSourceFromResource(context, sMediaPlayer,
                        R.raw.in_call_alarm);
            } else {
                sMediaPlayer.setDataSource(context, alarmNoise);
            }
            startAlarm(context, sMediaPlayer, instance);
        } catch (Exception ex) {
            Log.v("Using the fallback ringtone");
            // The alarmNoise may be on the sd card which could be busy right
            // now. Use the fallback ringtone.
            try {
                // Must reset the media player to clear the error state.
                sMediaPlayer.reset();
                sMediaPlayer.setDataSource(context, getDefaultAlarm(context));
                startAlarm(context, sMediaPlayer, instance);
            } catch (Exception ex2) {
                // At this point we just don't play anything.
                Log.e("Failed to play fallback ringtone", ex2);
            }
        }
    }

    // Do the common stuff when starting the alarm.
    private static void startAlarm(Context context, MediaPlayer player,
            AlarmInstance instance) throws IOException {
        // do not play alarms if stream volume is 0 (typically because ringer
        // mode is silent).
        if (sAlarmVolumeSetting != 0) {
            if (instance.mIncreasingVolume) {
                sCurrentVolume = INCREASING_VOLUME_START;
                sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                        sCurrentVolume, 0);
                Log.d("Starting alarm volume " + sCurrentVolume
                        + " max volume " + sAlarmVolumeSetting);
            } else {
                sAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                        sMaxAlarmVolumeSetting, 0);
            }
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            player.setLooping(true);
            player.prepare();
            sAudioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            player.start();

            if (instance.mIncreasingVolume && sCurrentVolume < sMaxAlarmVolumeSetting) {
                sHandler.sendEmptyMessageDelayed(INCREASING_VOLUME,
                        getVolumeChangeDelay());
            }
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

    private static long getVolumeChangeDelay() {
        if (sPreFiredMode) {
            return INCREASING_VOLUME_DELAY;
        } else {
            return INCREASING_VOLUME_DELAY_SLOW;
        }
    }
}
