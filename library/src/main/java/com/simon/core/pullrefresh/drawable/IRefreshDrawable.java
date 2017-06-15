package com.simon.core.pullrefresh.drawable;

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

/**
 * interface of RefreshDrawable
 * Created by simon on 17-6-12.
 */

public interface IRefreshDrawable extends Animatable, Drawable.Callback {

    int getFinalDragOffset();

    void onDrag(float dragPercent);

    void onOffsetTopAndBottom(int offset);

    void setColorSchemeColors(int[] colorSchemeColors);

}