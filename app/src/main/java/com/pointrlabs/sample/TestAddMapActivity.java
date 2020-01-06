package com.pointrlabs.sample;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.pointrlabs.core.map.interfaces.MapView;
import com.pointrlabs.core.map.ui.BasePointrMapView;

/**
 * Create By 刘铁柱
 * Create Date 2019-11-16
 * Sensetime@Copyright
 * Des:
 */
public class TestAddMapActivity extends Activity {

    private LinearLayout linearLayout;
    public static BasePointrMapView mapView;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_test_addmap);
        initView();
    }

    private void initView(){
        linearLayout = findViewById(R.id.root_layout_map);
        if(mapView != null){
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,500);
            linearLayout.addView(mapView);
        }
    }
}
