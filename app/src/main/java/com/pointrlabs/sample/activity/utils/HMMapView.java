package com.pointrlabs.sample.activity.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.pointrlabs.core.map.ui.PointrMapView;

/**
 * Create By 刘铁柱
 * Create Date 2019-11-29
 * Sensetime@Copyright
 * Des:
 */
public class HMMapView extends PointrMapView {
    public HMMapView(Context context) {
        super(context);
    }

    public HMMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HMMapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }
}
