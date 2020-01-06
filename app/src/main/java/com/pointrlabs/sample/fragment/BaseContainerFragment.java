package com.pointrlabs.sample.fragment;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.pointrlabs.core.R;
import com.pointrlabs.core.configuration.CoreConfiguration;
import com.pointrlabs.core.dataaccess.datamanager.models.Point;
import com.pointrlabs.core.dataaccess.models.DataType;
import com.pointrlabs.core.dataaccess.models.graph.NodeInterface;
import com.pointrlabs.core.dataaccess.models.graph.PortalNode;
import com.pointrlabs.core.dataaccess.models.poi.Poi;
import com.pointrlabs.core.management.ConfigurationManager;
import com.pointrlabs.core.management.GeofenceManager;
import com.pointrlabs.core.management.MapManager;
import com.pointrlabs.core.management.PathManager;
import com.pointrlabs.core.management.PoiManager;
import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.management.PointrBase;
import com.pointrlabs.core.management.PositionManager;
import com.pointrlabs.core.management.interfaces.DataManager;
import com.pointrlabs.core.management.models.ErrorMessage;
import com.pointrlabs.core.management.models.Facility;
import com.pointrlabs.core.management.models.Venue;
import com.pointrlabs.core.map.interfaces.Hideable;
import com.pointrlabs.core.map.interfaces.MapControllerEvents;
import com.pointrlabs.core.map.interfaces.MapView;
import com.pointrlabs.core.map.interfaces.MapViewProvider;
import com.pointrlabs.core.map.interfaces.OnFragmentDisplayStateChangedListener;
import com.pointrlabs.core.map.model.ContainerFragmentState;
import com.pointrlabs.core.map.model.MapMode;
import com.pointrlabs.core.map.ui.BasePointrMapView;
import com.pointrlabs.core.map.ui.DestinationMarkerView;
import com.pointrlabs.core.map.ui.DrawablePointView;
import com.pointrlabs.core.map.ui.LevelScrollView;
import com.pointrlabs.core.map.ui.LocateMeButton;
import com.pointrlabs.core.map.ui.MapDrawable;
import com.pointrlabs.core.map.ui.MapModeChangerView;
import com.pointrlabs.core.map.ui.NavigationFooterView;
import com.pointrlabs.core.map.ui.POIView;
import com.pointrlabs.core.map.ui.PinView;
import com.pointrlabs.core.map.ui.PoiSearchView;
import com.pointrlabs.core.map.ui.PointrProgressSpinner;
import com.pointrlabs.core.map.ui.SimpleDrawable;
import com.pointrlabs.core.map.ui.SimpleLineDrawable;
import com.pointrlabs.core.map.ui.TurnByTurnHeaderView;
import com.pointrlabs.core.nativecore.wrappers.Plog;
import com.pointrlabs.core.pathfinding.Path;
import com.pointrlabs.core.pathfinding.directions.PathDirection;
import com.pointrlabs.core.pathfinding.models.PathManagementError;
import com.pointrlabs.core.poi.models.PoiContainer;
import com.pointrlabs.core.positioning.model.CalculatedLocation;
import com.pointrlabs.core.positioning.model.GeoPosition;
import com.pointrlabs.core.positioning.model.Location;
import com.pointrlabs.core.positioning.model.Position;
import com.pointrlabs.core.positioning.model.PositioningTypes;
import com.pointrlabs.core.utils.PointrHelper;
import com.pointrlabs.sample.activity.BasePointrMapActivity;
import com.qozix.tileview.geom.CoordinateTranslater;
import com.qozix.tileview.paths.BasicPathView;
import com.sensetime.armap.constant.PointrConfig;
import com.sensetime.armap.entity.ARPathEntity;
import com.sensetime.armap.utils.ARMapJumpUtil;
import com.sensetime.armap.utils.ARPathUtils;
import com.sensetime.armap.utils.ARUtils;
import com.sensetime.armap.utils.MobileInfoUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.pointrlabs.core.positioning.model.PositioningTypes.INVALID_FACILITY;

public class BaseContainerFragment extends Fragment implements MapControllerEvents,
        MapViewProvider,
        DataManager.Listener,
        PathManager.Listener,
        PoiManager.Listener,
        PositionManager.Listener,
        ConfigurationManager.Listener,
        MapManager.Listener,
        MapView.Listener,
        LocateMeButton.Listener,
        MapModeChangerView.Listener,
        LevelScrollView.Listener {
    public static final String TAG = BaseContainerFragment.class.getSimpleName();

    public static final String poiDrawablesKey = "poiDrawablesKey";
    public static final String destinationMarkerKey = "destinationMarkerKey";
    public static final String directionDrawablesKey = "directionDrawablesKey";
    public static final String pathDrawableKey = "pathDrawableKey";

    protected int poiBackground;
    protected ContainerFragmentState state = ContainerFragmentState.Search;
    protected ContainerFragmentState previousState = ContainerFragmentState.Map;
    protected OnFragmentDisplayStateChangedListener stateChangeListener;
    private Pointr pointr;

    private Integer pinViewIconSize;
    private PinView userPinView;
    private SimpleDrawable userPinDrawable;
    protected AtomicBoolean isPinOnScreen = new AtomicBoolean(false);
    private final Object pinViewCreationLock = new Object();

    private Map<String, List<MapDrawable>> drawablesInLevel;
    private Position currentPosition;
    private Position positionInOtherFacility;
    private AtomicBoolean isPositionCalculatedBefore = new AtomicBoolean(false);
    private float lastMapOrientation;

    private SimpleDrawable destinationMarkerDrawable;
    private Path path;

    MapManager mapManager;

    private static final int REQUEST_ENABLE_BT = 420;
    protected MapModeChangerView mapModeButton;
    protected LevelScrollView levelPicker;
    protected NavigationFooterView navigationFooter;
    protected TurnByTurnHeaderView turnByTurnHeader;
    protected PoiSearchView searchView;
    protected BasePointrMapView map;
    protected LocateMeButton locateMeButton;
    protected PointrProgressSpinner progressView;
    private DestinationMarkerView destinationMarkerView;
    private AtomicBoolean isMapShownBefore = new AtomicBoolean(false);

    private static BaseContainerFragment instance;

    public static BaseContainerFragment newInstance() {
        if (instance == null) {
            instance = new BaseContainerFragment();
        }
        return instance;
    }
    public BaseContainerFragment() {
    }

    public int getLayoutId() {
        return R.layout.fragment_pointr_map;
    }

    /**
     * Level picker component for navigating through the available levels
     *

     * @return Level picker UI component
     */
    public LevelScrollView getLevelPicker() {
        return levelPicker;
    }

    /**
     * Map mode switcher for changing between modes such as Free, Tracking etc.
     *
     * @return Map mode switcher UI component
     */
    public MapModeChangerView getMapModeSwitcher() {
        return mapModeButton;
    }

    public NavigationFooterView getNavigationFooter() {
        return navigationFooter;
    }

    /**
     * Area to be filled with navigation directions
     *
     * @return UI component for displaying directions
     */
    public TurnByTurnHeaderView getTurnByTurnHeader() {
        return turnByTurnHeader;
    }

    /**
     * A Search bar to filter and let user select the desired POI
     *
     * @return UI component for filtering and selecting POIs
     */
    public PoiSearchView getSearchView() {
        return searchView;
    }

    public <T extends BasePointrMapView> T getMap() {
        return (T)map;
    }

    public LocateMeButton getLocateMeButton() {
        return locateMeButton;
    }

    public <T extends DrawablePointView> T getDestinationView() {
        return (T)destinationMarkerView;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutId(), container, false);

        Pointr pointr = Pointr.getPointr();
        if (pointr.getState() == null || pointr.getState() != PointrBase.State.RUNNING) {
            Plog.e("Pointr SDK is not running yet, cannot initialize the map. Please start the Pointr SDK.");
            return null;
        }
        map = (BasePointrMapView)view.findViewById(R.id.view_map);
        progressView = (PointrProgressSpinner)view.findViewById(R.id.progress_update);
        mapModeButton = (MapModeChangerView)view.findViewById(R.id.fab_change_map_mode);
        levelPicker = (LevelScrollView)view.findViewById(R.id.level_picker);
        navigationFooter = (NavigationFooterView)view.findViewById(R.id.navigation_footer);
        turnByTurnHeader = (TurnByTurnHeaderView)view.findViewById(R.id.turn_by_turn_header);
        searchView = (PoiSearchView)view.findViewById(R.id.view_search);
        locateMeButton = (LocateMeButton)view.findViewById(R.id.fab_locate_me);

        destinationMarkerView = new DestinationMarkerView(getContext());
        testARSDk(view);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pointr = Pointr.getPointr();

        if (pointr.getState() == null || pointr.getState() != PointrBase.State.RUNNING) {
            Plog.e("Pointr SDK is not running yet, cannot initialize the map. Please start the Pointr SDK.");
            return;
        }
        poiBackground = getResources().getColor(R.color.transparent_blue);

        drawablesInLevel = new HashMap<>();

        currentPosition = new Position();
        positionInOtherFacility = new Position();

        mapManager = pointr.getMapManager();

        map = getMap();

        if (pinViewIconSize != null) {
            setPinViewIconSize(pinViewIconSize);
        }
        map.setMapViewProvider(this);
        map.addListener(this);
        // convenient way for using scroll function on onCreate methods
        map.post(() -> map.scrollToAndCenter(0.5, 0.5));

        map.post(()->map.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                map.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                map.setScaleForFill();
            }
        }));
        if (getLevelPicker() != null) {
            LevelScrollView levelScrollView = getLevelPicker();
            levelScrollView.setController(this);
            levelScrollView.setCurrentLevel(getCurrentLevel(map));
        }

        if (getMapModeSwitcher() != null) {
            MapModeChangerView mapModeChangerView = getMapModeSwitcher();
            mapModeChangerView.setMapModeChangerListener(this);
        }

        if (getSearchView() != null) {
            getSearchView().setController(this);
        }

        if (getLocateMeButton() != null) {
            getLocateMeButton().addListener(this);
        }

        this.registerPointrListeners();
        this.updatePoisInMap();
    }

    @Override
    public ContainerFragmentState getState() {
        return state;
    }

    public void onSingleTap(MotionEvent event) {
        switch (state) {
            case Map:
                if (previousState == ContainerFragmentState.PoiSelected) {
                    transitStateTo(ContainerFragmentState.PoiSelected);
                } else if (previousState == ContainerFragmentState.Search) {
                    transitStateTo(ContainerFragmentState.Search);
                } else if (previousState == ContainerFragmentState.PathfindingHeaderAndFooter) {
                    transitStateTo(ContainerFragmentState.PathfindingHeaderAndFooter);
                }
                break;
            case Search:
                hideKeyboard();
                transitStateTo(ContainerFragmentState.Map);
                break;
            case PoiSelected:
                transitStateTo(ContainerFragmentState.Map);
                break;
            case PathfindingHeader:
                transitStateTo(ContainerFragmentState.PathfindingHeaderAndFooter);
                break;
            case PathfindingHeaderAndFooter:
                // do nothing
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pointr == null || pointr.getState() == null || pointr.getState() != PointrBase.State.RUNNING) {
            Plog.e("Pointr SDK is not running yet, cannot initialize the map. Please start the Pointr SDK.");
            return;
        }

        resumeMap();
        pointr.resume();
        this.registerPointrListeners();
        this.updatePoisInMap();
    }

    private void resumeMap() {
        map.resume();
        if (isPinOnScreen.get()) {
            isPinOnScreen.set(false);
            onPinEnterOrExit(isPinOnScreen.get());
        }
        if (map.getCurrentLevel() != PositioningTypes.INVALID_LEVEL) {
            map.updateMapForCurrentLevel();
        } else {
            List<Integer> levels = null;
            if (mapManager != null) {
                levels = mapManager.getLevelList();
            }
            if (levels != null && levels.size() > 0) {
                map.setCurrentLevel(levels.get(0));
            }
        }
    }

    private void registerPointrListeners() {
        DataManager dataManager = pointr.getDataManager();
        if (dataManager != null) {
            dataManager.addListener(this);
        }

        PoiManager poiManager = pointr.getPoiManager();
        if (poiManager != null) {
            poiManager.addListener(this);
        }

        ConfigurationManager configurationManager = pointr.getConfigurationManager();
        if (configurationManager != null) {
            configurationManager.addListener(this);
        }

        PathManager pathManager = pointr.getPathManager();
        if (pathManager != null) {
            pathManager.addListener(this);
        }

        PositionManager positionManager = pointr.getPositionManager();
        if (positionManager != null) {
            positionManager.addListener(this);
        }

        if (mapManager != null) {
            mapManager.addListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) {
            map.pause();
        }
        pointr.pause();
        this.unregisterPointrListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (map != null) {
            map.setMapViewProvider(null);
            map.removeListener(this);
            map.destroy();
        }
        this.unregisterPointrListeners();
        getLevelPicker().destroy();
    }

    private void unregisterPointrListeners() {
        DataManager dataManager = pointr.getDataManager();
        if (dataManager != null) {
            dataManager.removeListener(this);
        }

        PoiManager poiManager = pointr.getPoiManager();
        if (poiManager != null) {
            poiManager.removeListener(this);
        }

        ConfigurationManager configurationManager = pointr.getConfigurationManager();
        if (configurationManager != null) {
            configurationManager.removeListener(this);
        }

        PathManager pathManager = pointr.getPathManager();
        if (pathManager != null) {
            pathManager.removeListener(this);
        }

        PositionManager positionManager = pointr.getPositionManager();
        if (positionManager != null) {
            positionManager.removeListener(this);
        }

        if (mapManager != null) {
            mapManager.removeListener(this);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                Snackbar.make(mapModeButton, R.string.positioning_disabled_snackbar, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void startPathfinding(PoiContainer poi) {
        PoiManager poiManager = pointr.getPoiManager();
        PathManager pathManager = pointr.getPathManager();
        if (pathManager.isPathfindingStarted()) {
            Plog.w("There is already an active pathfinding in progress. Abort before starting new one.");
            return;
        }
        if (poiManager != null) {
            poiManager.setSelectedPoi(poi);
        }
        if (pathManager != null) {
            pathManager.startPathFinding();
        }
    }

    @Override
    public void abortPathfinding() {
        PathManager pathManager = Pointr.getPointr().getPathManager();
        if (pathManager != null) {
            pathManager.abortPathFinding();
        }
        PoiManager poiManager = Pointr.getPointr().getPoiManager();
        if (poiManager != null) {
            poiManager.resetSelectedPoi();
        }
        Plog.i("Path is aborted");
        getActivity().runOnUiThread(()->changePathWithPath(null));
    }

    @Override
    public void transitStateTo(ContainerFragmentState state) {
        Plog.v("BASE transit state to - " + state);
        if (stateChangeListener != null) {
            stateChangeListener.onDisplayStateChanged(state);
        }

        previousState = this.state;
        this.state = state;
        switch (state) {
            case Map:
                if (getLocateMeButton() != null) {
                    getLocateMeButton().detectPositionOnScreen(false);
                }
                if (getNavigationFooter() != null) {
                    getNavigationFooter().setVisibility(View.INVISIBLE);
                }
                setComponentVisibility(false,
                        getMapModeSwitcher(),
                        getLevelPicker(),
                        getTurnByTurnHeader(),
                        getLocateMeButton(),
                        getSearchView());
                break;
            case Search:
                if (getLocateMeButton() != null) {
                    getLocateMeButton().detectPositionOnScreen(true);
                    getLocateMeButton().setVisibility(!isPinOnScreen.get());
                }
                if (getNavigationFooter() != null) {
                    getNavigationFooter().setVisibility(View.INVISIBLE);
                }
                if (getLevelPicker() != null && getLevelPicker().getShouldBeVisible()) {
                    getLevelPicker().setVisibility(View.VISIBLE);
                }
                setComponentVisibility(false, getTurnByTurnHeader());
                setComponentVisibility(true, getSearchView(), getMapModeSwitcher());
                break;
            case PoiSelected:
                if (getLocateMeButton() != null) {
                    getLocateMeButton().detectPositionOnScreen(false);
                }
                if (getLevelPicker() != null && getLevelPicker().getShouldBeVisible()) {
                    getLevelPicker().setVisibility(View.VISIBLE);
                }
                setComponentVisibility(
                        false, getMapModeSwitcher(), getTurnByTurnHeader(), getLocateMeButton(), getSearchView());
                setComponentVisibility(true, getNavigationFooter());
                if (getNavigationFooter() != null) {
                    NavigationFooterView navigationFooterView = getNavigationFooter();
                    navigationFooterView.setPathfindingActive(true);
                }
                break;
            case PathfindingHeader:
                if (getLocateMeButton() != null) {
                    getLocateMeButton().detectPositionOnScreen(true);
                    getLocateMeButton().setVisibility(!isPinOnScreen.get());
                }
                if (getNavigationFooter() != null) {
                    getNavigationFooter().setPathfindingActive(false);
                    getNavigationFooter().setVisibility(View.INVISIBLE);
                }
                setComponentVisibility(false, getMapModeSwitcher(), getLocateMeButton(), getLevelPicker());
                setComponentVisibility(true, getTurnByTurnHeader());

                scaleToCurrentLocation(getPathFindingZoomValue());
                break;
            case PathfindingHeaderAndFooter:
                if (getLocateMeButton() != null) {
                    getLocateMeButton().detectPositionOnScreen(true);
                    getLocateMeButton().setVisibility(!isPinOnScreen.get());
                }
                if (getNavigationFooter() != null) {
                    getNavigationFooter().setPathfindingActive(false);
                }
                setComponentVisibility(false, getMapModeSwitcher(), getLocateMeButton(), getLevelPicker());
                setComponentVisibility(true, getNavigationFooter(), getTurnByTurnHeader());

                scaleToCurrentLocation(getPathFindingZoomValue());
                break;
        }
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(getView().getWindowToken(), 0);
    }

    public void setStateChangeListener(OnFragmentDisplayStateChangedListener stateChangeListener) {
        this.stateChangeListener = stateChangeListener;
    }

    protected void setComponentVisibility(boolean visible, Hideable... views) {
        for (int i = 0, viewsLength = views.length; i < viewsLength; i++) {
            Hideable hideable = views[i];
            if (hideable != null) {
                hideable.setVisibility(visible);
            }
        }
    }

    public void setSelectedPoi(PoiContainer poi) {
        PoiManager poiManager = pointr.getPoiManager();
        if (!poi.isContainerValid()) {
            Plog.v("Poi selection is not valid. Will not set the selection.");
            return;
        }
        if (poiManager != null) {
            poiManager.setSelectedPoi(poi);
            PointrConfig.selectPoi = poi;
            if (getNavigationFooter() != null) {
                getNavigationFooter().setSelectedPoi(poi);
            }

            transitStateTo(ContainerFragmentState.PoiSelected);
            map.getMapModeCoordinator().setMapMode(MapMode.Free);
        } else {
            Plog.w("Poi manager is null, path-finding won't work");
        }
    }

    protected float getHalfZoomValue() {
        return (map.getMaxZoomScale() + map.getMinZoomScale()) / 2;
    }

    protected float getQuarterZoomValue() {
        return (map.getMaxZoomScale() + map.getMinZoomScale()) / 4;
    }

    protected float getPathFindingZoomValue() {
        return (map.getMaxZoomScale() + map.getMinZoomScale()) / 8;
    }

    protected void scaleToCurrentLocation(float scale) {
        Position pos = getCurrentPosition();
        if (pos == null) {
            map.smoothScaleFromCenter(scale);
        } else {
            map.slideToAndCenterWithScale(pos.getX(), pos.getY(), scale);
        }
    }

    public int getPinViewIconSize() {
        return pinViewIconSize;
    }

    public void setPinViewIconSize(Integer pinViewIconSize) {
        this.pinViewIconSize = pinViewIconSize;
        if (getPinView() != null) {
            PinView pinView = getPinView();
            pinView.setSize(pinViewIconSize);
        }
    }

    // region PathManager.Listener methods
    @Override
    public void onDestinationReached(Poi destination) {
        getActivity().runOnUiThread(() -> {
            map.getBasicPathView().clear();
            transitStateTo(ContainerFragmentState.Search);
            map.getMapModeCoordinator().setMapMode(MapMode.Tracking);
            map.slideToAndCenterWithScale(0.5, 0.5, map.getMinZoomScale());
            Snackbar
                    .make(map,
                            String.format(getResources().getString(R.string.map_reached), destination.getName()),
                            Snackbar.LENGTH_LONG)
                    .show();
            changePathWithPath(null);
        });
    }

    @Override
    public void onPathCalculated(Path calculatedPath) {
        //System.out.println("678-------------:onPathCalculated:dis"+calculatedPath.getWalkingDistance()+
                //"wailtiem:"+calculatedPath.getTravelTime() + "cost:"+calculatedPath.getTravelCost());
            getActivity().runOnUiThread(() -> {
                if (state == ContainerFragmentState.PathfindingHeader ||
                        state == ContainerFragmentState.PathfindingHeaderAndFooter) {
                if (calculatedPath == null)
                    return;
                if (getTurnByTurnHeader() != null) {
                    getTurnByTurnHeader().updateNextDirection(calculatedPath.getDirections());
                }
                changePathWithPath(calculatedPath);
                }
            });
    }

    public void changePathWithPath(Path newPath) {
        if (!PointrHelper.isCalledOnUIThread()) {
            Plog.v("changePathWithPath needs to be called on UI Thread, calling again on UI thread");
            changePathWithPath(newPath);
            return;
        }
        this.path = newPath;
        // Add Destination Marker to path
        if (newPath == null) {
            if (destinationMarkerDrawable != null) {
                map.removeDrawable(destinationMarkerDrawable);
                destinationMarkerDrawable = null;
            }
        } else {
            // TODO: all of these will be simplified with latest PathManager code
            // check if end point of path changed
            boolean isThePathDifferent =
                    map.getCurrentPath() == null ||
                            (map.getCurrentPath().getLastNode().getLocation().getX() !=
                                    newPath.getLastNode().getLocation().getX()) ||
                            (map.getCurrentPath().getLastNode().getLocation().getY() != newPath.getLastNode().getLocation().getY());
            if (isThePathDifferent && destinationMarkerDrawable != null) {
                // Path is changed or removed; remove destination drawable
                map.removeDrawable(destinationMarkerDrawable);
                destinationMarkerDrawable = null;
            }

            NodeInterface nodeDestination = newPath.getLastNode();
            if (nodeDestination.getLocation().getLevel() == map.getCurrentLevel() &&
                    !map.drawableExists(destinationMarkerDrawable)) {
                destinationMarkerDrawable = new SimpleDrawable();
                destinationMarkerDrawable.setX(nodeDestination.getLocation().getX());
                destinationMarkerDrawable.setY(nodeDestination.getLocation().getY());
                destinationMarkerDrawable.setIdentifier(destinationMarkerKey);
                destinationMarkerDrawable.setRotatable(true);
                map.addDrawable(destinationMarkerDrawable);
                map.realignDrawable(destinationMarkerDrawable);
            }
        }

        // set path
        if (newPath != null) {
            newPath.setIdentifier(pathDrawableKey);
            map.addDrawable(newPath);
        } else {
            map.removeDrawable(map.getCurrentPath());
        }

        map.setCurrentPath(newPath);
    }

    @Override
    public void onPathCalculationFailed(final PathManagementError error) {
        getActivity().runOnUiThread(() -> {
            if (error.getErrorType() == PathManagementError.Type.PATH_UPDATE_FAILED) {
                // it will keep trying, will update the path when it is possible
                return;
            }
            map.getBasicPathView().clear();
            transitStateTo(ContainerFragmentState.Search);
            map.getMapModeCoordinator().setMapMode(MapMode.Tracking);
            Snackbar.make(map, R.string.route_failed_path_calculation, Snackbar.LENGTH_SHORT).show();
            changePathWithPath(null);
        });
    }

    @Override
    public void onPathCalculationAborted() {
        getActivity().runOnUiThread(() -> {
            map.getBasicPathView().clear();
            transitStateTo(ContainerFragmentState.Search);
            map.getMapModeCoordinator().setMapMode(MapMode.Tracking);
            changePathWithPath(null);
        });
    }
    // endregion

    // region DataManager.Listener methods

    @Override
    public void onDataManagerStartDataManagementForVenue(Venue venue, Facility facility, boolean isOnlineData) {
        // Do nothing
    }

    @Override
    public void onDataManagerCompleteAllForVenue(Venue venue,
                                                 Facility facility,
                                                 boolean isSuccessful,
                                                 boolean isOnlineData,
                                                 List<ErrorMessage> errors) {
        Plog.v("+ onCompleteAll for venue " + (venue == null ? "none" : venue.getVenueId()) + "- online " +
                isOnlineData + " - successful " + isSuccessful);
        if (!isSuccessful) {
            getActivity().runOnUiThread(
                    () -> Snackbar.make(map, R.string.data_update_error_occurred, Snackbar.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onDataManagerBeginProcessingDataForVenue(Venue venue,
                                                         Facility facility,
                                                         DataType dataType,
                                                         boolean isOnlineData) {
        // Do nothing
    }

    @Override
    public void onDataManagerEndProcessingDataForVenue(Venue venue,
                                                       Facility facility,
                                                       DataType dataType,
                                                       boolean isOnlineData,
                                                       boolean isSuccessful,
                                                       List<ErrorMessage> errors) {
        // Do nothing
    }

    @Override
    public void onDataManagerBeginProcessingMapUpdateForVenue(Venue venue,
                                                              Facility facility,
                                                              int level,
                                                              DataType type,
                                                              boolean isOnlineData) {
        // Do nothing
    }

    @Override
    public void onDataManagerEndProcessingMapUpdateForVenue(Venue venue,
                                                            Facility facility,
                                                            int level,
                                                            DataType type,
                                                            boolean isOnlineData,
                                                            boolean isSuccessful,
                                                            List<ErrorMessage> errors) {
        // Do nothing
    }

    @Override
    public void onVenueReady(Venue venue) {
        // Do nothing
    }

    @Override
    public void onDataManagerBeginProcessingGlobalData(DataType type, boolean isOnlineData) {
    }

    @Override
    public void onDataManagerEndProcessingGlobalData(DataType type, boolean isOnlineData, boolean isSuccessful) {
    }
    // endregion

    @Override
    public void onConfigurationUpdate() {
        List<Integer> levels = mapManager.getLevelList();
        if (levels.isEmpty()) {
            Plog.w("Levels are empty, map view cannot update maps");
            return;
        }
        if (levels.contains(map.getCurrentLevel())) {
            map.updateMapForCurrentLevel();
        } else {
            map.setCurrentLevel(levels.get(0));
        }
    }

    @Override
    public void onConfigurationUpdateFail() {
    }
    // region PositionManager.Listener methods
    @Override
    public void onLevelChanged(int level) {
        Plog.v("Level change detected -> " + level);
        boolean isCorrectFacility = Pointr.getPointr().getConfigurationManager().isConfiguredToPhysicalFacility();
        if (!isCorrectFacility) {
            // Ignore data from another facility
            Plog.i("Won't change level, user is viewing a different facility.");
            return;
        }

        map.setCurrentLevel(level);
        zoomToCurrentPosition();
    }

    private void zoomToCurrentPosition() {
        PositionManager posManager = pointr.getPositionManager();
        Position userPosition;
        if (posManager != null) {
            CalculatedLocation calculatedLocation = posManager.getLastCalculatedLocation();
            userPosition = calculatedLocation.convertToPosition();
            map.zoomToCurrentPosition(userPosition);
        }
    }

    private boolean shouldShowPinHeading() {
        ConfigurationManager configurationManager = pointr.getConfigurationManager();
        if (configurationManager != null) {
            CoreConfiguration configuration = configurationManager.getCurrentConfiguration();
            if (configuration != null) {
                return configuration.getUserInterfaceConfiguration().getShouldShowPinHeading();
            }
        }
        return true;
    }

    private boolean shouldShowLevelInformation() {
        ConfigurationManager configurationManager = Pointr.getPointr().getConfigurationManager();
        if (configurationManager != null) {
            CoreConfiguration coreConfiguration = configurationManager.getCurrentConfiguration();
            if (coreConfiguration != null) {
                return coreConfiguration.getUserInterfaceConfiguration().getShouldShowLevelInformationToast();
            } else {
                Plog.w("Configuration doesn't exist. Won't show level information!");
            }
        } else {
            Plog.w("Configuration manager doesn't exist. Won't show level information!");
        }
        return false;
    }

    @Override
    public void onLocationCalculated(CalculatedLocation calculatedLocation) {
        updateMapViewBasedOnCalculatedLocation(calculatedLocation);
    }

    private void updateMapViewBasedOnCalculatedLocation(CalculatedLocation calculatedLocation) {
        if (calculatedLocation == null) {
            this.getActivity().runOnUiThread(() -> hideUserPin());
            map.setCurrentLocation(null);
            isPositionCalculatedBefore.set(false);
            return;
        }
        Position userPosition = calculatedLocation.convertToPosition();
        currentPosition = userPosition;
        map.setCurrentLocation(calculatedLocation);
        if (userPosition == null) {
            this.getActivity().runOnUiThread(() -> hideUserPin());
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
            if (map.getMapModeCoordinator().getMapMode() != MapMode.Free) {
                map.getMapModeCoordinator().setMapMode(MapMode.Free);
            }
            this.getActivity().runOnUiThread(() -> hideUserPin());
        } else  // look if this else causes anything.
            if (userPosition.isValidNormalisedPosition()) {
                // position is in physical facility
                positionInOtherFacility = new Position();
                updateUserPin(calculatedLocation);
                map.showMapIfNotShownYet();
            } else {
                this.getActivity().runOnUiThread(() -> hideUserPin());
            }
        showPositionOnTheMapIfFirstOne(currentPosition);
    }

    private void showPositionOnTheMapIfFirstOne(Position position) {
        if (position != null && position.isValidNormalisedPosition()) {
            if (isPositionCalculatedBefore.get()) {
                return;
            }
            isPositionCalculatedBefore.set(true);
            if (position.getLevel() != map.getCurrentLevel()) {
                map.setCurrentLevel(position.getLevel());
            }
            map.zoomToCurrentPosition(position);
        } else {
            // invalid position from position manager
            isPositionCalculatedBefore.set(false);
            Plog.v("Invalid position to set.");
        }
    }

    @Override
    public void onPositionIsFading() {
    }

    @Override
    public void onPositionIsLost() {
        updateMapViewBasedOnCalculatedLocation(null);
    }

    private void hideUserPin() {
        if (userPinView != null) {
            userPinView.disappearFromView();
        }

        map.removeDrawable(userPinDrawable);

        userPinDrawable = null;
        userPinView = null;
    }

    private void updateUserPin(CalculatedLocation calculatedLocation) {
        // if the level is correct show drawable
        // else hide it
        Position userPosition = calculatedLocation.convertToPosition();
        boolean isPositionLevelSameWithMapLevel = map.getCurrentLevel() == currentPosition.getLevel();
        boolean isPositionInsideTheScreenBoundary = map.isPointVisibleInMap(userPosition);
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
            if (!map.drawableExists(userPinDrawable)) {
                createUserPinDrawable();
            }

            map.getActivity().runOnUiThread(() -> {
                map.rotateCanvasWithUserPinView(userPosition);
                adjustUserPinView(calculatedLocation);
            });

        } else {  // if(!isPositionLevelSameWithMapLevel){
            this.getActivity().runOnUiThread(() -> hideUserPin());
        }
        map.refreshDrawable(userPinDrawable);
    }

    private void adjustUserPinView(CalculatedLocation calculatedLocation) {
        Position userPosition = calculatedLocation.convertToPosition();

        if (userPinDrawable == null) {
            Plog.v("user pin drawable is null");
            return;
        }

        if (userPinView == null) {
            Plog.v("user pin view is null but drawable is not, refresh views");
            map.refreshDrawable(userPinDrawable);
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

    private void rotatePinWithRotation(Position position) {
        if (position.getOrientation() == PositioningTypes.INVALID_ORIENTATION || userPinDrawable == null) {
            return;
        }
        // if user pin drawable exist and has a valid rotation rotate and refresh
        userPinView.rotate(position);
    }

    private void createUserPinDrawable() {
        boolean isValidPosition = currentPosition.isValidNormalisedPosition();
        boolean isCurrentLevel = map.getCurrentLevel() == currentPosition.getLevel();
        synchronized (pinViewCreationLock) {
            if (isValidPosition && isCurrentLevel) {
                Plog.v("create user pin");
                userPinDrawable = new SimpleDrawable();
                // use given identifier in mapView for userpin, else some error can occur when rotating
                userPinDrawable.setIdentifier(MapView.userPinDrawableKey);
                userPinDrawable.setInteractive(false);
                map.addDrawable(userPinDrawable);
            }
        }
    }

    private boolean physicalAndMapViewFacilitiesAreSame() {
        GeofenceManager geofenceManager = pointr.getGeofenceManager();
        if (geofenceManager != null && geofenceManager.getCurrentFacility() != null) {
            return geofenceManager.getCurrentFacility().equals(map.getCurrentFacility());
        }
        return false;
    }

    @Override
    public void onGeolocationCalculated(GeoPosition location) {
        // Do nothing
    }

    @Override
    public void onStateChanged(EnumSet<PositionManager.State> set) {
        if (set.contains(PositionManager.State.BluetoothOff)) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }
    // endregion

    @Override
    public void onTriggerPoiEntered(Poi poi) {
        // do nothing
    }

    @Override
    public void onTriggerPoiExited(Poi poi) {
    }

    @Override
    public void onPoiUpdated() {
        updatePoisInMap();
    }

    private void updatePoisInMap() {
        if (drawablesInLevel.containsKey(poiDrawablesKey)) {
            map.removeDrawables(drawablesInLevel.get(poiDrawablesKey));
        }

        int level = map.getCurrentLevel();
        PoiManager poiManager = Pointr.getPointr().getPoiManager();
        if (poiManager == null) {
            Plog.w("Cannot get pois for level. Poi Manager is null");
            return;
        }

        if (map.getCurrentFacility() == null || map.getCurrentFacility().getFacilityId() == INVALID_FACILITY) {
            Plog.v("No facility to get poi's for.");
            return;
        }

        PoiContainer poiContainer = poiManager.getAllPoi(level, null, map.getCurrentFacility().getFacilityId());
        if (poiContainer == null || !poiContainer.isContainerValid()) {
            Plog.w("No Poi to draw yet");
            return;
        }
        List<Poi> poisOnLevel = poiContainer.getPoiList();

        List<MapDrawable> poiDrawables = new ArrayList<>();

        for (Poi poi : poisOnLevel) {
            poi.setIsRotatable(true);
            poi.setIsInteractive(true);

            poiDrawables.add(poi);
            map.addDrawable(poi);
        }

        drawablesInLevel.put(poiDrawablesKey, poiDrawables);
        map.realignDrawables();
        map.refreshView();
    }

    public PinView createNewPinView() {
        PinView pinView = new PinView(getActivity());
        pinView.setMap(getMap());
        if (pinViewIconSize != null) {
            pinView.setSize(pinViewIconSize);
        }
        return pinView;
    }
    public PinView getPinView() {
        return userPinView;
    }

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
            POIView poiView = new POIView(getActivity());
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

    /**
     * With this method the image icon of poi can be overriden depending on its name/type as preference
     * It should return the resource id of the drawable that is preferred
     *
     * @param poi
     * @return
     */
    private int getImageResourceIdForPoi(Poi poi) {
        // A default return value can be set, to use the pointr default image icons in wanted cases
        // Wanted cases can be overriden like this, if the case is not overriden, it will return 0 and pointr
        // default icon will be used
        if (poi.getName().contains("toilets")) {
            return R.drawable.toilets;
        }
        return 0;
    }

    @Override
    public BasicPathView.CustomDrawablePath lineViewForDrawable(MapDrawable drawable) {
        // create drawable path here.
        BasicPathView.CustomDrawablePath result = new BasicPathView.CustomDrawablePath();
        // create paint object
        Paint linePaint = new Paint();
        int color = map.getContext().getResources().getColor(R.color.dark_blue);
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
            pathObject = calculatePathArrayForLevel(drawable, map.getCurrentLevel());

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

    private List<float[]> calculatePathArrayForLevel(MapDrawable drawable, int currentLevel) {
        if (drawable == null || !(drawable instanceof Path)) {
            Plog.v("MapDrawable is null or not an instance of path");
            return null;
        }

        List<float[]> pathsToDrawResult = new ArrayList<>();

        Path path = (Path)drawable;

        // if we are going to take the facility of the current configuration
        final Facility visibleFacility = map.getCurrentFacility();

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

    private List<Location> getLocationArrayFromNodes(List<NodeInterface> nodesList) {
        List<Location> locations = new ArrayList<>();
        for (NodeInterface node : nodesList) {
            locations.add(node.getLocation());
        }
        return locations;
    }

    private float[] calculatePathArray(List<Location> pointsArray) {
        if (pointsArray == null || pointsArray.isEmpty()) {
            Plog.v("No nodes to draw.");
            return null;
        }
        float[] pathArray = new float[(pointsArray.size() - 1) * 4];
        CoordinateTranslater ctr = map.getCoordinateTranslater();
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

    // endregion

    @Override
    public void onDrawablePointViewTouched(BasePointrMapView mapView, DrawablePointView drawablePointView) {
        mapView.moveToMarker(drawablePointView, true);
        if (drawablePointView instanceof POIView) {
            POIView poiView = (POIView)drawablePointView;
            Poi poi = (Poi)poiView.getDrawable();
            if (!map.hasPath()) {
                setSelectedPoi(new PoiContainer(poi));
            }
        }
    }

    @Override
    public void onMapLevelChangedTo(BasePointrMapView mapView, int level) {
        if (shouldShowLevelInformation()) {
            String message = String.format(getString(R.string.display_level_text), String.valueOf(level));
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }

        if (level != mapView.getCurrentLevel()) {
            mapView.getMapModeCoordinator().setMapMode(MapMode.Free);
        }

        // This is for optimizing the map download. We prioritize the level user is viewing while downloading maps.
        notifyDataManagerOfLevelChange(level);

        getLevelPicker().setCurrentLevel(level);
        updatePoisInMap();
        adjustLevelForStoreLayout(mapView, level, mapView.getMapModeCoordinator().getMapMode());
        if (isMapShownBefore.compareAndSet(false, true)) {
            map.setScaleForFill();
        }

        Plog.v("Map level changed to " + level);
    }

    private void notifyDataManagerOfLevelChange(int level) {
        DataManager dataManager = Pointr.getPointr().getDataManager();
        if (dataManager != null) {
            dataManager.handleMapViewLevelChange(level);
        }
    }

    private void adjustLevelForStoreLayout(BasePointrMapView mapView, int level) {
        PoiManager poiManager = pointr.getPoiManager();
        Poi layoutPoi = poiManager == null ? null : poiManager.getStoreLayoutPoi(level);
        if (layoutPoi != null) {
            Map<String, Float> constraints = storeLayoutConstraintsFromRegionArray(layoutPoi.getRegion());
            mapView.setConstraintSize(constraints.get("minWidth"),
                    constraints.get("maxWidth"),
                    constraints.get("minHeight"),
                    constraints.get("maxHeight"));
            mapView.setCenterX(constraints.get("minWidth") +
                    (constraints.get("maxWidth") - constraints.get("minWidth")) / 2);
            mapView.setCenterY(constraints.get("minHeight") +
                    (constraints.get("maxHeight") - constraints.get("minHeight")) / 2);
            mapView.scrollToAndCenter(mapView.getCenterX(), mapView.getCenterY());
        } else {
            mapView.unsetConstraintSize();
            mapView.setCenterX(0.5F);
            mapView.setCenterY(0.5F);
        }
    }

    private void adjustLevelForStoreLayout(BasePointrMapView mapView, int level, MapMode mapMode) {
        if (mapMode == MapMode.PathTracking || mapMode == MapMode.RotationalTracking) {
            mapView.unsetConstraintSize();
            mapView.setCenterX(0.5F);
            mapView.setCenterY(0.5F);
            return;
        }
        PoiManager poiManager = pointr.getPoiManager();
        Poi layoutPoi = poiManager == null ? null : poiManager.getStoreLayoutPoi(level);
        if (layoutPoi != null) {
            Map<String, Float> constraints = storeLayoutConstraintsFromRegionArray(layoutPoi.getRegion());
            mapView.setConstraintSize(constraints.get("minWidth"),
                    constraints.get("maxWidth"),
                    constraints.get("minHeight"),
                    constraints.get("maxHeight"));
            mapView.setCenterX(constraints.get("minWidth") +
                    (constraints.get("maxWidth") - constraints.get("minWidth")) / 2);
            mapView.setCenterY(constraints.get("minHeight") +
                    (constraints.get("maxHeight") - constraints.get("minHeight")) / 2);
        }
    }

    private Map<String, Float> storeLayoutConstraintsFromRegionArray(List<Point> region) {
        float minWidth = 1.0F;
        float minHeight = 1.0F;
        float maxWidth = 0.0F;
        float maxHeight = 0.0F;

        for (Point point : region) {
            minWidth = (float)Math.min(minWidth, point.getX());
            minHeight = (float)Math.min(minHeight, point.getY());
            maxWidth = (float)Math.max(maxWidth, point.getX());
            maxHeight = (float)Math.max(maxHeight, point.getY());
        }

        Map<String, Float> layoutMap = new HashMap<>();
        layoutMap.put("minWidth", minWidth);
        layoutMap.put("minHeight", minHeight);
        layoutMap.put("maxWidth", maxWidth);
        layoutMap.put("maxHeight", maxHeight);

        return layoutMap;
    }

    @Override
    public void onMapModeChangedTo(BasePointrMapView mapView, MapMode mapMode) {
        String message = "";
        switch (mapMode) {
            case Tracking:
                message = "Auto tracking mode activated";
                break;
            case RotationalTracking:
                message = "Auto tracking with rotation mode activated";
                break;
            case Free:
                message = "Free mode activated";
                break;
            case PathTracking:
                message = "Path tracking mode activated";
                break;
            default:
                break;
        }

        Plog.v("Mode changed : " + message);

        doModeChangeEventsWithMapMode(mapMode);
    }

    @Override
    public void didReceiveSingleFingerTap(BasePointrMapView mapView, MotionEvent event) {
        onSingleTap(event);
    }

    @Override
    public void didReceiveDoubleFingerTap(BasePointrMapView mapView, MotionEvent event) {
        mapView.doubleTapZoom(event);
    }

    public void onLocatorClicked() {
        Position pos = getCurrentPosition();
        if (pos == null || !pos.isValidNormalisedPosition()) {
            // Cannot be located since there is no location
            return;
        }

        map.previewLevel(pos.getLevel());
        map.slideToAndCenterWithScale(pos.getX(), pos.getY(), getQuarterZoomValue());
        PathManager pathManager = pointr.getPathManager();
        if (pathManager == null || !pathManager.isPathfindingStarted()) {
            // There is no active path, and user wants to locate its position
            map.getMapModeCoordinator().setMapMode(MapMode.Tracking);
        } else {
            // There is an active path, so continue with the mode that was user prior to user pin moving out of screen
            map.getMapModeCoordinator().setMapMode(
                    MapMode.PathTracking);  // Any preferred map mode for --pathfinding-- can be picked from here
        }
    }

    public Position getCurrentPosition() {
        if (currentPosition != null && currentPosition.isValidNormalisedPosition()) {
            return currentPosition;
        }
        return null;
    }

    public void onPinEnterOrExit(boolean isInScreen) {
        if (getLocateMeButton().isDetectable()) {
            this.getActivity().runOnUiThread(() -> getLocateMeButton().setVisibility(!isInScreen));
        }
        if (!isInScreen) {
            map.getMapModeCoordinator().setMapMode(MapMode.Free);
        } else {
            PathManager pathManager = pointr.getPathManager();
            if (pathManager == null || !pathManager.isPathfindingStarted()) {
                // There is no active path, and pin is on screen so switch to tracking
                map.getMapModeCoordinator().setMapMode(MapMode.Tracking);
            } else {
                map.getMapModeCoordinator().setMapMode(
                        MapMode.PathTracking);  // Any preferred map mode for --pathfinding-- can be picked from here
            }
        }
    }

    private void doModeChangeEventsWithMapMode(MapMode mapMode) {
        if (mapMode != MapMode.Free) {
            if (currentPosition == null) {
                Plog.w("Position is null no chance to put map mode to another mode than free");
                map.getMapModeCoordinator().setMapMode(MapMode.Free);
                return;
            }
            // Reset zoom and content offset when switching to static mode
            if (currentPosition.getLevel() != PositioningTypes.INVALID_LEVEL) {
                // We have level and position and not in free mode
                // so set the center and level for position
                map.scrollToAndCenter(currentPosition.getX(), currentPosition.getY());
            } else if (positionInOtherFacility.getLevel() != PositioningTypes.INVALID_LEVEL) {
                // We have valid position in another facility
                // position is not in free mode
                if (switchFacilityAutomaticallyIfAvailable()) {
                    currentPosition = positionInOtherFacility;
                    positionInOtherFacility = new Position();
                    map.scrollToAndCenter(currentPosition.getX(), currentPosition.getY());
                }
            }

            if (currentPosition.getLevel() == PositioningTypes.INVALID_LEVEL) {
                Plog.w("Position is invalid no chance to put map mode to another mode than free");
                map.getMapModeCoordinator().setMapMode(MapMode.Free);
            }
        }

        adjustLevelForStoreLayout(map, map.getCurrentLevel(), mapMode);
    }

    private boolean switchFacilityAutomaticallyIfAvailable() {
        ConfigurationManager configurationManager = pointr.getConfigurationManager();
        if (configurationManager != null) {
            CoreConfiguration configuration = configurationManager.getCurrentConfiguration();
            if (configuration != null &&
                    !configuration.getGeofenceManagerConfig().getIsAutomaticFacilitySwitchEnabled()) {
                return false;
            }
        }
        // auto facility change enabled so set the facility and the center and level for position
        if (!physicalAndMapViewFacilitiesAreSame()) {
            // position is in different facility than seen and we want to change forcedFacility to physical just let
            // forced facility to invalid and when callback on config manager updated came facility will be changed
            // automatically
            map.setForcedFacility(null);
            return true;
        }
        return false;
    }

    @Override
    public void userDidTapOnMapModeView(MapModeChangerView mapModeChangerView) {
        MapMode newMode = MapMode.Free;                   // You can change here.
        map.getMapModeCoordinator().setMapMode(newMode);  // Can select which map to update.
        String message = String.format(getString(R.string.map_mode_switched),
                getResources().getString(newMode.getVisibleNameResourceId()));
        this.getActivity().runOnUiThread(() -> Snackbar.make(map, message, Snackbar.LENGTH_SHORT).show());
    }

    public List<Integer> getLevelList() {
        MapManager mapManager = Pointr.getPointr().getMapManager();
        if (mapManager != null) {
            return mapManager.getLevelList();
        }
        return new ArrayList<>(0);
    }

    public int getCurrentLevel(BasePointrMapView map) {
        return map.getCurrentLevel();
    }

    @Override
    public void onUserPickedLevel(LevelScrollView view, int level) {
        map.setCurrentLevel(level);
    }

    @Override
    public void onMapsUpdated() {
        Plog.v("+ onMapsUpdated");
        if (map.getCurrentLevel() == PositioningTypes.INVALID_LEVEL) {
            if (mapManager == null) {
                Plog.w("Map manager is null, map view cannot update maps");
                return;
            }

            // We cannot show anything if there is no level before and after the update
            List<Integer> levels = mapManager.getLevelList();
            if (levels.isEmpty()) {
                Plog.w("Levels are empty, map view cannot update maps");
                return;
            }
            CalculatedLocation currentLoc = map.getCurrentLocation();  // Position that you want to show on map
            Facility currentMapFacility = map.getCurrentFacility();    // Current facility of map
            if (currentLoc != null && currentMapFacility != null &&
                    currentLoc.getFacilityId() == currentMapFacility.getFacilityId()) {
                int userPositionLevel = currentLoc.getLevel();
                if (levels.contains(userPositionLevel)) {
                    map.setCurrentLevel(userPositionLevel);
                } else {
                    map.setCurrentLevel(levels.get(0));
                }
            } else {
                map.setCurrentLevel(levels.get(0));
            }
        } else {
            map.updateMapForCurrentLevel();
        }
    }

    @Override
    public void onMapsUpdated(Venue venue, Facility facility, int level, DataType type) {
        Plog.v("+ onMapsUpdated for facility " + facility + " level " + level + " type " + type);
        if (mapManager == null) {
            Plog.w("Map manager is null, map view cannot update maps");
            return;
        }

        if (map.getCurrentFacility() == null) {
            Plog.i("There is no facility set yet.");
            return;
        }
        if (map.getCurrentFacility().getFacilityId() != facility.getFacilityId()) {
            Plog.w("Will ignore map manager update - current facility id is " +
                    map.getCurrentFacility().getFacilityId() + " and the updated facility id is " +
                    facility.getFacilityId());
            return;
        }

        List<Integer> levels = mapManager.getLevelList();
        if (!map.getIsFirstMapShown()) {
            Plog.v("No maps shown yet - show first map");
            Plog.v("Set current level to " + level);
            // We cannot show anything if there is no level before and after the update

            if (levels.isEmpty()) {
                Plog.w("Levels are empty, map view cannot update maps");
                return;
            }
            map.setCurrentLevel(level);
        } else if (map.getCurrentLevel() == level) {
            map.updateMapForCurrentLevel();
        }
    }


    private void testARSDk(View rootView){
        Button arScanBtn = new Button(this.getActivity());
        arScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ARMapJumpUtil.jumpARScanPage(getActivity());

            }
        });
        arScanBtn.setText("AR Scan");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(300,200);
        params.setMargins(20,200,0,0);
        arScanBtn.setLayoutParams(params);
        getSearchView().addView(arScanBtn);


        Button arNavBtn = new Button(this.getActivity());
        arNavBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(arNavgationListener != null){
                    arNavgationListener.onEnterARView();
                }

            }
        });
        arNavBtn.setText("AR Nav");
        FrameLayout.LayoutParams params2 = new FrameLayout.LayoutParams(300,200);
        params2.setMargins(300,200,0,0);
        arNavBtn.setLayoutParams(params2);
        getSearchView().addView(arNavBtn);

    }

    private ARNavgationListener arNavgationListener;

    public void setArNavgationListener(ARNavgationListener listener){
        this.arNavgationListener = listener;
    }

    public interface ARNavgationListener{
        void onEnterARView();
    }



}
