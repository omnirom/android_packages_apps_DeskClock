/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.deskclock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.content.Context;

import com.android.deskclock.AnimatorUtils;
import com.android.deskclock.LogUtils;

public class DeskClockFragment extends Fragment {

    protected ImageButton mFab;
    protected ImageButton mLeftButton;
    protected ImageButton mRightButton;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
    }

    public void onPageChanged(int page) {
        // Do nothing here , only in derived classes
    }

    public void onFabClick(View view){
        // Do nothing here , only in derived classes
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final Activity activity = getActivity();
        if (activity instanceof DeskClock) {
            final DeskClock deskClockActivity = (DeskClock) activity;
            mFab = deskClockActivity.getFab();
            mLeftButton = deskClockActivity.getLeftButton();
            mRightButton = deskClockActivity.getRightButton();
        }
    }

    public void setFabAppearance() {
        // Do nothing here , only in derived classes
    }

    public void setLeftRightButtonAppearance() {
        // Do nothing here , only in derived classes
    }

    public void onLeftButtonClick(View view) {
        // Do nothing here , only in derived classes
    }

    public void onRightButtonClick(View view) {
        // Do nothing here , only in derived classes
    }

    protected Animator setButtonVisible(ImageButton button, boolean show) {
        final Animator buttonAnimator = AnimatorUtils.getScaleAnimator(
                button, show ? 0.0f : 1.0f, show ? 1.0f : 0.0f);

        return buttonAnimator;
    }

    protected void restoreScale(View view) {
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
    }

    protected void setHiddenScale(View view) {
        view.setScaleX(0.0f);
        view.setScaleY(0.0f);
    }

    protected AnimatorSet getButtonTransition(final boolean leftVisible, final boolean rightVisible) {
        Animator rightButtonAnimator = null;
        Animator leftButtonAnimator = null;

        if ((leftVisible && mLeftButton.getVisibility() != View.VISIBLE) ||
            (!leftVisible && mLeftButton.getVisibility() != View.GONE)) {
            if (leftVisible && mLeftButton.getVisibility() != View.VISIBLE) {
                setHiddenScale(mLeftButton);
                mLeftButton.setVisibility(View.VISIBLE);
            }
            leftButtonAnimator = setButtonVisible(mLeftButton, leftVisible);
        }
        if ((rightVisible && mRightButton.getVisibility() != View.VISIBLE) ||
            (!rightVisible && mRightButton.getVisibility() != View.GONE)) {
            if (rightVisible && mRightButton.getVisibility() != View.VISIBLE) {
                setHiddenScale(mRightButton);
                mRightButton.setVisibility(View.VISIBLE);
            }
            rightButtonAnimator = setButtonVisible(mRightButton, rightVisible);
        }

        if (rightButtonAnimator != null || leftButtonAnimator != null) {
            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLeftButton.setVisibility(leftVisible ? View.VISIBLE : View.GONE);
                    mRightButton.setVisibility(rightVisible ? View.VISIBLE : View.GONE);
                    restoreScale(mLeftButton);
                    restoreScale(mRightButton);
                }
            });
            if (rightButtonAnimator != null && leftButtonAnimator != null) {
                animatorSet.play(leftButtonAnimator).with(rightButtonAnimator);
            } else if (leftButtonAnimator != null) {
                animatorSet.play(leftButtonAnimator);
            } else if (rightButtonAnimator != null) {
                animatorSet.play(rightButtonAnimator);
            }
            return animatorSet;
        }
        return null;
    }

    protected AnimatorSet getFabButtonTransition(final boolean fabVisible) {
        Animator fabButtonAnimator = null;

        if ((fabVisible && mFab.getVisibility() != View.VISIBLE) ||
            (!fabVisible && mFab.getVisibility() != View.GONE)) {
            if (fabVisible && mFab.getVisibility() != View.VISIBLE) {
                setHiddenScale(mFab);
                mFab.setVisibility(View.VISIBLE);
            }
            fabButtonAnimator = setButtonVisible(mFab, fabVisible);
        }

        if (fabButtonAnimator != null) {
            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mFab.setVisibility(fabVisible ? View.VISIBLE : View.GONE);
                    restoreScale(mFab);
                }
            });
            if (fabButtonAnimator != null) {
                animatorSet.play(fabButtonAnimator);
            }
            return animatorSet;
        }
        return null;
    }
}
