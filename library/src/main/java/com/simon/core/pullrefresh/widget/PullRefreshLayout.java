package com.simon.core.pullrefresh.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.ImageView;

import com.simon.core.pullrefresh.R;

import com.simon.core.pullrefresh.drawable.IRefreshDrawable;
import com.simon.core.pullrefresh.drawable.RingDrawable;

/**
 * PullRefreshLayout
 * Created by simon on 17-5-13.
 */

public class PullRefreshLayout extends ViewGroup {

    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final int DRAG_MAX_DISTANCE = 64;
    private static final int INVALID_POINTER = -1;
    private static final float DRAG_RATE = .5f;

    private View mTarget;
    private ImageView mRefreshView;
    private Interpolator mDecelerateInterpolator;
    private int mTouchSlop;
    private int mSpinnerFinalOffset;
    private int mTotalDragDistance;
    private IRefreshDrawable mRefreshDrawable;
    private int mCurrentOffsetTop;
    private boolean mRefreshing;
    private int mActivePointerId;
    private boolean mIsBeingDragged;
    private float mInitialMotionY;
    private int mFrom;
    private boolean mNotify;
    private OnRefreshListener mListener;
    private int[] mColorSchemeColors;

    public int mDurationToStartPosition;
    public int mDurationToCorrectPosition;
    private int mInitialOffsetTop;
    private boolean mDispatchTargetTouchDown;
    private float mDragPercent;

    public PullRefreshLayout(Context context) {
        this(context, null);
    }

    public PullRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.refresh_PullRefreshLayout);
        final int colorsId = a.getResourceId(R.styleable.refresh_PullRefreshLayout_refreshColors, 0);
        final int colorId = a.getResourceId(R.styleable.refresh_PullRefreshLayout_refreshColor, 0);
        a.recycle();
        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        int defaultDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        mDurationToStartPosition = defaultDuration;
        mDurationToCorrectPosition = defaultDuration;
        mSpinnerFinalOffset = mTotalDragDistance = dp2px(DRAG_MAX_DISTANCE);
        mRefreshView = new ImageView(context);
        if (colorsId > 0) {
            mColorSchemeColors = context.getResources().getIntArray(colorsId);
        } else {
            mColorSchemeColors = new int[]{Color.rgb(0xC9, 0x34, 0x37), Color.rgb(0x37, 0x5B, 0xF1), Color.rgb(0xF7, 0xD2, 0x3E), Color.rgb(0x34, 0xA3, 0x50)};
        }

        if (colorId > 0) {
            mColorSchemeColors = new int[]{ContextCompat.getColor(context, colorId)};
        }
        setDefaultRefreshStyle();
        mRefreshView.setVisibility(View.GONE);

        setWillNotDraw(false);
        ViewCompat.setChildrenDrawingOrderEnabled(this, true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addView(mRefreshView);
    }

    public void setColorSchemeColors(int... colorSchemeColors) {
        mColorSchemeColors = colorSchemeColors;
        mRefreshDrawable.setColorSchemeColors(colorSchemeColors);
    }

    public void setDefaultRefreshStyle() {
        setRefreshing(false);
        mRefreshDrawable = new RingDrawable(this.getContext(), this.getFinalOffset());
        mRefreshDrawable.setColorSchemeColors(mColorSchemeColors);
        mRefreshView.setImageDrawable(((Drawable) mRefreshDrawable));
    }

    public void setRefreshDrawable(IRefreshDrawable drawable) {
        setRefreshing(false);
        mRefreshDrawable = drawable;
        mRefreshView.setImageDrawable((Drawable) mRefreshDrawable);
    }

    public int getFinalOffset() {
        return mSpinnerFinalOffset;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        ensureTarget();
        if (mTarget == null)
            return;

        widthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingRight() - getPaddingLeft(), MeasureSpec.EXACTLY);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY);
        mTarget.measure(widthMeasureSpec, heightMeasureSpec);
        mRefreshView.measure(widthMeasureSpec, heightMeasureSpec);
    }

    private void ensureTarget() {
        if (mTarget != null)
            return;
        if (getChildCount() > 0) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != mRefreshView)
                    mTarget = child;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        if (!isEnabled() || (canChildScrollUp() && !mRefreshing)) {
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!mRefreshing) {
                    setTargetOffsetTop(0);
                }
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                final float initialMotionY = getMotionEventY(ev, mActivePointerId);
                if (initialMotionY == -1) {
                    return false;
                }
                mInitialMotionY = initialMotionY;
                mInitialOffsetTop = mCurrentOffsetTop;
                mDispatchTargetTouchDown = false;
                mDragPercent = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }
                final float y = getMotionEventY(ev, mActivePointerId);
                if (y == -1) {
                    return false;
                }
                final float yDiff = y - mInitialMotionY;
                if (mRefreshing) {
                    mIsBeingDragged = !(yDiff < 0 && mCurrentOffsetTop <= 0);
                } else if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mIsBeingDragged = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (!mIsBeingDragged) {
            return super.onTouchEvent(ev);
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                final float y = ev.getY();
                final float yDiff = y - mInitialMotionY;
                int targetY;
                if (mRefreshing) {
                    targetY = (int) (mInitialOffsetTop + yDiff);
                    if (canChildScrollUp()) {
                        targetY = -1;
                        mInitialMotionY = y;
                        mInitialOffsetTop = 0;
                        if (mDispatchTargetTouchDown) {
                            mTarget.dispatchTouchEvent(ev);
                        } else {
                            MotionEvent obtain = MotionEvent.obtain(ev);
                            obtain.setAction(MotionEvent.ACTION_DOWN);
                            mDispatchTargetTouchDown = true;
                            mTarget.dispatchTouchEvent(obtain);
                        }
                    } else {
                        if (targetY < 0) {
                            if (mDispatchTargetTouchDown) {
                                mTarget.dispatchTouchEvent(ev);
                            } else {
                                MotionEvent obtain = MotionEvent.obtain(ev);
                                obtain.setAction(MotionEvent.ACTION_DOWN);
                                mDispatchTargetTouchDown = true;
                                mTarget.dispatchTouchEvent(obtain);
                            }
                            targetY = 0;
                        } else if (targetY > mTotalDragDistance) {
                            targetY = mTotalDragDistance;
                        } else {
                            if (mDispatchTargetTouchDown) {
                                MotionEvent obtain = MotionEvent.obtain(ev);
                                obtain.setAction(MotionEvent.ACTION_CANCEL);
                                mDispatchTargetTouchDown = false;
                                mTarget.dispatchTouchEvent(obtain);
                            }
                        }
                    }
                } else {
                    final float scrollTop = yDiff * DRAG_RATE;
                    float originalDragPercent = scrollTop / mTotalDragDistance;
                    if (originalDragPercent < 0) {
                        return false;
                    }
                    mDragPercent = Math.min(1f, Math.abs(originalDragPercent));
                    float extraOS = Math.abs(scrollTop) - mTotalDragDistance;
                    float slingshotDist = mSpinnerFinalOffset;
                    float tensionSlingshotPercent = Math.max(0,
                            Math.min(extraOS, slingshotDist * 2) / slingshotDist);
                    float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
                            (tensionSlingshotPercent / 4), 2)) * 2f;
                    float extraMove = (slingshotDist) * tensionPercent * 2;
                    targetY = (int) ((slingshotDist * mDragPercent) + extraMove);
                    if (mRefreshView.getVisibility() != View.VISIBLE) {
                        mRefreshView.setVisibility(View.VISIBLE);
                    }
                    if (scrollTop < mTotalDragDistance) {
                        mRefreshDrawable.onDrag(mDragPercent);

                    }
                }
                setTargetOffsetTop(targetY - mCurrentOffsetTop);
                break;
            }
            case MotionEventCompat.ACTION_POINTER_DOWN:
                final int index = MotionEventCompat.getActionIndex(ev);
                mActivePointerId = ev.findPointerIndex(index);
                break;
            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }
                if (mRefreshing) {
                    if (mDispatchTargetTouchDown) {
                        mTarget.dispatchTouchEvent(ev);
                        mDispatchTargetTouchDown = false;
                    }
                    return false;
                }
                final float y = ev.getY();
                final float overScrollTop = (y - mInitialMotionY) * DRAG_RATE;
                mIsBeingDragged = false;
                if (overScrollTop > mTotalDragDistance) {
                    setRefreshing(true, true);
                } else {
                    mRefreshing = false;
                    animateOffsetToStartPosition();
                }
                mActivePointerId = INVALID_POINTER;
                return false;
            }
        }

        return true;
    }

    public void setDurations(int durationToStartPosition, int durationToCorrectPosition) {
        mDurationToStartPosition = durationToStartPosition;
        mDurationToCorrectPosition = durationToCorrectPosition;
    }

    private void animateOffsetToStartPosition() {
        mFrom = mCurrentOffsetTop;
        mAnimateToStartPosition.reset();
        mAnimateToStartPosition.setDuration(mDurationToStartPosition);
        mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
        mAnimateToStartPosition.setAnimationListener(mToStartListener);
        mRefreshView.clearAnimation();
        mRefreshView.startAnimation(mAnimateToStartPosition);
    }

    private void animateOffsetToCorrectPosition() {
        mFrom = mCurrentOffsetTop;
        mAnimateToCorrectPosition.reset();
        mAnimateToCorrectPosition.setDuration(mDurationToCorrectPosition);
        mAnimateToCorrectPosition.setInterpolator(mDecelerateInterpolator);
        mAnimateToCorrectPosition.setAnimationListener(mRefreshListener);
        mRefreshView.clearAnimation();
        mRefreshView.startAnimation(mAnimateToCorrectPosition);
    }

    private final Animation mAnimateToStartPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            moveToStart(interpolatedTime);
        }
    };

    private final Animation mAnimateToCorrectPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int endTarget = mSpinnerFinalOffset;
            int targetTop = (mFrom + (int) ((endTarget - mFrom) * interpolatedTime));
            int offset = targetTop - mTarget.getTop();
            setTargetOffsetTop(offset  /* requires update */);
        }
    };

    private void moveToStart(float interpolatedTime) {
        int targetTop = mFrom - (int) (mFrom * interpolatedTime);
        int offset = targetTop - mTarget.getTop();
        setTargetOffsetTop(offset);
        mRefreshDrawable.onDrag(mDragPercent * (1 - interpolatedTime));
    }

    public void setRefreshing(boolean refreshing) {
        if (mRefreshing != refreshing) {
            setRefreshing(refreshing, false /* notify */);
        }
    }

    private void setRefreshing(boolean refreshing, final boolean notify) {
        if (mRefreshing != refreshing) {
            mNotify = notify;
            ensureTarget();
            mRefreshing = refreshing;
            if (mRefreshing) {
                mRefreshDrawable.onDrag(1f);
                animateOffsetToCorrectPosition();
            } else {
                animateOffsetToStartPosition();
            }
        }
    }

    private Animation.AnimationListener mRefreshListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
            mRefreshView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            if (mRefreshing) {
                mRefreshDrawable.start();
                if (mNotify) {
                    if (mListener != null) {
                        mListener.onRefresh();
                    }
                }
            } else {
                mRefreshDrawable.stop();
                mRefreshView.setVisibility(View.GONE);
                animateOffsetToStartPosition();
            }
            mCurrentOffsetTop = mTarget.getTop();
        }
    };

    private Animation.AnimationListener mToStartListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
            mRefreshDrawable.stop();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            mRefreshView.setVisibility(View.GONE);
            mCurrentOffsetTop = mTarget.getTop();
        }
    };

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private float getMotionEventY(MotionEvent ev, int activePointerId) {
        final int index = ev.getPointerId(activePointerId);
        if (index < 0) {
            return -1;
        }
        return ev.getY();
    }

    private void setTargetOffsetTop(int offset) {
        mTarget.offsetTopAndBottom(offset);
        mCurrentOffsetTop = mTarget.getTop();
        mRefreshDrawable.onOffsetTopAndBottom(offset);
    }

    private boolean canChildScrollUp() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (mTarget instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTarget;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return mTarget.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTarget, -1);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        ensureTarget();
        if (mTarget == null)
            return;

        int height = getMeasuredHeight();
        int width = getMeasuredWidth();
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = getPaddingRight();
        int bottom = getPaddingBottom();

        mTarget.layout(left, top + mTarget.getTop(), left + width - right, top + height - bottom + mTarget.getTop());
        mRefreshView.layout(left, top, left + width - right, top + height - bottom);
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getContext().getResources().getDisplayMetrics());
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        mListener = listener;
    }

    public interface OnRefreshListener {
        void onRefresh();
    }
}
