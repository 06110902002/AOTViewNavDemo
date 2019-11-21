package com.pointrlabs.sample.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.pointrlabs.sample.R;
import com.sensetime.armap.activity.ARNavigationActivity;
import com.sensetime.armap.activity.TestMapActivity;
import com.sensetime.armap.utils.ARUtils;
import com.sensetime.armap.utils.MobileInfoUtils;

/**
 * Create By 刘铁柱
 * Create Date 2019-11-08
 * Sensetime@Copyright
 * Des:
 */
public class DemoTestActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_test);
        findViewById(R.id.aar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DemoTestActivity.this, TestMapActivity.class));
            }
        });

        findViewById(R.id.btn_test_dialog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

//                LocationDialog dialog = new LocationDialog(DemoTestActivity.this);
//                //dialog.startAutoDissmissCountDown(3000);
//                dialog.setTxtTips(getString(R.string.repostion));
//                dialog.setDialogStatus(LocationDialog.Type.ANGLE_EXTRA);
//                dialog.show();
//                dialog.updateWindowHeight(
//                        (int)(MobileInfoUtils.getWindowWidth(DemoTestActivity.this) * 0.9),
//                        MobileInfoUtils.dp2px(DemoTestActivity.this,600),
//                        0);

                ARUtils.switchPageNoParams(DemoTestActivity.this, ARNavigationActivity.class);

            }
        });
    }
}
