package com.simon.core.pullrefresh.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.NonNull;


public class RingDrawable extends RefreshDrawable {

    private static final int MAX_LEVEL = 200;

    private boolean isRunning;
    private RectF mBounds;
    private Paint mPaint;
    private Path mPath;
    private float mAngle;
    private int[] mColorSchemeColors;
    private int mLevel;
    private float mDegrees;

    public RingDrawable(Context context, int finalOffset) {
        super(context, finalOffset);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp2px(3));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPath = new Path();
    }

    @Override
    public void setColorSchemeColors(int[] colorSchemeColors) {
        mColorSchemeColors = colorSchemeColors;
    }

    @Override
    public void start() {
        mLevel = 50;
        isRunning = true;
        invalidateSelf();
    }

    private void updateLevel(int level) {
        int animationLevel = level == MAX_LEVEL ? 0 : level;

        int stateForLevel = (animationLevel / 50);

        float percent = level % 50 / 50f;
        int startColor = mColorSchemeColors[stateForLevel];
        int endColor = mColorSchemeColors[(stateForLevel + 1) % mColorSchemeColors.length];
        mPaint.setColor(evaluate(percent, startColor, endColor));

        mDegrees = 360 * percent;
    }

    @Override
    public void stop() {
        isRunning = false;
        mDegrees = 0;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        int length = getFinalDragOffset();
        mBounds = new RectF(bounds.width() / 2 - length / 2, bounds.top, bounds.width() / 2 + length / 2, bounds.top + length);
        mBounds.inset(dp2px(15), dp2px(15));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.rotate(mDegrees, mBounds.centerX(), mBounds.centerY());
        drawRing(canvas);
        canvas.restore();
        if (isRunning) {
            mLevel = mLevel >= MAX_LEVEL ? 0 : mLevel + 1;
            updateLevel(mLevel);
            invalidateSelf();
        }
    }

    private void drawRing(Canvas canvas) {
        mPath.reset();
        mPath.arcTo(mBounds, 270, mAngle, true);
        canvas.drawPath(mPath, mPaint);
    }



    private int evaluate(float fraction, int startValue, int endValue) {
        int startA = (startValue >> 24) & 0xff;
        int startR = (startValue >> 16) & 0xff;
        int startG = (startValue >> 8) & 0xff;
        int startB = startValue & 0xff;

        int endA = (endValue >> 24) & 0xff;
        int endR = (endValue >> 16) & 0xff;
        int endG = (endValue >> 8) & 0xff;
        int endB = endValue & 0xff;

        return ((startA + (int) (fraction * (endA - startA))) << 24) |
                ((startR + (int) (fraction * (endR - startR))) << 16) |
                ((startG + (int) (fraction * (endG - startG))) << 8) |
                ((startB + (int) (fraction * (endB - startB))));
    }

    @Override
    public void onDrag(float dragPercent) {
        mPaint.setColor(evaluate(dragPercent, mColorSchemeColors[0], mColorSchemeColors[1]));
        mAngle = 340 * dragPercent;
    }

    @Override
    public void onOffsetTopAndBottom(int offset) {
        invalidateSelf();
    }
}
