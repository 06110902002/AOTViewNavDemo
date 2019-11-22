package com.pointrlabs.sample.activity.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.management.PointrBase;
import com.pointrlabs.core.management.PositionManager;
import com.pointrlabs.core.management.models.ErrorMessage;
import com.pointrlabs.core.management.models.WarningMessage;
import com.pointrlabs.core.map.ARController;
import com.pointrlabs.core.map.fragment.ARFragment;
import com.pointrlabs.core.map.fragment.RouteScreenFragment;
import com.pointrlabs.core.map.interfaces.OnFragmentDisplayStateChangedListener;
import com.pointrlabs.core.map.model.ContainerFragmentState;
import com.pointrlabs.sample.R;
import com.pointrlabs.sample.activity.BasePointrMapActivity;
import com.pointrlabs.sample.activity.utils.PointRMgr;
import com.pointrlabs.sample.fragment.BaseContainerFragment;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Create By 刘铁柱
 * Create Date 2019-11-22
 * Sensetime@Copyright
 * Des:
 */
public class TestPointrMapActivity extends AppCompatActivity {

    private ARController arController;
    private ARFragment arFragment;
    private boolean isContainerFragmentStateAvailableForAR = false;
    private ContainerFragmentState currentContainerFragmentState = ContainerFragmentState.Map;
    private AtomicBoolean isBleRequestSentBefore = new AtomicBoolean(false);
    private static final int REQUEST_CODE_ASK_LOCATION_PERMISSION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CODE_ASK_WHITELISTING_PERMISSION = 3;
    private BaseContainerFragment containerFragment;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        PointRMgr.getInstance().initPointR(new PointRInitListener());
    }
    private class PointRInitListener implements PointRMgr.InitPointRListener{

        @Override
        public void onBefore() {

        }

        @Override
        public void onAfter() {

        }

        @Override
        public void onComplete() {
            proceedToMap(true);
        }

        @Override
        public void onStartFailure(List<ErrorMessage> list) {

        }

        @Override
        public void onPointrStateUpdate(PointrBase.State state, List<WarningMessage> list) {

        }
    }

    /**
     * Proceeds to the map screen where we will use the pointr functionalities
     */
    protected void proceedToMap(boolean shouldAskForBluetoothIfNeeded) {
        runOnUiThread(() -> {
            if (shouldAskForBluetoothIfNeeded && !isBleRequestSentBefore.get()) {
                EnumSet<PositionManager.State> state = Pointr.getPointr().getPositionManager().getState(null);
                if (state.contains(PositionManager.State.BluetoothOff) ||
                        state.contains(PositionManager.State.BluetoothTurningOff)) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    return;
                }
            }
            //askForWhiteListingIfApplicable();
            replaceFragmentsWithMap();
            //configureMap();
            //initAR();
            //showToastMessage(getString(R.string.pointr_started_successfully));
        });
    }

    private void replaceFragmentsWithMap() {
        containerFragment = getContainerFragment();
        //containerFragment.setArNavgationListener(new BasePointrMapActivity.EnterARListener());
        containerFragment.setStateChangeListener(new OnFragmentDisplayStateChangedListener(){

            @Override
            public void onDisplayStateChanged(ContainerFragmentState containerFragmentState) {

            }
        });
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.grow, R.anim.grow)
                .replace(R.id.fragment_container, containerFragment, BaseContainerFragment.TAG)
                .commitAllowingStateLoss();
    }

    protected BaseContainerFragment getContainerFragment() {
        return BaseContainerFragment.newInstance();
    }
}
