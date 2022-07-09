package com.graphhopper.navigation.example;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Telemetry;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener {

    private static final int CAMERA_ANIMATION_DURATION = 1000;
    private static final int DEFAULT_CAMERA_ZOOM = 14;

    private LocationLayerPlugin locationLayer;
    private NavigationMapRoute mapRoute;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;

    @BindView(R.id.mapView)
    MapView mapView;
    @BindView(R.id.to_search_btn)
    Button to_search_btn;
    @BindView(R.id.to_navigate_btn)
    Button to_navigate_btn;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Mapbox.getInstance(this.getApplicationContext(), getString(R.string.mapbox_access_token));
        Telemetry.disableOnUserRequest();
        ButterKnife.bind(this);
        mapView.setStyleUrl(Style.MAPBOX_STREETS);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        getSupportActionBar().hide();

        //控制地点搜索和导航按钮图标大小
        Drawable searchIcon = ContextCompat.getDrawable(this.getApplicationContext(),R.drawable.location_search_icon);
        searchIcon.setBounds(30,0,130,100);
        to_search_btn.setCompoundDrawables(searchIcon,null,null,null);
        Drawable navigateIcon = ContextCompat.getDrawable(this.getApplicationContext(),R.drawable.navigation_icon);
        navigateIcon.setBounds(30,0,130,100);
        to_navigate_btn.setCompoundDrawables(navigateIcon,null,null,null);

        //地点搜索按钮的点击事件
        to_search_btn.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                startActivityForResult(new Intent(MainActivity.this, NavigationLauncherActivity.class), 1);
            }
        });
        //导航按钮的点击事件
        to_navigate_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(new Intent(MainActivity.this, Tab.class), 1);
            }
        });
    }


    @SuppressWarnings({"MissingPermission"})
    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        if (locationLayer != null) {
            locationLayer.onStart();
        }
    }

    @SuppressWarnings({"MissingPermission"})
    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // getIntent() should always return the most recent
        setIntent(intent);
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        if (locationLayer != null) {
            locationLayer.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }


    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
//        this.mapboxMap.getUiSettings().setAttributionDialogManager(new GHAttributionDialogManager(this.mapView.getContext(), this.mapboxMap));

        // Check for location permission
        permissionsManager = new PermissionsManager(this);
        if (!PermissionsManager.areLocationPermissionsGranted(this)) {
            permissionsManager.requestLocationPermissions(this);
        } else {
            initLocationLayer();
        }
    }

    @SuppressWarnings({"MissingPermission"})
    private void initLocationLayer() {
        locationLayer = new LocationLayerPlugin(mapView, mapboxMap);
        locationLayer.setRenderMode(RenderMode.COMPASS);
        mapboxMap.addMarker(new MarkerOptions()
                .position(new LatLng(DefaultConfig.defaultLocation.latitude(), DefaultConfig.defaultLocation.longitude()))
                .icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.currlocation32_icon)));
        animateCamera(new LatLng(DefaultConfig.defaultLocation.latitude(), DefaultConfig.defaultLocation.longitude()));
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownLocation() {
        if (locationLayer != null) {
            return locationLayer.getLastKnownLocation();
        }
        return null;
    }

    private void animateCamera(LatLng point) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, DEFAULT_CAMERA_ZOOM), CAMERA_ANIMATION_DURATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "This app needs location permissions to work properly.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            initLocationLayer();
        } else {
            Toast.makeText(this, "You didn't grant location permissions.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
