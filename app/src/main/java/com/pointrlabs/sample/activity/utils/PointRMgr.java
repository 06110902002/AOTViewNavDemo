package com.pointrlabs.sample.activity.utils;

import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.management.PointrBase;
import com.pointrlabs.core.management.interfaces.PointrListener;
import com.pointrlabs.core.management.models.ErrorMessage;
import com.pointrlabs.core.management.models.WarningMessage;
import com.pointrlabs.core.nativecore.wrappers.Plog;

import java.util.List;

/**
 * Create By 刘铁柱
 * Create Date 2019-11-22
 * Sensetime@Copyright
 * Des:
 */
public class PointRMgr {

    private static PointRMgr instance;
    private Pointr pointr;
    private StartPointRListener startPointRListener;
    private InitPointRListener initPointRListener;

    public static PointRMgr getInstance(){
        if(instance == null){
            synchronized (Pointr.class){
                if(instance == null){
                    instance  = new PointRMgr();
                }
            }
        }
        return instance;
    }
    private PointRMgr(){
    }

    private StartPointRListener getStartPointRListener(){
        if(startPointRListener == null){
            startPointRListener = new StartPointRListener();
        }
        return startPointRListener;
    }

    public Pointr getPointr() {
        return pointr;
    }

    /**
     * init pointr object
     */
    public void initPointR(InitPointRListener listener){
        initPointRListener = listener;
        pointr = Pointr.getPointr();
        pointr.addListener(getStartPointRListener());

        if (pointr.getState() == null || pointr.getState() == PointrBase.State.OFF) {
            Plog.v("51-------PointRMgr Pointr is OFF, let's start it");
            //tryStartEngine();
            if(initPointRListener != null){
                initPointRListener.onBefore();
            }
            pointr.start();
            if(initPointRListener != null){
                initPointRListener.onAfter();
            }
        } else {
            Plog.v("Pointr is already ON, show map");
//            setUpRouteScreen();
//            proceedToMap(true);
            if(initPointRListener != null){
                initPointRListener.onComplete();
            }
        }
    }

    public void onDestory(){
        if(pointr != null){
            pointr.removeListener(getStartPointRListener());
        }
    }


    /**
     * start pointr listener
     */
    private class StartPointRListener implements PointrListener{

        @Override
        public void onStateUpdated(PointrBase.State state, List<WarningMessage> list) {
            if(initPointRListener != null){
                initPointRListener.onPointrStateUpdate(state,list);
            }
        }

        @Override
        public void onFailure(List<ErrorMessage> list) {
            if(initPointRListener != null){
                initPointRListener.onStartFailure(list);
            }
        }
    }

    public interface InitPointRListener{

        void onBefore();
        void onAfter();
        void onComplete();
        void onStartFailure(List<ErrorMessage> list);
        void onPointrStateUpdate(PointrBase.State state, List<WarningMessage> list);

    }
}
