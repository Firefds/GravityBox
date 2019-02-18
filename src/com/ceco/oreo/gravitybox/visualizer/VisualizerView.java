/*
* Copyright (C) 2015 The CyanogenMod Project
* Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.ceco.oreo.gravitybox.visualizer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class VisualizerView extends View {

    private Paint mPaint;
    private ValueAnimator[] mValueAnimators;
    private float[] mFFTPoints;
    private float mDbCapValue = 16f;

    private boolean mSupportsVerticalPosition = false;
    private boolean mIsVertical = false;
    private boolean mIsVerticalLeft = false;

    VisualizerView(Context context) {
        super(context, null, 0);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mFFTPoints = new float[128];
        loadValueAnimators();
    }

    void setDbCapValue(float dbCap) {
        mDbCapValue = dbCap;
    }

    void setSupportsVerticalPosition(boolean value) {
        if (mSupportsVerticalPosition != value) {
            mSupportsVerticalPosition = value;
            mIsVerticalLeft &= mSupportsVerticalPosition;
            onSizeChanged(getWidth(), getHeight(), 0, 0);
        }
    }

    public void setVerticalLeft(boolean isleft) {
        if (mSupportsVerticalPosition && mIsVerticalLeft != isleft) {
            mIsVerticalLeft = isleft;
            onSizeChanged(getWidth(), getHeight(), 0, 0);
        }
    }

    private void loadValueAnimators() {
        if (mValueAnimators != null) {
            for (int i = 0; i < 32; i++) {
                mValueAnimators[i].cancel();
            }
        }

        mValueAnimators = new ValueAnimator[32];
        for (int i = 0; i < 32; i++) {
            final int j;
            if (mIsVertical) {
                j = i * 4;
            } else {
                j = i * 4 + 1;
            }
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(128);
            mValueAnimators[i].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mFFTPoints[j] = (float) animation.getAnimatedValue();
                    postInvalidate();
                }
            });
        }
    }

    void setData(byte[] fft) {
        byte rfk, ifk;
        int dbValue;
        float magnitude;
        for (int i = 0; i < 32; i++) {
            mValueAnimators[i].cancel();
            rfk = fft[i * 2 + 2];
            ifk = fft[i * 2 + 3];
            magnitude = rfk * rfk + ifk * ifk;
            dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

            if (mIsVertical) {
                if (mIsVerticalLeft) {
                    mValueAnimators[i].setFloatValues(mFFTPoints[i * 4],
                            dbValue * mDbCapValue);
                } else {
                    mValueAnimators[i].setFloatValues(mFFTPoints[i * 4],
                            mFFTPoints[2] - (dbValue * mDbCapValue));
                }
            } else {
                mValueAnimators[i].setFloatValues(mFFTPoints[i * 4 + 1],
                        mFFTPoints[3] - (dbValue * mDbCapValue));
            }
            mValueAnimators[i].start();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mIsVertical = mSupportsVerticalPosition && (h > w);
        loadValueAnimators();
        if (mIsVertical) {
            setVertical(w, h);
        } else {
            setHorizontal(w, h);
        }
    }

    private void setHorizontal(int w, int h) {
        float barUnit = w / 32f;
        float barWidth = barUnit * 8f / 9f;
        barUnit = barWidth + (barUnit - barWidth) * 32f / 31f;
        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 1] = h;
            mFFTPoints[i * 4 + 3] = h;
        }
    }

    private void setVertical(int w, int h) {
        float barUnit = h / 32f;
        float barHeight = barUnit * 8f / 9f;
        barUnit = barHeight + (barUnit - barHeight) * 32f / 31f;
        mPaint.setStrokeWidth(barHeight);
        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4 + 1] = mFFTPoints[i * 4 + 3] = i * barUnit + (barHeight / 2);
            mFFTPoints[i * 4] = mIsVerticalLeft ? 0 : w;
            mFFTPoints[i * 4 + 2] = mIsVerticalLeft ? 0 : w;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawLines(mFFTPoints, mPaint);
    }

    void setColor(int color) {
        mPaint.setColor(color);
    }
}
