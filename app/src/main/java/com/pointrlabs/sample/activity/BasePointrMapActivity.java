package com.pointrlabs.sample.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.pointrlabs.core.configuration.CoreConfiguration;
import com.pointrlabs.core.configuration.GeofenceManagerConfiguration;
import com.pointrlabs.core.configuration.UserInterfaceConfiguration;
import com.pointrlabs.core.dataaccess.models.poi.Poi;
import com.pointrlabs.core.dependencyinjection.Injector;
import com.pointrlabs.core.management.ConfigurationManager;
import com.pointrlabs.core.management.PathManager;
import com.pointrlabs.core.management.PoiManager;
import com.pointrlabs.core.management.Pointr;
import com.pointrlabs.core.management.PointrBase;
import com.pointrlabs.core.management.PositionManager;
import com.pointrlabs.core.management.PositionManagerImpl;
import com.pointrlabs.core.management.Storage;
import com.pointrlabs.core.management.interfaces.PointrListener;
import com.pointrlabs.core.management.models.ErrorMessage;
import com.pointrlabs.core.management.models.Message;
import com.pointrlabs.core.management.models.WarningMessage;
import com.pointrlabs.core.map.ARController;
import com.pointrlabs.core.map.ARStateListener;
import com.pointrlabs.core.map.fragment.ARFragment;
import com.pointrlabs.core.map.fragment.LoadingFragment;
import com.pointrlabs.core.map.fragment.RouteScreenFragment;
import com.pointrlabs.core.map.interfaces.OnFragmentDisplayStateChangedListener;
import com.pointrlabs.core.map.model.ContainerFragmentState;
import com.pointrlabs.core.map.model.MapMode;
import com.pointrlabs.core.pathfinding.Path;
import com.pointrlabs.core.pathfinding.directions.TurnByTurnDirectionManager;
import com.pointrlabs.core.poi.models.PoiContainer;
import com.pointrlabs.core.positioning.model.Location;
import com.pointrlabs.core.positioning.model.Position;
import com.pointrlabs.sample.R;
import com.pointrlabs.sample.fragment.BaseContainerFragment;
import com.sensetime.armap.activity.ArMapNavigationActivity;
import com.sensetime.armap.activity.HTMLActivity;
import com.sensetime.armap.activity.WebViewActivity;
import com.sensetime.armap.constant.ApiConfig;
import com.sensetime.armap.constant.PointrConfig;
import com.sensetime.armap.dialog.UserGuideDialog;
import com.sensetime.armap.entity.ARPathEntity;
import com.sensetime.armap.listeners.FastClickListener;
import com.sensetime.armap.utils.ARPathUtils;
import com.sensetime.armap.utils.ARUtils;
import com.sensetime.armap.utils.STLog;
import com.sensetime.armap.utils.Utils;
import com.sensetime.armap.widget.HMMapView;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class BasePointrMapActivity extends AppCompatActivity
        implements ARStateListener, OnFragmentDisplayStateChangedListener, RouteScreenFragment.Listener, PointrListener {
    protected static final String TAG = BasePointrMapActivity.class.getSimpleName();
    private static final int REQUEST_CODE_ASK_LOCATION_PERMISSION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CODE_ASK_WHITELISTING_PERMISSION = 3;
    private AtomicBoolean shouldShowRouteScreenFragment = new AtomicBoolean(false);
    private boolean isRouteOnClickListenerAssigned = false;

    // Map fragment with the default implementation of both functionality and ui components
    private BaseContainerFragment containerFragment;
    protected Pointr pointr;

    // Augmented Reality
    private ARController arController;
    private ARFragment arFragment;
    private RouteScreenFragment routeScreenFragment = null;
    private boolean isContainerFragmentStateAvailableForAR = false;
    private ContainerFragmentState currentContainerFragmentState = ContainerFragmentState.Map;
    private AtomicBoolean isBleRequestSentBefore = new AtomicBoolean(false);

    protected TurnByTurnDirectionManager turnByTurnDirectionManager;
    private long lastPathUpdatedTimeStamp = 0;

    private Storage storage;

    // region PointrListener
    @Override
    public void onStateUpdated(Pointr.State state, List<WarningMessage> warningMessages) {
        STLog.i("","Pointr state updated to " + state.toString());
        runOnUiThread(() -> {
            showToastMessage(state.toString());
        });

        if (state.equals(PointrBase.State.RUNNING)) {
            setUpRouteScreen();
            proceedToMap(true);
        }
    }

    @Override
    public void onFailure(List<ErrorMessage> errorMessages) {
        STLog.e("TAG","Failed to start Pointr");
        String strMsg = "";
        if (errorMessages != null) {
            for (ErrorMessage errMsg : errorMessages) {
                STLog.e(TAG,errMsg.getMessage());
                strMsg += errMsg.getMessage() + "\n";
            }
        }

        final String finalMsg = strMsg;
        runOnUiThread(() -> { showToastMessage(finalMsg); });
    }
    // endregion

    // region Activity lifecycle
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length == 0) {
            STLog.e(TAG,"Android has returned empty results");
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_ASK_LOCATION_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    // Try again
                    tryStartEngine();
                } else {
                    // Permission denied
                    LoadingFragment loadingFragment = getLoadingFragment();
                    loadingFragment.showProgress(false);
                    loadingFragment.setText(getResources().getText(R.string.location_permission_rationale).toString());
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        initView();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // Don't initialise map if device version is lower than supported
            showUnsupportedVersionMessage();
            return;
        }

        if (savedInstanceState == null) {
            showLoadingFragment(getLoadingFragment());
        }

        pointr = Pointr.getPointr();
        pointr.addListener(this);

        if (pointr.getState() == null || pointr.getState() == PointrBase.State.OFF) {
            STLog.e(TAG,"Pointr is OFF, let's start it");
            tryStartEngine();
        } else {
            STLog.e(TAG,"Pointr is already ON, show map");
            setUpRouteScreen();
            proceedToMap(true);
        }
        registerBroadcast();
    }

    @Override
    protected void onRestart() {//todo 2019/12/12
        super.onRestart();
        if(pointr != null && pointr.getState() != PointrBase.State.RUNNING){
            Pointr.getPointr().start();
        }
    }

    @Override
    protected void onStart() {//todo 2019/12/12
        super.onStart();
        if(pointr != null && pointr.getState() != PointrBase.State.RUNNING){
            Pointr.getPointr().start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove all listeners
        if (pointr != null) {
            pointr.removeListener(this);
        }

        if (arController != null) {
            arController.removeARStateListener(this);
        }
        if (mainRec != null) {
            unregisterReceiver(mainRec);
            mainRec = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            isBleRequestSentBefore.set(true);
            if (resultCode == RESULT_CANCELED) {
                showToastMessage("Bluetooth is disabled, positioning will not work");
            }
            proceedToMap(false);
        } else if (requestCode == REQUEST_CODE_ASK_WHITELISTING_PERMISSION) {
            if (resultCode == RESULT_CANCELED) {
                showToastMessage(
                        "Battery optimizations are not ignored, app may not work properly when in background.");
                if (storage == null) {
                    storage = Injector.findObjectForClass(Storage.class);
                }
                if (storage != null) {
                    storage.saveAskedWhitelistingPermissionBeforeAndDenied(true);
                }
            }
        }
    }
    // endregion

    // region Pointr methods
    private void tryStartEngine() {
        // Check for Location Permissions
        int hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        int hasFineLocationPermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (hasLocationPermission != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showLocationPermissionMessage();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_CODE_ASK_LOCATION_PERMISSION);
            }
            return;  // We can't contionue without location permission, we will wait for it
        }
        if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                showLocationPermissionMessage();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_CODE_ASK_LOCATION_PERMISSION);
            }
            return;  // We cant continue without location permission, we will wait for it
        }

        STLog.d("TAG","+ startPointrEngine");
        // Start pointr engine with the given licence key
        configurePointr();
        pointr.start();
    }

    protected void configurePointr() {
        // Do nothing
        // 3rd parties can override here
    }
    // endregion

    // region Popup messages
    private void showToastMessage(String msg) {
//        assert Looper.getMainLooper() == Looper.myLooper();
//
//        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
//        toast.show();
    }

    private void showLocationPermissionMessage() {
//        DialogInterface.OnClickListener listener = (dialog, which)
//                -> ActivityCompat.requestPermissions(
//                this,
//                new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
//                REQUEST_CODE_ASK_LOCATION_PERMISSION);
//        new AlertDialog.Builder(this)
//                .setMessage(R.string.location_permission_rationale)
//                .setPositiveButton(android.R.string.ok, listener)
//                .create()
//                .show();
    }

    private void showUnsupportedVersionMessage() {
        LoadingFragment fragment = getLoadingFragment();
        fragment.showProgress(false);
        fragment.setText(getString(R.string.version_below_supported));
        showLoadingFragment(fragment);
    }
    // endregion



    /**
     * Gets or creates main fragment where the map functionality will be displayed
     *
     * @return map fragment
     */
    protected LoadingFragment getLoadingFragment() {
        LoadingFragment loadingFragment =
                (LoadingFragment)getSupportFragmentManager().findFragmentByTag(LoadingFragment.TAG);

        if (loadingFragment == null) {
            loadingFragment = LoadingFragment.newInstance();
        }

        return loadingFragment;
    }

    // TODO: Show Loading - Get Loading doesn't make sense. showLoadingFragment() shouldn't take any parameters (or have a different name)
    private void showLoadingFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment, LoadingFragment.TAG)
                .commit();
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
            askForWhiteListingIfApplicable();
            replaceFragmentsWithMap();
            configureMap();
            initAR();
            showToastMessage(getString(R.string.pointr_started_successfully));
        });
    }

    protected void configureMap() {
        // configure map here. 3rd parties can override here.
    }

    @TargetApi(23)
    private void askForWhiteListingIfApplicable() {
        boolean askForPermission = false;

        ConfigurationManager configManager = Pointr.getPointr().getConfigurationManager();
        if (configManager != null) {
            CoreConfiguration currentConfiguration = configManager.getCurrentConfiguration();
            if (currentConfiguration != null) {
                GeofenceManagerConfiguration geofenceManagerConfiguration = currentConfiguration.getGeofenceManagerConfig();
                if (geofenceManagerConfiguration != null) {
                    askForPermission = currentConfiguration.getGeofenceManagerConfig().getShouldAskForWhitelistPermission();
                }
            }
        }

        if (askForPermission) {
            showWhiteListingPermissionMessage();
        } else {
            STLog.v(TAG,"Ignoring battery optimizations is not enabled.");
        }
    }

    @TargetApi(23)
    private void showWhiteListingPermissionMessage() {
        String packageName = getPackageName();
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);

        DialogInterface.OnClickListener listener = (dialog, which) -> {
            Intent intent = new Intent();
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivityForResult(intent, REQUEST_CODE_ASK_WHITELISTING_PERMISSION);
            }
        };

        if (!pm.isIgnoringBatteryOptimizations(packageName) && !hasAskedWhitelistingPermissionBefore()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.whitelisting_permission_rationale)
                    .setPositiveButton(android.R.string.ok, listener)
                    .create()
                    .show();
        }
    }

    private boolean hasAskedWhitelistingPermissionBefore() {
        if (storage == null) {
            storage = Injector.findObjectForClass(Storage.class);
        }
        return storage != null && storage.getAskedWhitelistingPermissionBeforeAndDenied();
    }

    private void setUpRouteScreen() {
        routeScreenFragment = RouteScreenFragment.newInstance();
        routeScreenFragment.addListener(this);
    }

    private void assignRouteScreenOnClickListeners() {
        PathManager pathManager = pointr.getPathManager();
        containerFragment.getNavigationFooter().onStartPathfindingClicked(v -> {
            if (currentContainerFragmentState == ContainerFragmentState.PoiSelected) {
                containerFragment.getNavigationFooter().setCalculating(true);
                AsyncTask.execute(() -> {
                    Path calculatedPath = pathManager.calculatePath();
                    if (calculatedPath == null || !containerFragment.getNavigationFooter().getCalculating()) {
                        // either path could not be calculated or the user selected another poi
                        runOnUiThread(() -> {
                            containerFragment.getNavigationFooter().setCalculating(false);
                            if (calculatedPath == null) {
                                Toast.makeText(this, R.string.route_failed_path_calculation, Toast.LENGTH_LONG).show();
                            }
                            containerFragment.transitStateTo(ContainerFragmentState.Search);
                            containerFragment.getMap().getMapModeCoordinator().setMapMode(MapMode.Tracking);
                            containerFragment.abortPathfinding();
                        });
                    } else {
                        if (routeScreenFragment == null) {
                            setUpRouteScreen();
                        }
                        if (!routeScreenFragment.isAdded()) {
                            STLog.v(TAG,"Route screen was not added, adding....");
                            runOnUiThread(() -> {
                                pathManager.abortPathFinding();
                                getSupportFragmentManager()
                                        .beginTransaction()
                                        .setCustomAnimations(R.anim.grow_bottom, R.anim.leave_bottom)
                                        .add(R.id.fragment_container, routeScreenFragment, RouteScreenFragment.TAG)
                                        .commitAllowingStateLoss();
                                routeScreenFragment.setPath(calculatedPath);
                                routeScreenFragment.setPathFindingActive(false);
                                containerFragment.getNavigationFooter().setCalculating(false);
                                routeScreenFragment.setUserVisibleHint(true);
                                containerFragment.setUserVisibleHint(false);
                            });
                        } else {
                            runOnUiThread(() -> {
                                pathManager.abortPathFinding();
                                routeScreenFragment.setPath(calculatedPath);
                                routeScreenFragment.setPathFindingActiveAndAdapt(false);
                                getSupportFragmentManager()
                                        .beginTransaction()
                                        .setCustomAnimations(R.anim.grow_bottom, R.anim.leave_bottom)
                                        .show(routeScreenFragment)
                                        .commitAllowingStateLoss();
                                containerFragment.getNavigationFooter().setCalculating(false);
                                routeScreenFragment.setUserVisibleHint(true);
                                containerFragment.setUserVisibleHint(false);
                            });
                        }

                    }
                });

            } else if (currentContainerFragmentState == ContainerFragmentState.PathfindingHeaderAndFooter ||
                    currentContainerFragmentState == ContainerFragmentState.PathfindingHeader) {
                // end button hit
                onButtonClickedForRouteAction(RouteScreenFragment.RouteScreenAction.cancelRoute);
            }
        });
        // assign 'cancel' button callbacks after poi selection, revert back to search state
        containerFragment.getNavigationFooter().onCancelClicked(v -> {
            runOnUiThread(() -> {
                containerFragment.getNavigationFooter().setCalculating(false);
                containerFragment.transitStateTo(ContainerFragmentState.Search);
                containerFragment.getMap().getMapModeCoordinator().setMapMode(MapMode.Free);
                pathManager.abortPathFinding();
            });
        });

        // replace containerFragment with route fragment if the header is clicked
        containerFragment.getTurnByTurnHeader().setOnClickListener(v -> {
            if (routeScreenFragment == null) {
                setUpRouteScreen();
            }
            if (!routeScreenFragment.isAdded()) {
                runOnUiThread(() -> {
                    Path path = pathManager.getCurrentPath();
                    getSupportFragmentManager()
                            .beginTransaction()
                            .setCustomAnimations(R.anim.grow_bottom, R.anim.leave_bottom)
                            .add(R.id.fragment_container, routeScreenFragment, RouteScreenFragment.TAG)
                            .commitAllowingStateLoss();
                    routeScreenFragment.setPathFindingActive(true);
                    routeScreenFragment.setPath(path);
                });
            } else {
                runOnUiThread(() -> {
                    Path path = pathManager.getCurrentPath();
                    routeScreenFragment.setPath(path);
                    routeScreenFragment.setPathFindingActiveAndAdapt(true);
                    getSupportFragmentManager()
                            .beginTransaction()
                            .setCustomAnimations(R.anim.grow_bottom, R.anim.leave_bottom)
                            .show(routeScreenFragment)
                            .commitAllowingStateLoss();
                });
            }
        });
    }

    private void initAR() {
        // Initialise AR if AugmentedRealityEnabled in user configuration
        Pointr pointr = Pointr.getPointr();
        if (pointr == null) {
            STLog.e(TAG,"Pointr is null, cannot initialise AR");
            return;
        }
        ConfigurationManager manager = pointr.getConfigurationManager();
        if (manager != null) {
            CoreConfiguration config = manager.getCurrentConfiguration();
            if (config != null) {
                UserInterfaceConfiguration userConfig = config.getUserInterfaceConfiguration();
                if (userConfig != null) {
                    boolean isAREnabled = userConfig.getAugmentedRealityEnabled();
                    if (isAREnabled) {
                        arController = new ARController(this);
                        arController.addARStateListener(this);
                        STLog.v(TAG,"AR is enabled");
                    } else {
                        STLog.v(TAG,"isAugmentedRealityEnabled is false - AR is disabled");
                    }
                } else {
                    STLog.v(TAG,"UserInterfaceConfiguration is null - AR is disabled");
                }
            } else {
                STLog.v(TAG,"CurrentConfiguration is null - AR is disabled");
            }
        } else {
            STLog.v(TAG,"ConfigurationManager is null - AR is disabled");
        }
    }

    private void replaceFragmentsWithMap() {
        containerFragment = getContainerFragment();
        containerFragment.setStateChangeListener(this);
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.grow, R.anim.grow)
                .replace(R.id.fragment_container, containerFragment, BaseContainerFragment.TAG)
                .commitAllowingStateLoss();
    }

    protected BaseContainerFragment getContainerFragment() {
        return BaseContainerFragment.newInstance();
    }

    /**
     * Flattens the given message list line by line
     *
     * @param messages list to get flattened
     * @return Single string with messages lined
     */
    protected String flattenMessages(List<? extends Message> messages) {
        StringBuilder sb = new StringBuilder(messages.size() * 30);
        for (int i = 0; i < messages.size(); i++) {
            sb.append(messages.get(i).getMessage());
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public void onARStateChanged(ARController.ARState state) {
        STLog.v(TAG,"onStateChanged: " + state.toString());
        // Change from map to AR
        if (state == ARController.ARState.READY && containerFragment != null &&
                containerFragment.getUserVisibleHint() && isContainerFragmentStateAvailableForAR) {
            if (arFragment == null) {
                arFragment = arController.getArFragment();
                getSupportFragmentManager()
                        .beginTransaction()
                        .hide(containerFragment)
                        .add(R.id.fragment_container, arFragment, ARFragment.TAG)
                        .commitAllowingStateLoss();
            } else {
                getSupportFragmentManager()
                        .beginTransaction()
                        .hide(containerFragment)
                        .show(arFragment)
                        .commitAllowingStateLoss();
            }
            arFragment.setUserVisibleHint(true);
            containerFragment.setUserVisibleHint(false);
        }
        // Change from AR to map
        else if (state == ARController.ARState.NOT_READY && arFragment != null && arFragment.getUserVisibleHint()) {
            if (containerFragment == null) {
                containerFragment = getContainerFragment();
                containerFragment.setStateChangeListener(this);

                getSupportFragmentManager()
                        .beginTransaction()
                        .hide(arFragment)
                        .add(R.id.fragment_container, containerFragment, BaseContainerFragment.TAG)
                        .commitAllowingStateLoss();
            } else {
                getSupportFragmentManager()
                        .beginTransaction()
                        .hide(arFragment)
                        .show(containerFragment)
                        .commitAllowingStateLoss();
            }
            containerFragment.setUserVisibleHint(true);
            arFragment.setUserVisibleHint(false);
        }
    }

    @Override
    public void onDisplayStateChanged(ContainerFragmentState state) {
        ContainerFragmentState previousContainerFragmentState = currentContainerFragmentState;
        currentContainerFragmentState = state;

        // if map state is poi selected and the on click listeners for navigation are not assigned, assign them
        if ((currentContainerFragmentState == ContainerFragmentState.PoiSelected ||
                currentContainerFragmentState == ContainerFragmentState.PathfindingHeaderAndFooter) &&
                !isRouteOnClickListenerAssigned) {
            assignRouteScreenOnClickListeners();
            isRouteOnClickListenerAssigned = true;
        }
        if (arController != null) {
            // If the user has not clicked start button in navigation footer, do not try to switch AR
            if (currentContainerFragmentState == ContainerFragmentState.PathfindingHeader ||
                    currentContainerFragmentState == ContainerFragmentState.PathfindingHeaderAndFooter ||
                    (currentContainerFragmentState == ContainerFragmentState.Map &&
                            previousContainerFragmentState == ContainerFragmentState.PathfindingHeaderAndFooter)) {
                isContainerFragmentStateAvailableForAR = true;
                arController.start();
            } else {
                isContainerFragmentStateAvailableForAR = false;
                arController.stop();
            }
        }
    }

    @Override
    public void onButtonClickedForRouteAction(RouteScreenFragment.RouteScreenAction action) {
        PoiManager poiManager = pointr.getPoiManager();
        if (poiManager == null) {
            STLog.e(TAG,"could not get Poi Manager, cannot perform routing");
            return;
        }
        if (routeScreenFragment != null) {
            // This needs to be called if setStartPoint(name, image) is called once, to reset it
            // to default value which is current Position
            routeScreenFragment.setStartPoint(null, null);
        }
        if (action == RouteScreenFragment.RouteScreenAction.startPathfinding) {
            if (containerFragment == null) {
                STLog.v(TAG,"map fragment is null, adding reference");
                runOnUiThread(() -> {
                    containerFragment = getContainerFragment();
                    containerFragment.setStateChangeListener(this);
                    getSupportFragmentManager()
                            .beginTransaction()
                            .hide(routeScreenFragment)
                            .add(R.id.fragment_container, containerFragment, BaseContainerFragment.TAG)
                            .commitAllowingStateLoss();
                    PoiContainer selectedPoi = poiManager.getSelectedPoi();
                    if (selectedPoi != null) {
                        containerFragment.startPathfinding(selectedPoi);
                        containerFragment.transitStateTo(ContainerFragmentState.PathfindingHeaderAndFooter);
                        containerFragment.getMap().getMapModeCoordinator().setMapMode(
                                MapMode.PathTracking);  // Any preferred map mode for --pathfinding-- can be set from here
                    } else {
                        STLog.e(TAG,"Cannot start pathfinding - selected Poi is null");
                    }
                });
            } else {
                STLog.v(TAG,"map fragment is not null");
                runOnUiThread(() -> {
                    containerFragment = getContainerFragment();
                    getSupportFragmentManager()
                            .beginTransaction()
                            .hide(routeScreenFragment)
                            .show(containerFragment)
                            .commitAllowingStateLoss();
                    PoiContainer selectedPoi = poiManager.getSelectedPoi();
                    if (selectedPoi != null) {
                        containerFragment.startPathfinding(selectedPoi);
                        containerFragment.transitStateTo(ContainerFragmentState.PathfindingHeaderAndFooter);
                        containerFragment.getMap().getMapModeCoordinator().setMapMode(
                                MapMode.PathTracking);  // Any preferred map mode for --pathfinding-- can be set from here
                    } else {
                        STLog.e(TAG,"Cannot start pathfinding - selected Poi is null");
                    }
                });
            }
        } else if (action == RouteScreenFragment.RouteScreenAction.cancelRoute) {
            runOnUiThread(() -> {
                getSupportFragmentManager()
                        .beginTransaction()
                        .hide(routeScreenFragment)
                        .show(containerFragment)
                        .commitAllowingStateLoss();
                containerFragment.transitStateTo(ContainerFragmentState.Search);
                containerFragment.getMap().getMapModeCoordinator().setMapMode(MapMode.Tracking);
                containerFragment.abortPathfinding();
            });
        } else if (action == RouteScreenFragment.RouteScreenAction.hideRoute) {
            // continue from the last map state
            runOnUiThread(() -> {
                getSupportFragmentManager()
                        .beginTransaction()
                        .hide(routeScreenFragment)
                        .show(containerFragment)
                        .commitAllowingStateLoss();
            });
        }
    }
    // TODO - implement for custom pois not categorized by type
    public void decideOnIconImageBasedOnName(String name) {
    }

    @Override
    public void onBackPressed() {
        // Clean up state
        arFragment = null;
        if (routeScreenFragment != null) {
            routeScreenFragment.removeListener(this);
            routeScreenFragment = null;
        }
        // These calls are necessary since when the super onBackButtonPressed is called, the activity will be destroyed
        // and the UI states of the fragments associated with it will not be kept In order to have a synchronous state
        // after back button, we should reset the maps UI's when back button is pressed
        if (currentContainerFragmentState == ContainerFragmentState.PoiSelected) {
            // if back button is pressed when map is on state 'poiSelected', cancel the poi selection, reset map state
            // to search and then call the super
            runOnUiThread(() -> containerFragment.transitStateTo(ContainerFragmentState.Search));
        } else if (currentContainerFragmentState == ContainerFragmentState.PathfindingHeader ||
                currentContainerFragmentState == ContainerFragmentState.PathfindingHeaderAndFooter) {
            // if back button is pressed when map is either on state 'pathfindingHeader' or 'pathfindingHeaderAndFooter'
            // signifying that currently there is an active path,
            // change map state to search, change map mode to tracking and abort current pathfinding before using the
            // super back button pressed
            runOnUiThread(() -> {
                containerFragment.transitStateTo(ContainerFragmentState.Search);
                containerFragment.getMap().getMapModeCoordinator().setMapMode(MapMode.Tracking);
                containerFragment.abortPathfinding();
            });
        }
        super.onBackPressed();
    }


    private void initView(){
        Utils.init(getApplication());
        findViewById(R.id.btn_go_navgation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPointrPath();

            }
        });

        findViewById(R.id.btn_go_scane).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(getContainerFragment().getMap().getCurrentLocation() == null ||
                        getContainerFragment().getCurrentPosition() == null){
                    Toast.makeText(BasePointrMapActivity.this,"bluetooth location is failed",Toast.LENGTH_LONG).show();
                    return;
                }

                float curLocationX = getContainerFragment().getCurrentPosition().getX();
                float curLocationY = getContainerFragment().getCurrentPosition().getY();
                int curLevel = getContainerFragment().getCurrentPosition().getLevel();
                int venueId = getContainerFragment().getMap().getCurrentLocation().getVenueId();
                int facilityId = getContainerFragment().getMap().getCurrentLocation().getFacilityId();
                ARPathEntity arPathEntity = new ARPathEntity();
                arPathEntity.setCurLeve(curLevel);
                arPathEntity.setVenueId(venueId);
                arPathEntity.setFacilityId(facilityId);
                arPathEntity.setCurLocationX(curLocationX);
                arPathEntity.setCurLocationY(curLocationY);
                ARUtils.switchPage2ARScan(BasePointrMapActivity.this,arPathEntity);
            }
        });

        findViewById(R.id.btn_buile_path).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ARPathEntity arPathEntity = new ARPathEntity();
                arPathEntity.setPathString(getJSONStr());
                arPathEntity.setVenueId(1);
                arPathEntity.setFacilityId(1);
                arPathEntity.setCurLeve(4);
                //PointrConfig.curLocationX = 0.314418f;
                //PointrConfig.curLocationY = 0.364939f;
                arPathEntity.setCurLocationX(0.314418f);
                arPathEntity.setCurLocationY(0.364939f);
                Poi poi = new Poi("327",0.546074f,0.561281f,-999.0,-999.0,1,1,1,"Lift","Lift",
                        null,null, null,null,"Lift",null,true,null,null,null,null,null);
                //PointrConfig.selectPoi = new PoiContainer(poi);
                ARUtils.switchPage2ARNavgation(BasePointrMapActivity.this,arPathEntity);
            }
        });
        findViewById(R.id.btn_abort_path).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endNavgation();
            }
        });

    }
    ARPathEntity arPathEntity = null;
    private void getPointrPath(){
        if(arPathEntity != null && PointrConfig.mPath != null){
            goARPage(arPathEntity);
            return;
        }
        if(getContainerFragment().getMap().getCurrentLocation() == null ||
                getContainerFragment().getCurrentPosition() == null){
            Toast.makeText(this,"bluetooth location is failed",Toast.LENGTH_LONG).show();
            return;
        }
        if(pointr == null) return;
        if(containerFragment.getSelectPoi() == null){
            Toast.makeText(this,"pick destination poi first",Toast.LENGTH_LONG).show();
            return;
        }
        PositionManagerImpl positionManager = (PositionManagerImpl) pointr.getPositionManager();
        final Position position = getContainerFragment().getCurrentPosition();
        // Poi poi = PointrConfig.selectPoi.getPoiList().get(0);
        // Location location = new Location(position.getX(),position.getY(),position.getLevel(),0,0);
        //Location endLocation = poi.getLocation();
        PathManager pathManager = pointr.getPathManager();

        AsyncTask.execute(() -> {
            pointr.getPoiManager().setSelectedPoi(containerFragment.getSelectPoi());
            Path calculatedPath = pathManager.calculatePath();
            if(calculatedPath == null){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BasePointrMapActivity.this,"calculate Path failed",Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            PointrConfig.mPath = calculatedPath;
            int curLevel = getContainerFragment().getCurrentPosition().getLevel();
            int venueId = getContainerFragment().getMap().getCurrentLocation().getVenueId();
            int facilityId = getContainerFragment().getMap().getCurrentLocation().getFacilityId();;
            float curLocationX = getContainerFragment().getCurrentPosition().getX();
            float curLocationY = getContainerFragment().getCurrentPosition().getY();
            if(arPathEntity == null){
                arPathEntity = new ARPathEntity();
            }
            arPathEntity.setCurLeve(curLevel);
            arPathEntity.setVenueId(venueId);
            arPathEntity.setFacilityId(facilityId);
            arPathEntity.setCurLocationX(curLocationX);
            arPathEntity.setCurLocationY(curLocationY);
            arPathEntity.setDestinationX(containerFragment.getSelectPoi().getPoiList().get(0).getX());
            arPathEntity.setDestinationY(containerFragment.getSelectPoi().getPoiList().get(0).getY());
            arPathEntity.setDestnination(containerFragment.getSelectPoi().getPoiList().get(0).getName());
            if(calculatedPath == null){
                containerFragment.abortPathfinding();
                return;
            }

            pathManager.abortPathFinding();


            PoiManager poiManager = pointr.getPoiManager();
            if (poiManager == null) {
                STLog.e(TAG,"could not get Poi Manager, cannot perform routing");
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
                        goARPage(arPathEntity);
                    } else {
                        System.out.println("379---------:selected Poi is null");
                    }

                }
            });

        });

    }
    private void goARPage(ARPathEntity arPathEntity){
        ARUtils.switchPage2ARNavgation(this,arPathEntity);
    }

    private MainRec mainRec;
    public void registerBroadcast() {
        if (mainRec == null) {
            mainRec = new MainRec();
            IntentFilter filter = new IntentFilter();
            filter.addAction(PointrConfig.END_NAVGATION);
            registerReceiver(mainRec, filter);
        }
    }
    private class MainRec extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), PointrConfig.END_NAVGATION)) {
                endNavgation();
            }
        }
    }

    private void endNavgation(){
        runOnUiThread(() -> {
            containerFragment.getMap().getMapModeCoordinator().setMapMode(MapMode.Tracking);
            containerFragment.abortPathfinding();
        });

        if(arPathEntity != null){
            arPathEntity = null;
        }

        if(PointrConfig.mPath != null){
            PointrConfig.mPath = null;
        }
    }

    /**
     * abort path find and destory res
     */
    private void cancelNavgation(){
        endNavgation();
    }

    private String getJSONStr() {

        String test = "{\n" +
                "    \"nodelist\":[\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":584.7125118196639,\n" +
                "            \"_z\":421.9173916375117,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":583.2117425830113,\n" +
                "            \"_z\":422.1504710011894,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":581.7109733463589,\n" +
                "            \"_z\":422.3835503648671,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":580.2102041097064,\n" +
                "            \"_z\":422.6166297285448,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":578.709434873054,\n" +
                "            \"_z\":422.8497090922225,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":577.2086656364014,\n" +
                "            \"_z\":423.08278845590024,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":575.707896399749,\n" +
                "            \"_z\":423.31586781957793,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":574.2071271630965,\n" +
                "            \"_z\":423.5489471832557,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":572.7063579264441,\n" +
                "            \"_z\":423.78202654693337,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":571.2055886897915,\n" +
                "            \"_z\":424.01510591061106,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":569.7048194531391,\n" +
                "            \"_z\":424.2481852742888,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":568.2040502164866,\n" +
                "            \"_z\":424.4812646379665,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":566.7032809798342,\n" +
                "            \"_z\":424.71434400164424,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":565.2025117431816,\n" +
                "            \"_z\":424.9474233653219,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":563.7017425065292,\n" +
                "            \"_z\":425.1805027289996,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":562.2009732698767,\n" +
                "            \"_z\":425.41358209267736,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":560.7002040332243,\n" +
                "            \"_z\":425.64666145635505,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":559.1994347965717,\n" +
                "            \"_z\":425.8797408200328,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":557.6986655599193,\n" +
                "            \"_z\":426.1128201837105,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":556.1978963232668,\n" +
                "            \"_z\":426.34589954738817,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":554.6971270866144,\n" +
                "            \"_z\":426.5789789110659,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":553.1963578499619,\n" +
                "            \"_z\":426.8120582747436,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"Please follow the line\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":551.6955886133094,\n" +
                "            \"_z\":427.04513763842135,\n" +
                "            \"_y\":0\n" +
                "        },\n" +
                "        {\n" +
                "            \"_des\":\"The End\",\n" +
                "            \"_directionString\":\"forward\",\n" +
                "            \"_x\":550.1948193766569,\n" +
                "            \"_z\":427.27821700209904,\n" +
                "            \"_y\":0\n" +
                "        }\n" +
                "    ]\n" +
                "}\n" +
                "\n";

        return test;
    }
}
