package com.graphhopper.navigation.example;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.graphhopper.directions.api.client.model.GeocodingLocation;
import com.graphhopper.directions.api.client.model.GeocodingPoint;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.core.constants.Constants;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.exceptions.InvalidLatLngBoundsException;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Telemetry;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.ui.v5.route.OnRouteSelectionChangeListener;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.utils.LocaleUtils;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Response;


public class NavigationLauncherActivity extends AppCompatActivity implements OnMapReadyCallback,
        OnRouteSelectionChangeListener,
        FetchSolutionTaskCallbackInterface,
        FetchGeocodingTaskCallbackInterface,
        PermissionsListener,View.OnClickListener {

    private static final int CAMERA_ANIMATION_DURATION = 1000;
    private static final int DEFAULT_CAMERA_ZOOM = 16;
    private static final int CHANGE_SETTING_REQUEST_CODE = 1;
    // If you change the first start dialog (help message), increase this number, all users will be shown the message again
    private static final int FIRST_START_DIALOG_VERSION = 1;
    private static final int FIRST_NAVIGATION_DIALOG_VERSION = 1;
    private static final String TAG = NavigationLauncherActivity.class.getSimpleName();

    private LocationLayerPlugin locationLayer;
    private NavigationMapRoute mapRoute;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;

    @BindView(R.id.mapView)
    MapView mapView;
    @BindView(R.id.loading)
    ProgressBar loading;
    @BindView(R.id.speech_btn)
    Button speech_btn;
    @BindView(R.id.navigate)
    Button navigate_btn;
    @BindView(R.id.search_location)
    EditText search;

    private Marker currentMarker;
    private List<Marker> markers;
    private List<Point> waypoints = new ArrayList<>();
    private DirectionsRoute route;
    private LocaleUtils localeUtils;


    boolean isPOISearch = false;
    private List<Marker> POImarkers;

    private List<List<String>> geocodingInfo;      //?????????????????????POI?????????name street postcode city lat lon

    private final int[] padding = new int[]{50, 50, 50, 50};

    private String currentJobId = "";
    private String currentVehicleId = "";
    private String currentGeocodingInput = "";

    private Point defaultLocation = DefaultConfig.defaultLocation;
    private int POISearchSize = 0;
    private Dialog dialog;
    private int POIindex = 0;          //????????????POI????????????????????????marker??????
    private int currLocationIndex = 0;    //?????????????????????

    // ???HashMap??????????????????
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation_launcher);
        Mapbox.getInstance(this.getApplicationContext(), getString(R.string.mapbox_access_token));
        Telemetry.disableOnUserRequest();
        ButterKnife.bind(this);
//        mapView.setStyleUrl(getString(R.string.map_view_styleUrl));
        mapView.setStyleUrl(Style.MAPBOX_STREETS);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        localeUtils = new LocaleUtils();
        getSupportActionBar().hide();
        Context context = this.getApplicationContext();
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @SuppressLint("WrongConstant")
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                clearGeocodingResults();
                clearRoute();
                clearPOIMarkers();
                POIindex = 0;
                if(i == EditorInfo.IME_ACTION_SEARCH){
                    String currLat = String.valueOf(defaultLocation.latitude());
                    String currLon = String.valueOf(defaultLocation.longitude());
//                    String currLat = "40.6701392",currLon = "-73.9857019";
                    judgePoiOrLocation(search.getText().toString(),currLat,currLon);     //???????????????????????????POI?????????????????????????????????
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    assert imm != null;
                    imm.hideSoftInputFromWindow(search.getWindowToken(),0);
                    return false;
                }
                return false;
            }
        });
        navigate_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchNavigationWithRoute();
            }
        });
        speech_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startSpeechDialog();
            }
        });
        initSpeech();
    }

    // ??????????????????????????????
    private void initSpeech(){
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=5f295d51");
    }

    private void startSpeechDialog() {
        //1. ??????RecognizerDialog??????
        RecognizerDialog mDialog = new RecognizerDialog(this, new MyInitListener()) ;
        //2. ??????accent??? language?????????
        mDialog.setParameter(SpeechConstant. LANGUAGE, "en_us" );
        mDialog.setUILanguage(Locale.US);
        // ?????????UI???????????????????????????????????????????????????????????????????????? onResult??????????????????????????????
        // ??????
        // mDialog.setParameter("asr_sch", "1");
        // mDialog.setParameter("nlp_version", "2.0");
        //3.??????????????????
        mDialog.setListener( new MyRecognizerDialogListener()) ;
        //4. ??????dialog?????????????????????
        mDialog.show() ;
        TextView txt = (TextView)mDialog.getWindow().getDecorView().findViewWithTag("textlink");
        txt.setText("");
    }

    class MyRecognizerDialogListener implements RecognizerDialogListener {

        /**
         * @param results
         * @param isLast  ???????????????
         */
        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String result = results.getResultString(); //????????????
//            showTip(result) ;
            System. out.println(" ??????????????? :" + result);

            String text = com.graphhopper.navigation.example.JsonParser.parseIatResult(result) ;//???????????????
            System. out.println(" ???????????? :" + text);

            String sn = null;
            // ??????json???????????? sn??????
            try {
                JSONObject resultJson = new JSONObject(results.getResultString()) ;
                sn = resultJson.optString("sn" );
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mIatResults .put(sn, text) ;//??????????????????????????????

            StringBuffer resultBuffer = new StringBuffer();
            for (String key : mIatResults.keySet()) {
                resultBuffer.append(mIatResults .get(key));
            }

//            search.setText("bar");
            search.setText("New york university");

//            search.setText(resultBuffer.toString());// ????????????????????????

            search .setSelection(search.length()) ;//?????????????????????
            // ???????????????????????????????????????
            String currLat = String.valueOf(defaultLocation.latitude());
            String currLon = String.valueOf(defaultLocation.longitude());
            judgePoiOrLocation(search.getText().toString(),currLat,currLon);     //???????????????????????????POI?????????????????????????????????
        }

        @Override
        public void onError(SpeechError speechError) {

        }
    }

    class MyInitListener implements InitListener {

        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                showTip("??????????????? ");
            }

        }
    }

    /**
     * ????????????
     */
    private void startSpeech() {
        //1. ??????SpeechRecognizer??????????????????????????? ?????????????????? InitListener
        SpeechRecognizer mIat = SpeechRecognizer.createRecognizer( this, null); //???????????????
        //2. ?????????????????????????????? MSC Reference Manual??? SpeechConstant???
        mIat.setParameter(SpeechConstant. DOMAIN, "iat" );// ???????????????????????? iat (??????)
        mIat.setParameter(SpeechConstant. LANGUAGE, "en_us" );// ????????????
        mIat.setParameter(SpeechConstant.VAD_EOS,"5000");
        mIat.setParameter(SpeechConstant.ASR_PTT,"0");
        //3. ????????????
        mIat.startListening( mRecoListener);
    }


    // ???????????????
    private RecognizerListener mRecoListener = new RecognizerListener() {
        // ???????????????????????? (??????Json ???????????????????????????????????? 13.1)???
//????????????????????????onResults???????????????????????????????????????????????????????????????????????????
//????????????Json?????????????????? Demo???JsonParser ??????
//isLast??????true ??????????????????
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.e (TAG, results.getResultString());
            System.out.println(results.getResultString()) ;
            showTip(results.getResultString()) ;
        }

        // ??????????????????????????????
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true)) ;
            // ?????????????????????
            Log. e(TAG, "error.getPlainDescription(true)==" + error.getPlainDescription(true ));
        }

        // ????????????
        public void onBeginOfSpeech() {
            showTip(" ???????????? ");
        }

        //volume ?????????0~30??? data????????????
        public void onVolumeChanged(int volume, byte[] data) {
            showTip(" ??????????????? ");
        }

        // ????????????
        public void onEndOfSpeech() {
            showTip(" ???????????? ");
        }

        // ???????????????
        public void onEvent(int eventType, int arg1 , int arg2, Bundle obj) {
        }
    };

    private void showTip(String data){
        Toast.makeText(this,data,Toast.LENGTH_SHORT).show();
    }

    public void judgePoiOrLocation(String str,String currLat,String currLon){
        String url = "http://" + DefaultConfig.IP + ":8000/POISearch";
//        String url = "http://222.20.76.82:8000/POISearch";
        OkHttpClient client = new OkHttpClient();
        FormEncodingBuilder builder = new FormEncodingBuilder();
        RequestBody requestBody = builder.add("city",DefaultConfig.city).add("searchString",str).add("currLat",currLat).add("currLon",currLon).build();
        final Request request = new Request.Builder().url(url).post(requestBody).build();
        showLoading();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                String  error = e.getMessage();
            }
            @Override
            public void onResponse(com.squareup.okhttp.Response response) throws IOException {
                if(response.isSuccessful()){
                    String responseText = response.body().string();
                    System.out.println(responseText);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if("false".equals(responseText)){
                                System.out.println("responseText" + responseText);
                                System.out.println("str=" + str);
                                doSearch(str);
                            }
                            else{
                                System.out.println("responseText" + responseText);
                                getPoiList(responseText);
                            }
                        }
                    });
                }
            }
        });
    }

    public void getPoiList(String responseText){       //????????????????????????json??????????????????List
        try{
            JSONObject jsonObject = new JSONObject(responseText);
            JSONArray jsonArray = jsonObject.getJSONArray("POI");
            System.out.println(jsonArray);
            List<List<Double>> POIs = new ArrayList<>(jsonArray.length());
            for(int i = 0; i < jsonArray.length(); i++){
                JSONObject currObject = jsonArray.getJSONObject(i);
                double lat = Double.parseDouble(currObject.getString("lat"));
                double lon = Double.parseDouble(currObject.getString("lon"));
                List<Double> currPOI = new ArrayList<>(2);
                currPOI.add(lat);
                currPOI.add(lon);
                POIs.add(currPOI);
            }
            setPOIMarkers(POIs);          //???POI markers??????????????????

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void doSearch(String searchStr){
        if (!("".equals(searchStr))) {
            currentGeocodingInput = searchStr;
            currentGeocodingInput = searchStr;
            showLoading();
            String point = null;
            LatLng pointLatLng = this.mapboxMap.getCameraPosition().target;
            if (pointLatLng != null)
                point = pointLatLng.getLatitude() + "," + pointLatLng.getLongitude();
            isPOISearch = false;
            new FetchGeocodingTask(this, getString(R.string.gh_key)).execute(new FetchGeocodingConfig(currentGeocodingInput, getLanguageFromSharedPreferences().getLanguage(), 5, false, point, "default"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHANGE_SETTING_REQUEST_CODE && resultCode == RESULT_OK) {
            boolean shouldRefetch = data.getBooleanExtra(NavigationViewSettingsActivity.UNIT_TYPE_CHANGED, false)
                    || data.getBooleanExtra(NavigationViewSettingsActivity.LANGUAGE_CHANGED, false)
                    || data.getBooleanExtra(NavigationViewSettingsActivity.PROFILE_CHANGED, false);
            if (waypoints.size() > 0 && shouldRefetch) {
                fetchRoute();
            }
        }
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
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // getIntent() should always return the most recent
        setIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null && "graphhopper.com".equals(data.getHost())) {
                if (data.getPath() != null) {
                    if (this.mapboxMap == null) {
                        //this happens when onResume is called at the initial start and we will call this method again in onMapReady
                        return;
                    }
                    if (data.getPath().contains("maps")) {
                        clearRoute();
                        //Open Map Url
                        setRouteProfileToSharedPreferences(data.getQueryParameter("vehicle"));

                        List<String> points = data.getQueryParameters("point");
                        for (String point : points) {
                            String[] pointArr = point.split(",");
                            addPointToRoute(Double.parseDouble(pointArr[0]), Double.parseDouble(pointArr[1]));
                        }

                        setStartFromLocationToSharedPreferences(false);
                        updateRouteAfterWaypointChange();
                    }
                    // https://graphhopper.com/api/1/vrp/solution/e7fb8a9b-e441-4ec2-a487-20788e591bb3?vehicle_id=1&key=[KEY]
                    if (data.getPath().contains("api/1/vrp/solution")) {
                        clearRoute();
                        //Open Vrp Url
                        List<String> pathSegments = data.getPathSegments();
                        fetchVrpSolution(pathSegments.get(pathSegments.size() - 1), data.getQueryParameter("vehicle_id"));
                    }
                }

            }
        }
    }

    private void showNavigationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.legal_title);
        builder.setMessage(Html.fromHtml("You are required to comply with applicable laws.<br/>When using mapping data, directions or other data from GraphHopper / OpenStreetMap, it is possible that the results differ from the actual situation. You should therefore act at your own discretion. Use of GraphHopper / OpenStreetMap is at your own risk. You are responsible for your own behavior and consequences at all times."));
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        builder.setPositiveButton(R.string.agree, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(getString(R.string.first_navigation_dialog_key), FIRST_NAVIGATION_DIALOG_VERSION);
                editor.apply();
                launchNavigationWithRoute();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void fetchVrpSolution(String jobId, String vehicleId) {
        currentJobId = jobId;
        currentVehicleId = vehicleId;

        showLoading();
        new FetchSolutionTask(this, getString(R.string.gh_key)).execute(new FetchSolutionConfig(currentJobId, currentVehicleId));
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

    private void clearRoute() {
        waypoints.clear();
        mapRoute.removeRoute();
        route = null;
        if (currentMarker != null) {
            mapboxMap.removeMarker(currentMarker);
            currentMarker = null;
        }
        //?????????????????????????????????????????????
        RelativeLayout routeInfoLayout = findViewById(R.id.routeInfoLayout);
        routeInfoLayout.setVisibility(View.INVISIBLE);
        Button navigate_btn = findViewById(R.id.navigate);
        navigate_btn.setEnabled(false);
    }

    private void clearGeocodingResults() {
        if(geocodingInfo != null)
            geocodingInfo.clear();
        if (markers != null) {
            for (Marker marker : markers) {
                this.mapboxMap.removeMarker(marker);
            }
            markers.clear();
        }
    }

    private void clearPOIMarkers(){
        if(geocodingInfo != null)
            geocodingInfo.clear();
        if (POImarkers != null) {
            for (Marker marker : POImarkers) {
                this.mapboxMap.removeMarker(marker);
            }
            POImarkers.clear();
        }
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        initMapRoute();
        // Check for location permission
        permissionsManager = new PermissionsManager(this);
        if (!PermissionsManager.areLocationPermissionsGranted(this)) {
            permissionsManager.requestLocationPermissions(this);
        } else {
            initLocationLayer();
        }

        handleIntent(getIntent());
    }

    private void addPointToRoute(double lat, double lng) {
        waypoints.add(Point.fromLngLat(lng, lat));
    }

    @Override
    public void onNewPrimaryRouteSelected(DirectionsRoute directionsRoute) {
        route = directionsRoute;
    }

    @SuppressWarnings({"MissingPermission"})
    private void initLocationLayer() {
        locationLayer = new LocationLayerPlugin(mapView, mapboxMap);
        locationLayer.setRenderMode(RenderMode.COMPASS);
        search.setText("");
        mapboxMap.addMarker(new MarkerOptions()
                .position(new LatLng(defaultLocation.latitude(),defaultLocation.longitude()))
                .icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.currlocation32_icon)));
        animateCamera(new LatLng(defaultLocation.latitude(),defaultLocation.longitude()));
    }

    private void initMapRoute() {
        mapRoute = new NavigationMapRoute(mapView, mapboxMap);
        mapRoute.setOnRouteSelectionChangeListener(this);
    }

    private void clearOtherMarkers(double lat,double lon){   //??????????????????????????????marker
        List<Marker> currMarkers;
        if(isPOISearch){
            currMarkers = POImarkers;
        }
        else{
            currMarkers = markers;
        }
        for (Marker geocodingMarker : currMarkers) {
            if (!(geocodingMarker.getPosition().getLatitude() == lat && geocodingMarker.getPosition().getLongitude() == lon)) {
                mapboxMap.removeMarker(geocodingMarker);
            }
        }
    }

    private void fetchRoute() {
        NavigationRoute.Builder builder = NavigationRoute.builder(this)
                .accessToken("pk." + getString(R.string.gh_key))
                .baseUrl(getString(R.string.base_url))
                .user("gh")
                .alternatives(true);

        if(waypoints.size() < 1){
            onError(R.string.error_not_enough_waypoints);
            return;
        }
        //???????????????????????????POI????????????????????????????????????????????????????????????????????????
        builder.origin(defaultLocation);
        builder.destination(waypoints.get(0));

//        for (int i = 0; i < waypoints.size(); i++) {
//            Point p = waypoints.get(i);
//            if (i == 0 && !startFromLocation) {
//                builder.origin(p);
//            } else if (i < waypoints.size() - 1) {
//                builder.addWaypoint(p);
//            } else {
//                builder.destination(p);
//            }
//        }
        showLoading();
        setFieldsFromSharedPreferences(builder);
        builder.build().getRoute(new SimplifiedCallback() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                if (validRouteResponse(response)) {
                    route = response.body().routes().get(0);
                    mapRoute.addRoutes(response.body().routes());
                    boundCameraToRoute();
                    showRouteInfo(currLocationIndex);
                } else {
                    Snackbar.make(mapView, R.string.error_calculating_route, Snackbar.LENGTH_LONG).show();
                }
                hideLoading();
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                super.onFailure(call, throwable);
                Snackbar.make(mapView, R.string.error_calculating_route, Snackbar.LENGTH_LONG).show();
                hideLoading();
            }
        });
    }


    private void showRouteInfo(int index){      //????????????????????????????????????
        TextView open_location_list = (TextView) findViewById(R.id.open_location_list);
        open_location_list.setVisibility(View.INVISIBLE);
        RelativeLayout routeInfoLayout = (RelativeLayout) findViewById(R.id.routeInfoLayout);
        routeInfoLayout.setVisibility(View.VISIBLE);
        TextView originText = (TextView) findViewById(R.id.routeOrigin);
        TextView destinationText = (TextView) findViewById(R.id.routeDestination);
        TextView timeText = (TextView) findViewById(R.id.routeTime);
        TextView distanceText = (TextView) findViewById(R.id.routeDistance);
        Button button = (Button) findViewById(R.id.navigate);
        button.setEnabled(true);
        originText.setText("current location");
        destinationText.setText(geocodingInfo.get(index).get(0));
        distanceText.setText(String.format("%.2f",(route.distance())/1000) + "km");
        timeText.setText(Double.toString(Math.round((route.duration())/60)) + "min");
    }

    public void routeNavigate(View view){   //????????????????????????
        launchNavigationWithRoute();
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownLocation() {
        if (locationLayer != null) {
            return locationLayer.getLastKnownLocation();
        }
        return null;
    }

    private void updateRouteAfterWaypointChange() {
        if (this.waypoints.isEmpty()) {
            hideLoading();
        } else {
            Point lastPoint = this.waypoints.get(this.waypoints.size() - 1);
            LatLng latLng = new LatLng(lastPoint.latitude(), lastPoint.longitude());
//            setCurrentMarkerPosition(latLng);
            if (this.waypoints.size() > 0) {
                fetchRoute();
            } else {
                hideLoading();
            }
        }
    }

    private void setFieldsFromSharedPreferences(NavigationRoute.Builder builder) {
        builder
                .language(getLanguageFromSharedPreferences())
                .voiceUnits(getUnitTypeFromSharedPreferences())
                .profile(getRouteProfileFromSharedPreferences());
    }

    private String getUnitTypeFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultUnitType = getString(R.string.default_unit_type);
        String unitType = sharedPreferences.getString(getString(R.string.unit_type_key), defaultUnitType);
        if (unitType.equals(defaultUnitType)) {
            unitType = localeUtils.getUnitTypeForDeviceLocale(this);
        }

        return unitType;
    }

    private Locale getLanguageFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultLanguage = getString(R.string.default_locale);
        String language = sharedPreferences.getString(getString(R.string.language_key), defaultLanguage);
        if (language.equals(defaultLanguage)) {
            return localeUtils.inferDeviceLocale(this);
        } else {
            return new Locale(language);
        }
    }

    private boolean getShouldSimulateRouteFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean(getString(R.string.simulate_route_key), false);
    }

    // ?????????????????????????????????
    private boolean getStartFromLocationFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean(getString(R.string.start_from_location_key), true);
    }

    private void setStartFromLocationToSharedPreferences(boolean setStartFromLocation) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(getString(R.string.start_from_location_key), setStartFromLocation);
        editor.apply();
    }

    private void setRouteProfileToSharedPreferences(String ghVehicle) {
        if (ghVehicle == null)
            return;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String routeProfile;
        switch (ghVehicle) {
            case "foot":
            case "hike":
                routeProfile = "walking";
                break;
            case "bike":
            case "mtb":
            case "racingbike":
                routeProfile = "cycling";
                break;
            default:
                routeProfile = "driving";
        }
        editor.putString(getString(R.string.route_profile_key), routeProfile);
        editor.apply();
    }

    private String getRouteProfileFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString(
                getString(R.string.route_profile_key), DirectionsCriteria.PROFILE_DRIVING
        );
    }

    private void launchNavigationWithRoute() {
        if (route == null) {
            Snackbar.make(mapView, R.string.error_route_not_available, Snackbar.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getInt(getString(R.string.first_navigation_dialog_key), -1) < FIRST_NAVIGATION_DIALOG_VERSION) {
            showNavigationDialog();
            return;
        }

        Location lastKnownLocation = getLastKnownLocation();
        if (lastKnownLocation != null && waypoints.size() > 1) {
            float[] distance = new float[1];
            Location.distanceBetween(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), waypoints.get(0).latitude(), waypoints.get(0).longitude(), distance);

            //Ask the user if he would like to recalculate the route from his current positions
            if (distance[0] > 100) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.error_too_far_from_start_title);
                builder.setMessage(R.string.error_too_far_from_start_message);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        waypoints.set(0, Point.fromLngLat(lastKnownLocation.getLongitude(), lastKnownLocation.getLatitude()));
                        fetchRoute();
                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        _launchNavigationWithRoute();
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                _launchNavigationWithRoute();
            }
        } else {
            _launchNavigationWithRoute();
        }

    }

    private void _launchNavigationWithRoute() {
        NavigationLauncherOptions.Builder optionsBuilder = NavigationLauncherOptions.builder()
                .shouldSimulateRoute(getShouldSimulateRouteFromSharedPreferences())
                .directionsProfile(getRouteProfileFromSharedPreferences())
                .waynameChipEnabled(false);

        optionsBuilder.directionsRoute(route);

        NavigationLauncher.startNavigation(this, optionsBuilder.build());
    }

    private boolean validRouteResponse(Response<DirectionsResponse> response) {
        return response.body() != null && !response.body().routes().isEmpty();
    }

    private void hideLoading() {
        if (loading.getVisibility() == View.VISIBLE) {
            loading.setVisibility(View.INVISIBLE);
        }
    }

    private void showLoading() {
        if (loading.getVisibility() == View.INVISIBLE) {
            loading.setVisibility(View.VISIBLE);
        }
    }

    private void boundCameraToRoute() {
        if (route != null) {
            List<Point> routeCoords = LineString.fromPolyline(route.geometry(),
                    Constants.PRECISION_6).coordinates();
            List<LatLng> bboxPoints = new ArrayList<>();
            for (Point point : routeCoords) {
                bboxPoints.add(new LatLng(point.latitude(), point.longitude()));
            }
            if (bboxPoints.size() > 1) {
                try {
                    LatLngBounds bounds = new LatLngBounds.Builder().includes(bboxPoints).build();
                    // left, top, right, bottom
                    animateCameraBbox(bounds, CAMERA_ANIMATION_DURATION, padding);
                } catch (InvalidLatLngBoundsException exception) {
                    Toast.makeText(this, R.string.error_valid_route_not_found, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void animateCameraBbox(LatLngBounds bounds, int animationTime, int[] padding) {
        CameraPosition position = mapboxMap.getCameraForLatLngBounds(bounds, padding);
        mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), animationTime);
    }

    private void animateCamera(LatLng point) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, DEFAULT_CAMERA_ZOOM), CAMERA_ANIMATION_DURATION);
    }

    private void setCurrentMarkerPosition(LatLng position) {
        if (position != null) {
            if (currentMarker == null) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(position);
                currentMarker = mapboxMap.addMarker(markerOptions);
            } else {
                currentMarker.setPosition(position);
            }
        }
    }

    private void updateWaypoints(List<Point> points) {
        if (points.size() > 24) {
            onError(R.string.error_too_many_waypoints);
            return;
        }
        clearRoute();
        this.waypoints = points;
        updateRouteAfterWaypointChange();
    }


    @Override
    public void onError(int message) {
        Snackbar.make(mapView, message, Snackbar.LENGTH_LONG).show();
    }

    public void setPOIMarkers(List<List<Double>> pois){
        clearGeocodingResults();
        clearPOIMarkers();
        if (pois.isEmpty()) {
            onError(R.string.error_geocoding_no_location);
            return;
        }
        isPOISearch = true;
        POImarkers = new ArrayList<>();       //???????????????POI marker?????????
        geocodingInfo = new ArrayList<>();   //????????????????????????????????????
        POISearchSize = pois.size();
        showLoading();
        List<LatLng> bounds = new ArrayList<>();
        for (List<Double> location : pois) {            //??????poi????????????????????????????????????????????????
            System.out.println("current poi: " + location);
            String point = location.get(1) + "," + location.get(0);
            LatLng latLng = new LatLng(location.get(1),location.get(0));
            bounds.add(latLng);
            new FetchGeocodingTask(this, getString(R.string.gh_key)).execute(new FetchGeocodingConfig(null, getLanguageFromSharedPreferences().getLanguage(), 1, true, point, "default"));
            // For bounds we need at least 2 entries
            if (bounds.size() >= 2) {
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                boundsBuilder.includes(bounds);
                animateCameraBbox(boundsBuilder.build(), CAMERA_ANIMATION_DURATION, padding);
            } else if (bounds.size() == 1) {
                // If there is only 1 result (=>current location unknown), we just zoom to that result
                animateCamera(bounds.get(0));
            }
            hideLoading();
        }
    }

    public void showGeocodingResult(){           //???????????????POI?????????
        dialog = new Dialog(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dialog.setContentView(mainLayout,mainParams);
        Window dialogWindow = dialog.getWindow();
        dialogWindow.setGravity(Gravity.BOTTOM);
        for(int i = 0; i < geocodingInfo.size(); i++){
            View view = LayoutInflater.from(this).inflate(R.layout.single_geocoding_information,mainLayout,false);
            TextView indexText = (TextView) view.findViewById(R.id.geocoding_index);
            TextView nameText = (TextView) view.findViewById(R.id.geocoding_name);
            TextView streetText = (TextView) view.findViewById(R.id.geocoding_street);
            TextView codeAndCityText = (TextView) view.findViewById(R.id.geocoding_codeAndCity);
            Button goThereBtn = (Button) view.findViewById(R.id.gothere_btn);
            String geocodingName = geocodingInfo.get(i).get(0);
            String geocodingStreet = geocodingInfo.get(i).get(1);
            String geocodingCodeAndCity = geocodingInfo.get(i).get(2) + "   " + geocodingInfo.get(i).get(3);
            indexText.setText(String.valueOf(i+1));
            nameText.setText(geocodingName);
            streetText.setText(geocodingStreet);
            codeAndCityText.setText(geocodingCodeAndCity);
            mainLayout.addView(view);
            goThereBtn.setId(i);
            goThereBtn.setOnClickListener(this::onClick);
        }
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
        dialogWindow.setAttributes(lp);
        dialog.show();
    }

    public void clickEdit(View view){
        EditText location_search = (EditText) findViewById(R.id.search_location);
        location_search.setText("");
    }

    public void showBottom(){   //???????????????????????????????????????????????????
        TextView textView = findViewById(R.id.open_location_list);
        textView.setVisibility(View.VISIBLE);
        String str = "A total of " + String.valueOf(geocodingInfo.size())  + " results related to " + "<font color = '#0D76D3'>" + search.getText().toString() + "</font>" + " were found";
        textView.setText(Html.fromHtml(str));
    }

    public void openLocationList(View view){
        showGeocodingResult();
    }

    @Override
    public void onClick(View view){
        int id = view.getId();
        currLocationIndex = id;
        System.out.println(id);
        double lat = Double.parseDouble(geocodingInfo.get(id).get(4));
        double lon = Double.parseDouble(geocodingInfo.get(id).get(5));
        System.out.println(lat + " " + lon);
        dialog.dismiss();
        clearOtherMarkers(lat,lon);
        addPointToRoute(lat,lon);
        updateRouteAfterWaypointChange();
    }

    public void LocationMarker(List<GeocodingLocation> locations){
        System.out.println("location size: "+ locations.size());
        clearGeocodingResults();
        clearPOIMarkers();
        markers = new ArrayList<>(locations.size());
        geocodingInfo = new ArrayList<>();

        if (locations.isEmpty()) {
            onError(R.string.error_geocoding_no_location);
            return;
        }

        List<LatLng> bounds = new ArrayList<>();
        System.out.println("location size" + locations.size());
        int index = 0;
        for (GeocodingLocation location : locations) {
            if(!location.getCity().equals("New York"))
                continue;
            if(index == 2)
                break;
            //???????????????????????????
            List<String> currGeocodingInfo = new ArrayList<>();
            currGeocodingInfo.add(location.getName());
            currGeocodingInfo.add(location.getStreet());
            currGeocodingInfo.add(location.getPostcode());
            currGeocodingInfo.add(location.getCity());
            currGeocodingInfo.add(location.getPoint().getLat().toString());
            currGeocodingInfo.add(location.getPoint().getLng().toString());
            geocodingInfo.add(currGeocodingInfo);
            GeocodingPoint point = location.getPoint();
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.title(location.getName());
            String snippet = "";
            if (location.getStreet() != null) {
                System.out.println("location: " + location);
                snippet += location.getStreet();
                if (location.getHousenumber() != null)
                    snippet += " " + location.getHousenumber();
                snippet += "\n";
            }
            if (location.getCity() != null) {
                if (location.getPostcode() != null)
                    snippet += location.getPostcode() + " ";
                snippet += location.getCity() + "\n";
            }
            if (location.getCountry() != null)
                snippet += location.getCountry() + "\n";
            if (!snippet.isEmpty())
                markerOptions.snippet(snippet);
            LatLng latLng = new LatLng(point.getLat(), point.getLng());
            markerOptions.position(latLng);
            bounds.add(latLng);
            switch (index){
                case 0:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_0));
                    break;
                case 1:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_1));
                    break;
                case 2:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_2));
                    break;
                case 3:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_3));
                    break;
                case 4:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_4));
                    break;
                case 5:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_5));
                    break;
            }
            index++;
            markers.add(mapboxMap.addMarker(markerOptions));
        }

        // For bounds we need at least 2 entries
        if (bounds.size() >= 2) {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            boundsBuilder.includes(bounds);
            animateCameraBbox(boundsBuilder.build(), CAMERA_ANIMATION_DURATION, padding);
        } else if (bounds.size() == 1) {
            // If there is only 1 result (=>current location unknown), we just zoom to that result
            animateCamera(bounds.get(0));
        }
        hideLoading();
    }

    public void saveAPoiInfo(List<GeocodingLocation> locations){
        if (locations.isEmpty()) {
            onError(R.string.error_geocoding_no_location);
            return;
        }
        List<LatLng> bounds = new ArrayList<>();
        GeocodingLocation location = locations.get(0);   //????????????????????????????????????????????????????????????location???size??????1
        System.out.println("POI??? " + location);
        //??????????????????????????????
        List<String> currGeocodingInfo = new ArrayList<>();
        currGeocodingInfo.add(location.getName());
        currGeocodingInfo.add(location.getStreet());
        currGeocodingInfo.add(location.getPostcode());
        currGeocodingInfo.add(location.getCity());
        currGeocodingInfo.add(location.getPoint().getLat().toString());
        currGeocodingInfo.add(location.getPoint().getLng().toString());
        geocodingInfo.add(currGeocodingInfo);
    }

    public void POIMarkerIntoMap(){
        for(int i = 0; i < geocodingInfo.size(); i++){
            MarkerOptions markerOptions = new MarkerOptions();
            LatLng latLng = new LatLng(Double.parseDouble(geocodingInfo.get(i).get(4)),Double.parseDouble(geocodingInfo.get(i).get(5)));
            markerOptions.position(latLng);
            markerOptions.title(geocodingInfo.get(i).get(0));
            String snippet = "";
            if (geocodingInfo.get(i).get(3) != null) {
                if (geocodingInfo.get(i).get(2) != null)
                    snippet += geocodingInfo.get(i).get(2) + " ";
                snippet += geocodingInfo.get(i).get(3) + "\n";
            }
            if (!snippet.isEmpty())
                markerOptions.snippet(snippet);
            switch (i){
                case 0:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_0));
                    break;
                case 1:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_1));
                    break;
                case 2:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_2));
                    break;
                case 3:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_3));
                    break;
                case 4:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_4));
                    break;
                case 5:
                    markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_5));
                    break;
            }
            POImarkers.add(mapboxMap.addMarker(markerOptions));
        }
    }

    @Override
    public void onPostExecuteGeocodingSearch(List<GeocodingLocation> locations) {    //??????????????????????????????????????????
        //?????????????????????????????????????????????marker??????
        if(isPOISearch){
            System.out.println("postExecute poi");
            saveAPoiInfo(locations);
            if(geocodingInfo.size() == POISearchSize){
                POIMarkerIntoMap();
                showGeocodingResult();
                showBottom();
                hideLoading();
            }
        }
        else{
            System.out.println("postExecute location");
            LocationMarker(locations);
            showGeocodingResult();
            showBottom();
        }
    }

    @Override
    public void onPostExecute(List<Point> points) {
        if (getStartFromLocationFromSharedPreferences() && !points.isEmpty()) {
            // Remove the first point if we want to start from the current location
            points.remove(0);
        }
        updateWaypoints(points);
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
