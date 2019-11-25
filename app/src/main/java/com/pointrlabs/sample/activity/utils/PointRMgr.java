package com.pointrlabs.sample.activity.utils;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.pointrlabs.core.management.PathManager;
import com.pointrlabs.core.management.PoiManager;
import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.management.PointrBase;
import com.pointrlabs.core.management.interfaces.PointrListener;
import com.pointrlabs.core.management.models.ErrorMessage;
import com.pointrlabs.core.management.models.WarningMessage;
import com.pointrlabs.core.map.fragment.RouteScreenFragment;
import com.pointrlabs.core.map.model.ContainerFragmentState;
import com.pointrlabs.core.map.model.MapMode;
import com.pointrlabs.core.nativecore.wrappers.Plog;
import com.pointrlabs.core.pathfinding.Path;
import com.pointrlabs.sample.R;

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
    private Path calculatedPath;

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

    public void getPathAndDrawNavLineOnMap(Activity context,PathCalculateListener pathCalculateListener){
        if(pointr == null){
            System.out.println("84---------:init pointr first please");
            return ;
        }
        PathManager pathManager = pointr.getPathManager();
        AsyncTask.execute(() -> {
            calculatedPath = pathManager.calculatePath();
            if (calculatedPath == null ) {
                // either path could not be calculated or the user selected another poi
                context.runOnUiThread(() -> {
                    if(pathCalculateListener != null){
                        pathCalculateListener.onFail(context.getString(R.string.route_failed_path_calculation));
                        abortPathfinding();
                    }
                });
            } else {
                pathManager.abortPathFinding();
                if(pathCalculateListener != null){
                    pathCalculateListener.onSuccess(calculatedPath);
                }
            }
        });

    }

    public void abortPathfinding(){
        PathManager pathManager = Pointr.getPointr().getPathManager();
        if (pathManager != null) {
            pathManager.abortPathFinding();
        }
        PoiManager poiManager = Pointr.getPointr().getPoiManager();
        if (poiManager != null) {
            poiManager.resetSelectedPoi();
        }
    }

    public void onDestory(){
        if(pointr != null){
            pointr.removeListener(getStartPointRListener());
            pointr = null;
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

    /**
     * 初始pointr 的接口
     */
    public interface InitPointRListener{

        void onBefore();
        void onAfter();
        void onComplete();
        void onStartFailure(List<ErrorMessage> list);
        void onPointrStateUpdate(PointrBase.State state, List<WarningMessage> list);

    }

    /**
     * 对外的路径计算接口与pointr 本身的路径计算回调接口无关
     * 注意：当路径计算有结果时会将结果回调出去，此时如果想在
     *      地图上做画线操作应该在onSuccess 回调接口中进行
     *
     *      当路径计算失败时，走onFail回调，同时放弃pointr
     *      内部路径计算
     */
    public interface PathCalculateListener{

        void onSuccess(Path path);
        void onFail(String msg);
    }
}
