package com.pointrlabs.sample.activity.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.pointrlabs.core.configuration.CoreConfiguration;
import com.pointrlabs.core.dataaccess.models.graph.NodeInterface;
import com.pointrlabs.core.dataaccess.models.graph.PortalNode;
import com.pointrlabs.core.dataaccess.models.poi.Poi;
import com.pointrlabs.core.management.ConfigurationManager;
import com.pointrlabs.core.management.GeofenceManager;
import com.pointrlabs.core.management.PathManager;
import com.pointrlabs.core.management.PoiManager;
import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.management.PointrBase;
import com.pointrlabs.core.management.PositionManager;
import com.pointrlabs.core.management.models.ErrorMessage;
import com.pointrlabs.core.management.models.Facility;
import com.pointrlabs.core.management.models.WarningMessage;
import com.pointrlabs.core.map.ARController;
import com.pointrlabs.core.map.fragment.ARFragment;
import com.pointrlabs.core.map.fragment.RouteScreenFragment;
import com.pointrlabs.core.map.interfaces.MapView;
import com.pointrlabs.core.map.interfaces.MapViewProvider;
import com.pointrlabs.core.map.interfaces.OnFragmentDisplayStateChangedListener;
import com.pointrlabs.core.map.model.ContainerFragmentState;
import com.pointrlabs.core.map.model.MapMode;
import com.pointrlabs.core.map.ui.DestinationMarkerView;
import com.pointrlabs.core.map.ui.DrawablePointView;
import com.pointrlabs.core.map.ui.MapDrawable;
import com.pointrlabs.core.map.ui.POIView;
import com.pointrlabs.core.map.ui.PinView;
import com.pointrlabs.core.map.ui.PointrMapView;
import com.pointrlabs.core.map.ui.SimpleDrawable;
import com.pointrlabs.core.map.ui.SimpleLineDrawable;
import com.pointrlabs.core.nativecore.wrappers.Plog;
import com.pointrlabs.core.pathfinding.Path;
import com.pointrlabs.core.pathfinding.directions.PathDirection;
import com.pointrlabs.core.poi.models.PoiContainer;
import com.pointrlabs.core.positioning.model.CalculatedLocation;
import com.pointrlabs.core.positioning.model.GeoPosition;
import com.pointrlabs.core.positioning.model.Location;
import com.pointrlabs.core.positioning.model.Position;
import com.pointrlabs.core.positioning.model.PositioningTypes;
import com.pointrlabs.sample.R;
import com.pointrlabs.sample.activity.BasePointrMapActivity;
import com.pointrlabs.sample.activity.utils.PointRMgr;
import com.pointrlabs.sample.fragment.BaseContainerFragment;
import com.qozix.tileview.geom.CoordinateTranslater;
import com.qozix.tileview.paths.BasicPathView;

import java.util.ArrayList;
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
    private PointrMapView mapView;
    private PointrMapViewProviderListener pointrMapViewProviderListener;
    private PointrLocationListener pointrLocationListener;
    private PinView userPinView;
    private SimpleDrawable userPinDrawable;
    private Integer pinViewIconSize;
    private DestinationMarkerView destinationMarkerView;
    public static final String poiDrawablesKey = "poiDrawablesKey";
    public static final String destinationMarkerKey = "destinationMarkerKey";
    public static final String directionDrawablesKey = "directionDrawablesKey";
    public static final String pathDrawableKey = "pathDrawableKey";
    private AtomicBoolean isPositionCalculatedBefore = new AtomicBoolean(false);
    private Position currentPosition;
    private Position positionInOtherFacility;
    protected AtomicBoolean isPinOnScreen = new AtomicBoolean(false);
    private final Object pinViewCreationLock = new Object();






    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_test_map);
        initView();
        PointRMgr.getInstance().initPointR(new PointRInitListener());
        findViewById(R.id.btn_test_nav).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PointRMgr.getInstance().getPathAndDrawNavLineOnMap(TestPointrMapActivity.this, new PointRMgr.PathCalculateListener() {
                    @Override
                    public void onSuccess(Path path) {

//                        PoiManager poiManager = PointRMgr.getInstance().getPointr().getPoiManager();
//
//                        runOnUiThread(() -> {
//                            containerFragment = getContainerFragment();
//                            PoiContainer selectedPoi = poiManager.getSelectedPoi();
//                            if (selectedPoi != null) {
//                                containerFragment.startPathfinding(selectedPoi);
//                                containerFragment.transitStateTo(ContainerFragmentState.PathfindingHeaderAndFooter);
//                                containerFragment.getMap().getMapModeCoordinator().setMapMode(MapMode.PathTracking);
//                            }
//                        });
                    }

                    @Override
                    public void onFail(String msg) {
                        Toast.makeText(TestPointrMapActivity.this,msg,Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /**
     * pointr initListener
     */
    private class PointRInitListener implements PointRMgr.InitPointRListener{

        @Override
        public void onBefore() {

        }

        @Override
        public void onAfter() {
            initPointrObj();
        }

        @Override
        public void onComplete() {
            initPointrObj();
        }

        @Override
        public void onStartFailure(List<ErrorMessage> list) {

        }

        @Override
        public void onPointrStateUpdate(PointrBase.State state, List<WarningMessage> list) {

        }
    }

    private void initView(){
        mapView = findViewById(R.id.view_map);
        currentPosition = new Position();
        positionInOtherFacility = new Position();

    }

    /**
     * 本函数应当在pointr完全启动之后再进行
     * 情况一：
     *      pointr 初次启动那么应该在它启动完成之后进行 PositionManager 的监听绑定
     * 情况二：
     *      pointr 本身已经启动，那么就在它启动完成之后的回调进行监听器的绑定
     */
    private void initPointrObj(){
        pointrMapViewProviderListener = new PointrMapViewProviderListener();
        mapView.setMapViewProvider(pointrMapViewProviderListener);
        PositionManager positionManager = PointRMgr.getInstance().getPointr().getPositionManager();
        pointrLocationListener = new PointrLocationListener();
        if (positionManager != null) {
            positionManager.addListener(pointrLocationListener);
        }
    }

    /**
     * pointr poi 图标与导航线等绘制监听器
     * viewForDrawable ：绘制poi等标
     *                  poi数据从网络获取，可从在配置文件 assets/BaseConfiguration.json 查看链接
     * lineViewForDrawable 绘制导航线条
     * 本sdk 绘制导航线条的流程为：
     * 初始化pointr --->开启导航路径计算---> lineViewForDrawable 更新路径绘制信息
     *
     */
    private class PointrMapViewProviderListener implements MapViewProvider{

        @Override
        public View viewForDrawable(MapDrawable drawable) {
            if (drawable.getIdentifier() != null && drawable.getIdentifier().equalsIgnoreCase(MapView.userPinDrawableKey)) {
                // drawable is userPin create a user pinview
                userPinView = createNewPinView();
                userPinView.resetVariables();
                userPinView.setDrawable(drawable);
                userPinView.setShouldShowPinHeading(shouldShowPinHeading());
                return userPinView;

            } else if (drawable.getIdentifier() != null && drawable.getIdentifier().contains(destinationMarkerKey)) {
                DestinationMarkerView destinationMarker = getDestinationView();
                destinationMarker.setDrawable(drawable);
                destinationMarker.setAnchorPoints(-0.5f, -1f);
                //            destinationMarkerDrawable.setSize(100);  size can be changed here.
                return destinationMarker;

            } else if ((drawable instanceof SimpleDrawable) && drawable.getIdentifier() != null &&
                    drawable.getIdentifier().contains(directionDrawablesKey)) {
                // do nothing for now
            } else if (drawable instanceof Poi) {
                Poi poi = (Poi)drawable;
                POIView poiView = new POIView(TestPointrMapActivity.this);
                poiView.setLabelText(poi.getName());
                poiView.setDrawable(poi);
                int poiIconId = getImageResourceIdForPoi(poi);
                if (poiIconId == 0) {
                    poiIconId = poi.getDefaultPoiImage();
                    if (poiIconId == poi.getGenericIconId()) {
                        // Means there is no default icon for this poi, and the generic icon will be used
                    }
                }
                poiView.setIconViewImage(poiIconId);
                poiView.setIconDimensions(80, 80);  // icon size can be altered here
                poiView.setAnchorPoints(-0.5f, -0.5f);
                return poiView;
            }
            return null;
        }

        @Override
        public BasicPathView.CustomDrawablePath lineViewForDrawable(MapDrawable drawable) {
            // create drawable path here.
            BasicPathView.CustomDrawablePath result = new BasicPathView.CustomDrawablePath();
            // create paint object
            Paint linePaint = new Paint();
            int color = TestPointrMapActivity.this.getResources().getColor(com.pointrlabs.core.R.color.dark_blue);
            linePaint.setColor(color);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            linePaint.setStrokeWidth(10);
            linePaint.setStyle(Paint.Style.STROKE);
            PathEffect pathEffect = new PathEffect();  // new DashPathEffect(new float[]{2.0f, 2.0f}, 0); //Dashed Line.
            linePaint.setPathEffect(pathEffect);
            result.paint = linePaint;
            // create Path
            List<float[]> pathObject = null;
            if (drawable instanceof Path) {
                pathObject = calculatePathArrayForLevel(drawable, mapView.getCurrentLevel());

            } else if (drawable instanceof SimpleLineDrawable) {
                float[] pathArray = calculatePathArray(((SimpleLineDrawable)drawable).getPointsArray());
                pathObject = new ArrayList<>();
                pathObject.add(pathArray);
            }

            if (pathObject == null) {
                Plog.v("Cannot create android Path object from the Path nodes. Cannot provide view.");
                return null;
            }

            result.path = pathObject;

            result.pathMode = BasicPathView.PathMode.Lined;  // or Dotted, Custom.

            return result;
        }
    }


    /**
     * pointr 定位监听器
     * onLocationCalculated ：定位成功回调
     * onLevelChanged ：楼层切换回调
     * onGeolocationCalculated：gps 定位成功回调
     * onStateChanged ：定位状态变化回调
     * onPositionIsFading ：pointr定位信号变弱
     * onPositionIsLost ：定位丢失
     */
    private class PointrLocationListener implements PositionManager.Listener{

        @Override
        public void onLocationCalculated(CalculatedLocation calculatedLocation) {
            updateMapViewBasedOnCalculatedLocation(calculatedLocation);
            System.out.println("291---------:position success：");
        }

        @Override
        public void onLevelChanged(int level) {
            boolean isCorrectFacility = Pointr.getPointr().getConfigurationManager().isConfiguredToPhysicalFacility();
            if (!isCorrectFacility) {
                // Ignore data from another facility
                Plog.i("Won't change level, user is viewing a different facility.");
                return;
            }
            mapView.setCurrentLevel(level);
            zoomToCurrentPosition();
        }

        @Override
        public void onGeolocationCalculated(GeoPosition geoPosition) {

        }

        @Override
        public void onStateChanged(EnumSet<PositionManager.State> enumSet) {

        }

        @Override
        public void onPositionIsFading() {

        }

        @Override
        public void onPositionIsLost() {

        }
    }


    /**
     * 在地图更新当前定位小图标
     *
     * @param calculatedLocation
     */
    private void updateMapViewBasedOnCalculatedLocation(CalculatedLocation calculatedLocation) {
        if (calculatedLocation == null) {
           runOnUiThread(() -> hideUserPin());
            mapView.setCurrentLocation(null);
            isPositionCalculatedBefore.set(false);
            return;
        }
        Position userPosition = calculatedLocation.convertToPosition();
        currentPosition = userPosition;
        mapView.setCurrentLocation(calculatedLocation);
        if (userPosition == null) {
            runOnUiThread(() -> hideUserPin());
            isPositionCalculatedBefore.set(false);
            return;
        }
        if (userPosition.isValidNormalisedPosition() &&
                (!physicalAndMapViewFacilitiesAreSame() ||
                        calculatedLocation.getState() == CalculatedLocation.LocationState.LOST)) {
            // Hide position
            positionInOtherFacility = userPosition;
            currentPosition = null;
            Plog.v("Position is from different facility or is lost - will hide position.");
            if (mapView.getMapModeCoordinator().getMapMode() != MapMode.Free) {
                mapView.getMapModeCoordinator().setMapMode(MapMode.Free);
            }
            runOnUiThread(() -> hideUserPin());
        } else  // look if this else causes anything.
            if (userPosition.isValidNormalisedPosition()) {
                // position is in physical facility
                positionInOtherFacility = new Position();
                updateUserPin(calculatedLocation);
                mapView.showMapIfNotShownYet();
            } else {
                runOnUiThread(() -> hideUserPin());
            }
        showPositionOnTheMapIfFirstOne(currentPosition);
    }

    /**
     * 隐藏当前定位图标
     */
    private void hideUserPin() {
        if (userPinView != null) {
            userPinView.disappearFromView();
        }

        mapView.removeDrawable(userPinDrawable);

        userPinDrawable = null;
        userPinView = null;
    }

    private void showPositionOnTheMapIfFirstOne(Position position) {
        if (position != null && position.isValidNormalisedPosition()) {
            if (isPositionCalculatedBefore.get()) {
                return;
            }
            isPositionCalculatedBefore.set(true);
            if (position.getLevel() != mapView.getCurrentLevel()) {
                mapView.setCurrentLevel(position.getLevel());
            }
            mapView.zoomToCurrentPosition(position);
        } else {
            // invalid position from position manager
            isPositionCalculatedBefore.set(false);
            Plog.v("Invalid position to set.");
        }
    }

    private void updateUserPin(CalculatedLocation calculatedLocation) {
        // if the level is correct show drawable
        // else hide it
        Position userPosition = calculatedLocation.convertToPosition();
        boolean isPositionLevelSameWithMapLevel = mapView.getCurrentLevel() == currentPosition.getLevel();
        boolean isPositionInsideTheScreenBoundary = mapView.isPointVisibleInMap(userPosition);
        if (isPositionInsideTheScreenBoundary != isPinOnScreen.get() ||
                (!isPinOnScreen.get() && !isPositionCalculatedBefore.get())) {
            onPinEnterOrExit(isPositionInsideTheScreenBoundary);
            isPinOnScreen.set(isPositionInsideTheScreenBoundary);
        }

        if (userPinDrawable != null) {
            userPinDrawable.setX(userPosition.getX());
            userPinDrawable.setY(userPosition.getY());
        }

        if (isPositionLevelSameWithMapLevel && isPositionInsideTheScreenBoundary) {
            if (!mapView.drawableExists(userPinDrawable)) {
                createUserPinDrawable();
            }

            mapView.getActivity().runOnUiThread(() -> {
                mapView.rotateCanvasWithUserPinView(userPosition);
                adjustUserPinView(calculatedLocation);
            });

        } else {  // if(!isPositionLevelSameWithMapLevel){
            runOnUiThread(() -> hideUserPin());
        }
        mapView.refreshDrawable(userPinDrawable);
    }

    public void onPinEnterOrExit(boolean isInScreen) {

        if (!isInScreen) {
            mapView.getMapModeCoordinator().setMapMode(MapMode.Free);
        } else {
            PathManager pathManager = PointRMgr.getInstance().getPointr().getPathManager();
            if (pathManager == null || !pathManager.isPathfindingStarted()) {
                // There is no active path, and pin is on screen so switch to tracking
                mapView.getMapModeCoordinator().setMapMode(MapMode.Tracking);
            } else {
                mapView.getMapModeCoordinator().setMapMode(
                        MapMode.PathTracking);  // Any preferred map mode for --pathfinding-- can be picked from here
            }
        }
    }

    private void adjustUserPinView(CalculatedLocation calculatedLocation) {
        Position userPosition = calculatedLocation.convertToPosition();

        if (userPinDrawable == null) {
            Plog.v("user pin drawable is null");
            return;
        }

        if (userPinView == null) {
            Plog.v("user pin view is null but drawable is not, refresh views");
            mapView.refreshDrawable(userPinDrawable);
            return;
        }
        // if there is pin rotate it
        userPinView.setOrientationAccuracy(userPosition.getOrientationAccuracy());
        rotatePinWithRotation(userPosition);

        if (calculatedLocation.getState() == CalculatedLocation.LocationState.ACTIVE) {
            userPinView.updatePinViewWithAccuracy(userPosition);
        } else if (calculatedLocation.getState() == CalculatedLocation.LocationState.FADED && userPinView.isActive()) {
            userPinView.fadeTo(0.5F);
        }
    }

    /**
     * 调整地图显示中心
     */
    private void zoomToCurrentPosition() {
        PositionManager posManager = PointRMgr.getInstance().getPointr().getPositionManager();
        Position userPosition;
        if (posManager != null) {
            CalculatedLocation calculatedLocation = posManager.getLastCalculatedLocation();
            userPosition = calculatedLocation.convertToPosition();
            mapView.zoomToCurrentPosition(userPosition);
        }
    }

    /**
     * 根据pointr 提供的position 旋转当前定位图标
     * @param position
     */
    private void rotatePinWithRotation(Position position) {
        if (position.getOrientation() == PositioningTypes.INVALID_ORIENTATION || userPinDrawable == null) {
            return;
        }
        // if user pin drawable exist and has a valid rotation rotate and refresh
        userPinView.rotate(position);
    }

    /**
     * 创建用于当前定位的图标
     */
    private void createUserPinDrawable() {
        boolean isValidPosition = currentPosition.isValidNormalisedPosition();
        boolean isCurrentLevel = mapView.getCurrentLevel() == currentPosition.getLevel();
        synchronized (pinViewCreationLock) {
            if (isValidPosition && isCurrentLevel) {
                Plog.v("create user pin");
                userPinDrawable = new SimpleDrawable();
                // use given identifier in mapView for userpin, else some error can occur when rotating
                userPinDrawable.setIdentifier(MapView.userPinDrawableKey);
                userPinDrawable.setInteractive(false);
                mapView.addDrawable(userPinDrawable);
            }
        }
    }

    /**
     * 判断 pointr 与当着地图显示的设施是否一致
     * 用来控制当前定位小图标是否显示的条件
     * @return
     */
    private boolean physicalAndMapViewFacilitiesAreSame() {
        GeofenceManager geofenceManager = PointRMgr.getInstance().getPointr().getGeofenceManager();
        if (geofenceManager != null && geofenceManager.getCurrentFacility() != null) {
            return geofenceManager.getCurrentFacility().equals(mapView.getCurrentFacility());
        }
        return false;
    }


    /**
     * 根据楼层信息计算路径结点信息
     * @param drawable
     * @param currentLevel
     * @return
     */
    private List<float[]> calculatePathArrayForLevel(MapDrawable drawable, int currentLevel) {
        if (drawable == null || !(drawable instanceof Path)) {
            Plog.v("MapDrawable is null or not an instance of path");
            return null;
        }

        List<float[]> pathsToDrawResult = new ArrayList<>();

        Path path = (Path)drawable;

        // if we are going to take the facility of the current configuration
        final Facility visibleFacility = mapView.getCurrentFacility();

        final List<NodeInterface> nodes = path.getNodes();

        boolean isNearPortal = false;
        if (path.getDirections().size() > 0) {
            PathDirection firstDirection = path.getDirections().get(0);
            if (firstDirection.getType() == PathDirection.PathDirectionType.CHANGE_FACILITY ||
                    firstDirection.getType() == PathDirection.PathDirectionType.CHANGE_LEVEL) {
                isNearPortal = true;
            }
        }

        List<NodeInterface> nodesOnCurrentLevel = new ArrayList<>();

        List<List<NodeInterface>> pathsToDraw = new ArrayList<>();
        NodeInterface previousNode = nodes.get(0);

        Plog.v("Will draw " + nodes.size() + " nodes");

        for (int i = 0; i < nodes.size(); i++) {
            NodeInterface node = nodes.get(i);

            Integer nodeLevel = node.getLocation().getLevel();

            boolean beforeChangingFacility =
                    previousNode.getLocation().getFacilityId() == visibleFacility.getFacilityId() &&
                            node.getLocation().getFacilityId() != visibleFacility.getFacilityId();
            boolean afterChangingFacility =
                    previousNode.getLocation().getFacilityId() != visibleFacility.getFacilityId() &&
                            node.getLocation().getFacilityId() == visibleFacility.getFacilityId();
            boolean beforeChangingLevel =
                    previousNode.getLocation().getLevel() == currentLevel && nodeLevel != currentLevel;
            boolean afterChangingLevel =
                    previousNode.getLocation().getLevel() != currentLevel && nodeLevel == currentLevel;

            if (afterChangingFacility || afterChangingLevel) {
                // Current node is after the portal, no need to remove the remaining path from screen.
                isNearPortal = false;
            }
            if (isNearPortal) {
                continue;
            }
            if ((previousNode instanceof PortalNode) && (node instanceof PortalNode) &&
                    (previousNode.getLocation().getLevel() == nodeLevel)) {
                pathsToDraw.add(nodesOnCurrentLevel);
                nodesOnCurrentLevel = new ArrayList<>();
                nodesOnCurrentLevel.add(node);
                previousNode = node;
                continue;
            }

            previousNode = node;

            if (node.getLocation().getFacilityId() == visibleFacility.getFacilityId() &&
                    nodeLevel.equals(currentLevel)) {
                nodesOnCurrentLevel.add(node);
            }

            if (beforeChangingFacility) {
                pathsToDraw.add(nodesOnCurrentLevel);
                nodesOnCurrentLevel = new ArrayList<>();
            } else if (afterChangingFacility) {
                nodesOnCurrentLevel = new ArrayList<>();
                nodesOnCurrentLevel.add(node);
            }
            if (!beforeChangingFacility && beforeChangingLevel) {
                pathsToDraw.add(nodesOnCurrentLevel);
                nodesOnCurrentLevel = new ArrayList<>();
            } else if (!afterChangingFacility && afterChangingLevel) {
                nodesOnCurrentLevel = new ArrayList<>();
                nodesOnCurrentLevel.add(node);
            }
        }

        if (!pathsToDraw.contains(nodesOnCurrentLevel) && nodesOnCurrentLevel.size() != 0) {
            pathsToDraw.add(nodesOnCurrentLevel);
        }

        for (List<NodeInterface> nodesToDraw : pathsToDraw) {  // Because some levels have non-continuous paths.
            List<Location> locationArray = getLocationArrayFromNodes(nodesToDraw);
            float[] pathArray = calculatePathArray(locationArray);
            pathsToDrawResult.add(pathArray);
        }

        return pathsToDrawResult;
    }

    /**
     * 组装路径结点信息为链表形式
     * @param pointsArray
     * @return
     */
    private float[] calculatePathArray(List<Location> pointsArray) {
        if (pointsArray == null || pointsArray.isEmpty()) {
            Plog.v("No nodes to draw.");
            return null;
        }
        float[] pathArray = new float[(pointsArray.size() - 1) * 4];
        CoordinateTranslater ctr = mapView.getCoordinateTranslater();
        for (int i = 0; i < pointsArray.size() - 1; i++) {
            int index = i * 4;
            Location from = pointsArray.get(i);
            pathArray[index] = ctr.translateX(from.getX());
            pathArray[index + 1] = ctr.translateY(from.getY());

            Location to = pointsArray.get(i + 1);
            pathArray[index + 2] = ctr.translateX(to.getX());
            pathArray[index + 3] = ctr.translateY(to.getY());
        }

        return pathArray;
    }

    private List<Location> getLocationArrayFromNodes(List<NodeInterface> nodesList) {
        List<Location> locations = new ArrayList<>();
        for (NodeInterface node : nodesList) {
            locations.add(node.getLocation());
        }
        return locations;
    }

    public <T extends DrawablePointView> T getDestinationView() {
        return (T)destinationMarkerView;
    }

    public PinView createNewPinView() {
        PinView pinView = new PinView(this);
        pinView.setMap(mapView);
        if (pinViewIconSize != null) {
            pinView.setSize(pinViewIconSize);
        }
        return pinView;
    }
    public PinView getPinView() {
        return userPinView;
    }

    /**
     * 获取poi 图片资源
     * @param poi
     * @return
     */
    private int getImageResourceIdForPoi(Poi poi) {
        // A default return value can be set, to use the pointr default image icons in wanted cases
        // Wanted cases can be overriden like this, if the case is not overriden, it will return 0 and pointr
        // default icon will be used
        if (poi.getName().contains("toilets")) {
            return com.pointrlabs.core.R.drawable.toilets;
        }
        return 0;
    }

    private boolean shouldShowPinHeading() {
        ConfigurationManager configurationManager = PointRMgr.getInstance().getPointr().getConfigurationManager();
        if (configurationManager != null) {
            CoreConfiguration configuration = configurationManager.getCurrentConfiguration();
            if (configuration != null) {
                return configuration.getUserInterfaceConfiguration().getShouldShowPinHeading();
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PointRMgr.getInstance().onDestory();
    }
}
