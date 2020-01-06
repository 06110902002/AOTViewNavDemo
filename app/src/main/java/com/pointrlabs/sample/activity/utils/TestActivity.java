package com.pointrlabs.sample.activity.utils;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.pointrlabs.core.management.PathManager;
import com.pointrlabs.core.management.PoiManager;
import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.management.PointrBase;
import com.pointrlabs.core.management.interfaces.PointrListener;
import com.pointrlabs.core.management.models.ErrorMessage;
import com.pointrlabs.core.management.models.WarningMessage;
import com.pointrlabs.core.map.model.ContainerFragmentState;
import com.pointrlabs.core.map.model.MapMode;
import com.pointrlabs.core.nativecore.wrappers.Plog;
import com.pointrlabs.core.pathfinding.Path;
import com.pointrlabs.core.poi.models.PoiContainer;
import com.pointrlabs.sample.R;
import com.pointrlabs.sample.fragment.BaseContainerFragment;
import com.sensetime.armap.constant.PointrConfig;
import com.sensetime.armap.utils.ARPathUtils;
import com.sensetime.armap.utils.MobileInfoUtils;

import java.util.List;

/**
 * Create By 刘铁柱
 * Create Date 2019-11-26
 * Sensetime@Copyright
 * Des:
 */
public class TestActivity extends AppCompatActivity {

    private PointrStartListener pointrStartListener;
    private final int REQUEST_PERMISSION_CODE = 0x0021;
    private static final int REQUEST_CODE_ASK_LOCATION_PERMISSION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE

    };
    private MiniMapFragment containerFragment;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        startPointr();
        findViewById(R.id.btn_cal_path).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawNavPathLineOnPointr();
            }
        });
        findViewById(R.id.btn_level).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLevel(2);
            }
        });
        findViewById(R.id.btn_abort).setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                getContainerFragment().abortPathfinding();
            }
        });
    }

    private void startPointr(){
        if(Pointr.getPointr() == null) {
            Toast.makeText(this,"pointr异常程序中断",Toast.LENGTH_SHORT).show();
            return;
        }
        pointrStartListener = new PointrStartListener();
        Pointr.getPointr().addListener(pointrStartListener);
        if (Pointr.getPointr().getState() == null || Pointr.getPointr().getState() == PointrBase.State.OFF) {
            Plog.v("Pointr is OFF, let's start it");
            tryStartEngine();
        } else {
            Plog.v("Pointr is already ON, show map");
            showPointrMapView();
        }
    }


    /**
     * 启动pointr 如果没有权限则先申请
     */
    private void tryStartEngine() {
        if(!hasPermission(permissions)){
            requestPermission(permissions);
            return;
        }
        Pointr.getPointr().start();
    }

    public boolean hasPermission(String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    protected void requestPermission(String... permissions) {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean isGranted = true;
        if(requestCode == REQUEST_PERMISSION_CODE){
            for (int i = 0; i < grantResults.length; i++){
                // 权限申请被拒绝
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED){
                    //权限拒绝
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        // 用户直接拒绝了（勾选不再显示）
                    } else {
                        // 用户此次选择了禁止权限
                    }
                    isGranted = false;
                    break;
                }
            }
            if(isGranted){
                onHoldPermissions();
            }
        }
    }

    private void onHoldPermissions(){
        tryStartEngine();
    }

    private void showPointrMapView(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BluetoothAdapter blueadapter=BluetoothAdapter.getDefaultAdapter();
                if(blueadapter == null) {
                    Toast.makeText(TestActivity.this,"设备不支持蓝牙",Toast.LENGTH_LONG).show();
                    return;
                }
                if(!blueadapter.enable()){
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }else{
                    replaceFragmentsWithMap();
                }
            }
        });

    }

    private void replaceFragmentsWithMap() {
        containerFragment = getContainerFragment();
        //containerFragment.setStateChangeListener(this);
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.grow, R.anim.grow)
                .replace(R.id.fragment_container, containerFragment, BaseContainerFragment.TAG)
                .commitAllowingStateLoss();
    }
    protected MiniMapFragment getContainerFragment() {
        return MiniMapFragment.getInstance();
    }



    /**
     * pointr 启动管理监听器
     * 注意回调均在子线程执行的
     */
    private class PointrStartListener implements PointrListener{

        @Override
        public void onStateUpdated(PointrBase.State state, List<WarningMessage> list) {
            if (state.equals(PointrBase.State.RUNNING)) {
                showPointrMapView();
            }
        }

        @Override
        public void onFailure(List<ErrorMessage> list) {

        }
    }


    private void drawNavPathLineOnPointr() {
        if (PointrConfig.selectPoi == null) return;
        PathManager pathManager = Pointr.getPointr().getPathManager();
        AsyncTask.execute(() -> {
            Pointr.getPointr().getPoiManager().setSelectedPoi(PointrConfig.selectPoi);
            Path calculatedPath = pathManager.calculatePath();
            ARPathUtils2.getInstance().buildPathJSONString(TestActivity.this, calculatedPath);
            if (calculatedPath == null) {
                containerFragment.abortPathfinding();
                return;
            }
            pathManager.abortPathFinding();
            PoiManager poiManager = Pointr.getPointr().getPoiManager();
            if (poiManager == null) {
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    PoiContainer selectedPoi = poiManager.getSelectedPoi();
                    if (selectedPoi != null) {
                        containerFragment.startPathfinding(selectedPoi);
                        containerFragment.transitStateTo(ContainerFragmentState.PathfindingHeaderAndFooter);
                        containerFragment.getMap().getMapModeCoordinator().setMapMode(
                                MapMode.PathTracking);  // Any preferred map mode for --pathfinding-- can be set from here
                    } else {
                        System.out.println("379---------:selected Poi is null");
                    }
                }
            });
        });
    }

    private void switchLevel(int level){
        //getContainerFragment().getMap().setCurrentLevel(level);
        FrameLayout miniMapLayout = findViewById(R.id.fragment_container);
        ViewGroup.LayoutParams layoutParams = miniMapLayout.getLayoutParams();
        layoutParams.height = MobileInfoUtils.dp2px(this, 200);
        layoutParams.width = MobileInfoUtils.dp2px(this, 200);
        miniMapLayout.setLayoutParams(layoutParams);
        getContainerFragment().getMap().postDelayed(new Runnable() {
            @Override
            public void run() {
                getContainerFragment().getMap().scrollToAndCenter(0.5,0.5);
            }
        },500);
//        getContainerFragment().getMap().post(()->getContainerFragment().getMap().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//                getContainerFragment().getMap().getViewTreeObserver().removeOnGlobalLayoutListener(this);
//                getContainerFragment().getMap().setScaleForFill();
//            }
//        }));

        //getContainerFragment().getMap().setScale(0.0f);

    }

}
