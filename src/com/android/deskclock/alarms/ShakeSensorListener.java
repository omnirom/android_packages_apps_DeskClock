/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.deskclock.alarms;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;


public class ShakeSensorListener implements SensorEventListener {
    private static float SENSITIVITY = 10;
    private static final int BUFFER = 5;
    private float[] gravity = new float[3];
    private float average = 0;
    private int fill = 0;
    private boolean mStopped;
    private Runnable mShakeAction;

    public ShakeSensorListener(Runnable shakeAction) {
        mShakeAction = shakeAction;
    }

    public void reset() {
        mStopped = false;
        average = 0;
        fill = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
            final float alpha = 0.8F;

        if (mStopped) {
            return;
        }
        for (int i = 0; i < 3; i++) {
             gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i];
        }
        float x = event.values[0] - gravity[0];
        float y = event.values[1] - gravity[1];
        float z = event.values[2] - gravity[2];

        if (fill <= BUFFER) {
            average += Math.abs(x) + Math.abs(y) + Math.abs(z);
            fill++;
        } else {
            if (average / BUFFER >= SENSITIVITY) {
                mStopped = true;
                mShakeAction.run();
            }
            average = 0;
            fill = 0;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
