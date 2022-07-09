package com.graphhopper.navigation.example;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
//import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Response;

public class ClueNavigate extends AppCompatActivity implements OnMapReadyCallback,
        OnRouteSelectionChangeListener,
        FetchSolutionTaskCallbackInterface,
        FetchGeocodingTaskCallbackInterface,
        PermissionsListener,View.OnClickListener
        {

    private static final int CAMERA_ANIMATION_DURATION = 1000;
    private static final int DEFAULT_CAMERA_ZOOM = 14;
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

    private Marker currentMarker;
    private List<Marker> markers;
    private List<Point> waypoints = new ArrayList<>();
    private DirectionsRoute route;
    private LocaleUtils localeUtils;


    private final int[] padding = new int[]{50, 50, 50, 50};

    private String currentJobId = "";
    private String currentVehicleId = "";

    String nlpResult = "";     //关键词解析的结果
    private List<Pair<String,Double>> tempClues;    //语音识别出的途径地信息

    Context context;
    private Marker currLocationMarker;

    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    private List<Pair<String,String>> POIs;    //根据输入的途径地搜索到的确切POI

    private Dialog dialog;       //显示提取出的关键词的dialog
    private Dialog POIdialog;    //显示查找到的POI的dialog

    private LinearLayout mainLayout;
    private List<LinearLayout> cluesLayout;
    public boolean finishedNLP = false;
    public int POIindex = 0;   //判断是第几次反向编码
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.clue_base_navigate);
        Mapbox.getInstance(this.getApplicationContext(), getString(R.string.mapbox_access_token));
        Telemetry.disableOnUserRequest();
        ButterKnife.bind(this);
        mapView.setStyleUrl(Style.MAPBOX_STREETS);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        localeUtils = new LocaleUtils();
        context = this.getApplicationContext();
        getSupportActionBar().hide();
        initSpeech();

    }
    // 开始集成语音识别
    public void initSpeech(){
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=5f295d51");
    }

    public void startSpeechDialog(View view) {
        //1. 创建RecognizerDialog对象
        RecognizerDialog mDialog = new RecognizerDialog(this, new ClueNavigate.MyInitListener()) ;
        //2. 设置accent、 language等参数
        mDialog.setParameter(SpeechConstant. LANGUAGE, "en_us" );// 设置英文
        //设置界面的语言为英文
        mDialog.setUILanguage(Locale.US);
        // 若要将UI控件用于语义理解，必须添加以下参数设置，设置之后 onResult回调返回将是语义理解
        // 结果
        // mDialog.setParameter("asr_sch", "1");
        // mDialog.setParameter("nlp_version", "2.0");
        //3.设置回调接口
        mDialog.setListener( new ClueNavigate.MyRecognizerDialogListener()) ;
        //4. 显示dialog，接收语音输入
        mDialog.show() ;
        TextView txt = (TextView)mDialog.getWindow().getDecorView().findViewWithTag("textlink");
        txt.setText("");
    }

    class MyRecognizerDialogListener implements RecognizerDialogListener {

        /**
         * @param results
         * @param isLast  是否说完了
         */
        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String result = results.getResultString(); //为解析的
            System. out.println(" 没有解析的 :" + result);
            String text = com.graphhopper.navigation.example.JsonParser.parseIatResult(result);//解析过后的
            System. out.println(" 解析后的 :" + text);

            String sn = null;
            // 读取json结果中的 sn字段
            try {
                JSONObject resultJson = new JSONObject(results.getResultString()) ;
                sn = resultJson.optString("sn" );
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mIatResults .put(sn, text) ;//没有得到一句，添加到

            StringBuffer resultBuffer = new StringBuffer();
            for (String key : mIatResults.keySet()) {
                resultBuffer.append(mIatResults .get(key));
            }
            RelativeLayout speechLayout = (RelativeLayout)findViewById(R.id.speechLayout);
            EditText speechText = (EditText) findViewById(R.id.speechText);
            speechLayout.setVisibility(View.VISIBLE);
//            speechText.setText(resultBuffer.toString());//设置输入框的文本
//            String asrResult = "pick a route to the airport passing a fuel";
//            String asrResult = "I want to find a path first passing a restaurant then four hundred and sixty meters there is a atm and then four hundred meters there is a fast food";
            String asrResult = "I want to find a route first passing a restaurant Then walk about four hundred and sixty meters to an atm and another four hundred meters to a fast food";

            speechText.setText(asrResult);//设置输入框的文本
            speechText.setSelection(speechText.length());//把光标定位末尾
            //进行关键词提取
            String orig_texts = resultBuffer.toString().replaceAll( "[\\p{P}+~$`^=|<>～｀＄＾＋＝｜＜＞￥×]" , "");
            String url = "http://" + DefaultConfig.IP + ":5000/demo";
            View view = findViewById(R.id.keywords_text);
//            SendMessage(url, orig_texts,view);
            String texts = strToNum(asrResult);
            SendMessage(url,texts,view);
//            SendMessage(url,asrResult,view);
            System.out.println("11111");
            showLoading();
        }

        @Override
        public void onError(SpeechError speechError) {

        }
    }

    class MyInitListener implements InitListener {

        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败 ");
            }

        }
    }

    /**
     * 语音识别
     */
    public void startSpeech() {
        //1. 创建SpeechRecognizer对象，第二个参数： 本地识别时传 InitListener
        SpeechRecognizer mIat = SpeechRecognizer.createRecognizer( this, null); //语音识别器
        //2. 设置听写参数，详见《 MSC Reference Manual》 SpeechConstant类
        mIat.setParameter(SpeechConstant. DOMAIN, "iat" );// 短信和日常用语： iat (默认)
        mIat.setParameter(SpeechConstant. LANGUAGE, "en_us" );// 设置英文
        mIat.setParameter(SpeechConstant.VAD_EOS,"10000");
        mIat.setParameter(SpeechConstant.ASR_PTT,"0");
        //3. 开始听写
        mIat.startListening( mRecoListener);
    }


    // 听写监听器
    public RecognizerListener mRecoListener = new RecognizerListener() {
        // 听写结果回调接口 (返回Json 格式结果，用户可参见附录 13.1)；
        //一般情况下会通过onResults接口多次返回结果，完整的识别内容是多次结果的累加；
        //关于解析Json的代码可参见 Demo中JsonParser 类；
        //isLast等于true 时会话结束。
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.e (TAG, results.getResultString());
            System.out.println(results.getResultString()) ;
            showTip(results.getResultString()) ;
        }

        // 会话发生错误回调接口
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true)) ;
            // 获取错误码描述
            Log. e(TAG, "error.getPlainDescription(true)==" + error.getPlainDescription(true ));
        }

        // 开始录音
        public void onBeginOfSpeech() {
            showTip(" 开始录音 ");
        }

        //volume 音量值0~30， data音频数据
        public void onVolumeChanged(int volume, byte[] data) {
            showTip(" 声音改变了 ");
        }

        // 结束录音
        public void onEndOfSpeech() {
            showTip(" 结束录音 ");
        }

        // 扩展用接口
        public void onEvent(int eventType, int arg1 , int arg2, Bundle obj) {
        }
    };

    public void showTip(String data){
        Toast.makeText(this,data,Toast.LENGTH_SHORT).show();
    }
    public void omitASR(View view){
//        String orig_texts = "I want to find a path first passes a bicycle rental six hundred and seventy meters away from me then three hundred and fifty meters there is a bicycle parking and then three hundred and sixty meters there is a school";
//        String orig_texts = "I want to find a path first passes a restaurant one hundred meters away from me then four hundred and sixty meters there is a atm and then four hundred meters there is a fast food";
//        String orig_texts = "I want to find a path first passes a restaurant then four hundred and sixty meters there is a atm and then four hundred meters there is a fast food";
        String orig_texts = "I want to find a route first passing a restaurant Then walk about four hundred and sixty meters to an atm and another four hundred meters to a fast food";
//        String orig_texts = "Pick a route to the airport passing a fuel";
//        String orig_texts = "Pick a route to the restaurant passing a fuel";
        RelativeLayout speechLayout = (RelativeLayout)findViewById(R.id.speechLayout);
        EditText speechText = (EditText) findViewById(R.id.speechText);
        speechLayout.setVisibility(View.VISIBLE);
        speechText.setText(orig_texts);//设置输入框的文本
        speechText.setSelection(speechText.length());//把光标定位末尾
        //进行关键词提取
        String url = "http://" + DefaultConfig.IP + ":5000/demo";
//        String url = "http://222.20.76.82:5000/demo";
        System.out.println("开始连接服务器！");
        String texts = strToNum(orig_texts);
        SendMessage(url, texts,view);
        System.out.println("hahahaha");
        showLoading();
    }
    //默认不指定个位数字
    public String strToNum(String orig){
        String[] origs = orig.split(" ");
        StringBuilder stringBuilder = new StringBuilder();
        //存放hundred/thousand之前的数字
        Map<String,Integer> map = new HashMap<>();
        map.put("one",1);
        map.put("two",2);
        map.put("three",3);
        map.put("four",4);
        map.put("five",5);
        map.put("six",6);
        map.put("seven",7);
        map.put("eight",8);
        map.put("nine",9);
        Map<String,Integer> nummap = new HashMap<>();
        nummap.put("ten",10);
        nummap.put("twenty",20);
        nummap.put("thirty",30);
        nummap.put("forty",40);
        nummap.put("fifty",50);
        nummap.put("sixty",60);
        nummap.put("seventy",70);
        nummap.put("eighty",80);
        nummap.put("ninety",90);
        for (int i = 0;i<origs.length;i++){
            if ((!map.containsKey(origs[i]))&&(!nummap.containsKey(origs[i]))){
                stringBuilder.append(origs[i]+" ");
            }else{
                int sum = 0;
                while (!origs[i].equals("meters")){
                    if (origs[i].equals("and")){
                        i++;
                    }
                    if (map.containsKey(origs[i])){
                        int j = map.get(origs[i]);
                        if (origs[i+1].equals("hundred")){
                            sum = sum + j * 100;
                        }else if (origs[i+1].equals("thousand")){
                            sum = sum + j * 1000;
                        }else {
                            sum = sum + j;
                        }
                        i = i + 2;
                    }
                    if (nummap.containsKey(origs[i])){
                        sum = sum + nummap.get(origs[i]);
                        i++;
                    }
                }
                stringBuilder.append(sum).append(" ");
                stringBuilder.append("meters ");
            }
        }
        return stringBuilder.toString();
    }
    //结束语音识别集成
    //连接服务器进行关键词提取
    public void SendMessage(String url, final String orig_texts,View view){
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder formBuilder = new FormBody.Builder();
        formBuilder.add("orig_texts",orig_texts);
        Request request = new Request.Builder().url(url).post(formBuilder.build()).build();
        okhttp3.Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ClueNavigate.this,"Server Error",Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                final String res = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (res.equals("0")){
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ClueNavigate.this,"Keywords parsing failed!",Toast.LENGTH_SHORT).show();
                                }
                            });
                        }else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
//                                    Toast.makeText(ClueNavigate.this,"Keywords parsing succeeded!",Toast.LENGTH_SHORT).show();
                                    nlpResult = res;
                                    System.out.println("nlpResult:" + nlpResult);
                                    System.out.println("数据传输成功！");
                                    initClues();
                                    System.out.println("线索初始化成功！");
                                    RelativeLayout speechLayout = findViewById(R.id.speechLayout);
                                    speechLayout.setVisibility(View.INVISIBLE);
                                    hideLoading();
                                    showKeywordsDialog(view);
                                }
                            });
                        }
                    }
                });
            }
        });

    }

    private void initClues(){   //初始化clues
        System.out.println("nlpResult" + nlpResult);
        if (!nlpResult.equals("")){
            String[] strings = nlpResult.split("#");
            tempClues = new ArrayList<>();
            System.out.println("clues size" + tempClues.size());
            for (int i = 1;i <= Integer.parseInt(strings[0]);i++){
                System.out.println("string:" + strings[2*i]);
                tempClues.add(new Pair<>(strings[2*i-1].trim(),Double.parseDouble(strings[2*i])));
            }
        }
        else{
            tempClues = new ArrayList<>();
            Toast.makeText(ClueNavigate.this,"No Clues!",Toast.LENGTH_SHORT).show();
        }
    }

    public LinearLayout createAClueLayout(int i,boolean flag){  //生成第i个索引对应的布局,flag=true表示生成一个空clue让用户编辑
        LinearLayout view = (LinearLayout)LayoutInflater.from(this).inflate(R.layout.single_clue_information,null);
        EditText locText = view.findViewById(R.id.loc_text);
        EditText distText = view.findViewById(R.id.dist_text);
        Button add_btn = view.findViewById(R.id.add_btn);
        Button del_btn = view.findViewById(R.id.del_btn);
        locText.setTag("currLoc" + i);
        distText.setTag("currDist" + i);
        add_btn.setTag("add" + i);
        del_btn.setTag("del" + i);
        add_btn.setOnClickListener(this::onClick);
        del_btn.setOnClickListener(this::onClick);
        if(!flag){
            locText.setText(tempClues.get(i).first);
            if (tempClues.get(i).second == -1.0){
                distText.setText("unspecified");
            }else{
                distText.setText(tempClues.get(i).second.toString());
            }
        }
        return view;
    }

    public void generateLayout(){   //根据clues第一次动态生成布局
        for(int i = 0; i < tempClues.size(); i++){
            LinearLayout currLayout = createAClueLayout(i,false);
            LinearLayout.LayoutParams currParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mainLayout.addView(currLayout,currParams);
            cluesLayout.add(currLayout);
        }
        Button confirm_btn = new Button(this);
        confirm_btn.setText("Confirm");
        confirm_btn.setTag("Confirm");
        confirm_btn.setAllCaps(false);
        confirm_btn.setBackgroundColor(Color.parseColor("#87CEEB"));
        confirm_btn.setTextColor(Color.WHITE);
        confirm_btn.setOnClickListener(this::onClick);
        mainLayout.addView(confirm_btn);
    }

    public void regenrateLayout(){   //当删除或添加一个clue后，重新生成布局
        mainLayout.removeAllViews();
        for(int i = 0; i < cluesLayout.size(); i++){
            LinearLayout currLayout = cluesLayout.get(i);
            LinearLayout.LayoutParams currParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mainLayout.addView(currLayout,currParams);
        }
        Button confirm_btn = new Button(this);
        confirm_btn.setText("Confirm");
        confirm_btn.setTag("Confirm");
        confirm_btn.setAllCaps(false);
        confirm_btn.setBackgroundColor(Color.parseColor("#87CEEB"));
        confirm_btn.setOnClickListener(this::onClick);
        mainLayout.addView(confirm_btn);
    }

    public void showKeywordsDialog(View view){   //从底部弹出关键词的弹框
        cluesLayout = new ArrayList<>(tempClues.size());
        dialog = new Dialog(this);
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        generateLayout();
        dialog.setContentView(mainLayout,mainParams);
        Window dialogWindow = dialog.getWindow();
        dialogWindow.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
        dialogWindow.setAttributes(lp);
        dialog.show();
    }

    @Override
    public void onClick(View view){      //关键词弹框中按钮的绑定事件
        String tag = view.getTag().toString();
        System.out.println("tag" + tag);
        if(tag.startsWith("add")){   //在下方添加一个clue
            int index = Integer.parseInt(tag.substring(tag.length() - 1));
            LinearLayout insertLayout = createAClueLayout(index + 1,true);
            System.out.println("index:" + index);
            System.out.println("cluesLayout size:" + cluesLayout.size());
            for(int i = index + 1; i < cluesLayout.size();i++){    //添加一个元素后，重新设置控件的tag，因为点击时是根据tag判断点击的是哪个控件
                LinearLayout layout = cluesLayout.get(i);
                int currindex = i + 1;
                EditText Loc = layout.findViewWithTag("currLoc" + i);
                Loc.setTag("currLoc" + currindex);
                EditText Dist= layout.findViewWithTag("currDist" + i);
                Dist.setTag("currDist" + currindex);
                Button add= layout.findViewWithTag("add" + i);
                add.setTag("add" + currindex);
                Button del= layout.findViewWithTag("del" + i);
                del.setTag("del" + currindex);
            }
            cluesLayout.add(index + 1,insertLayout);
            regenrateLayout();
        }
        else if(tag.startsWith("del")){
            int index = Integer.parseInt(tag.substring(tag.length() - 1));
            for(int i = index + 1; i < cluesLayout.size();i++){
                LinearLayout layout = cluesLayout.get(i);
                int currindex = i - 1;
                EditText Loc = layout.findViewWithTag("currLoc" + i);
                Loc.setTag("currLoc" + currindex);
                EditText Dist= layout.findViewWithTag("currDist" + i);
                Dist.setTag("currDist" + currindex);
                Button add= layout.findViewWithTag("add" + i);
                add.setTag("add" + currindex);
                Button del= layout.findViewWithTag("del" + i);
                del.setTag("del" + currindex);
            }
            cluesLayout.remove(index);
            regenrateLayout();
        }
        else{   //点击的是"confirm"按钮,重新读取用户填写的clues
            tempClues.clear();
            POIindex = 0;
            for(int i = 0; i < cluesLayout.size(); i++){
                LinearLayout layout = cluesLayout.get(i);
                EditText Loc = layout.findViewWithTag("currLoc" + i);
                EditText Dist= layout.findViewWithTag("currDist" + i);
                String LocValue = Loc.getText().toString();
                Double distance;
                if("unspecified".equals(Dist.getText().toString())){
                    distance = -1.0;
                }
                else{
                    distance = Double.parseDouble(Dist.getText().toString());
                }
                dialog.dismiss();
                tempClues.add(new Pair<>(LocValue,distance));
            }
            try {    //点击confirm按钮后，开始规划路径
                getPathByServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void showPOIDialog(View view){     //显示路径上的POI信息
        if(POIs.size() != waypoints.size()){    //没有POI信息时，提示错误
            Snackbar.make(mapView, R.string.error_no_POI_info, Snackbar.LENGTH_LONG).show();
            Toast.makeText(ClueNavigate.this,"No POI information!",Toast.LENGTH_SHORT).show();
        }
        POIdialog = new Dialog(this);
        View view1 = LayoutInflater.from(this).inflate(R.layout.poi_timeline,null);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        POIdialog.setContentView(view1,mainParams);
        //往时间线布局里添加POI信息
        UnderLineLinearLayout underLineLinearLayout = (UnderLineLinearLayout) view1.findViewById(R.id.underline_layout);
        for(int i = 0; i < POIs.size(); i++){
            String name = POIs.get(i).first;
            if(name == null)
                name = "";
            String street = POIs.get(i).second;
            if(street == null)
                street = "";
            View v = LayoutInflater.from(this).inflate(R.layout.single_poi_information,underLineLinearLayout,false);
            TextView poiName = v.findViewById(R.id.poi_name);
            TextView poiStreet = v.findViewById(R.id.poi_street);
            if(i == 0){
                poiName.setText("current location");
            }
            else{
                poiName.setText(tempClues.get(i - 1).first);
            }
//            poiName.setText(name);
            poiStreet.setText(name + "   " + street);
            underLineLinearLayout.addView(v);
        }
        Window dialogWindow = POIdialog.getWindow();
        dialogWindow.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
        dialogWindow.setAttributes(lp);
        POIdialog.show();
    }

    public void fresh(View view){           //刷新页面，清空页面上的所有内容
        if(markers == null)
            return;
        //清空markers和路径信息
        clearGeocodingResults();
        clearRoute();
        POIindex = 0;
        //路径信息不可见，导航按钮不可用
        RelativeLayout routeInfoLayout = findViewById(R.id.routeInfoLayout);
        routeInfoLayout.setVisibility(View.INVISIBLE);
        Button navigate_btn = routeInfoLayout.findViewById(R.id.navigate);
        navigate_btn.setEnabled(false);
        POIs.clear();
    }

    public void routeNavigate(View view){
        launchNavigationWithRoute();
    }

    public String clues2JSONString(){       //将用户起点和途径地信息存为json字符串
        JSONObject originAndClues = new JSONObject();        //包含起点和clue
        try
        {
            JSONArray cluesArray = new JSONArray();      //把线索存为json数组
            for(int i = 0; i < tempClues.size(); i++){
                String loc = tempClues.get(i).first;
                String dist = tempClues.get(i).second.toString();
                JSONObject currJson = new JSONObject();
                currJson.put("location",loc);
                currJson.put("distance",dist);
                System.out.println("currJson"+currJson);
                cluesArray.put(currJson);
            }
            String origin = DefaultConfig.defaultLocation.latitude() + "," + DefaultConfig.defaultLocation.longitude();
            originAndClues.put("origin",origin);
            originAndClues.put("clues",cluesArray);
            originAndClues.put("city",DefaultConfig.city);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return originAndClues.toString();
    }

    public void getPathByServer(){        //与服务器通信，得到路径
        String url = "http://" + DefaultConfig.IP +":8000/clueBasedSearch";
//        String url = "http://222.20.76.82:8000/clueBasedSearch";
        com.squareup.okhttp.OkHttpClient client = new com.squareup.okhttp.OkHttpClient();
        MediaType JSON = MediaType.parse("application/json;charset=utf-8");
        System.out.println("tmdddddd");
        String originAndClues = clues2JSONString();
        RequestBody requestBody = RequestBody.create(JSON,originAndClues);
        final com.squareup.okhttp.Request request = new com.squareup.okhttp.Request.Builder().url(url).post(requestBody).build();
        client.newCall(request).enqueue(new com.squareup.okhttp.Callback() {
            @Override
            public void onFailure(com.squareup.okhttp.Request request, IOException e) {
                String  error = e.getMessage();
            }

            @Override
            public void onResponse(com.squareup.okhttp.Response response) throws IOException {
                if(response.isSuccessful()){
                    String responseText = response.body().string();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            getPathList(responseText);
                        }
                    });
                }
            }
        });
    }

    public void getPathList(String responseText){       //将服务器传回来的json字符串转换为List
        try{
            if("".equals(responseText)){
                Toast.makeText(this, R.string.error_valid_route_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject jsonObject = new JSONObject(responseText);
            JSONArray jsonArray = jsonObject.getJSONArray("path");
            System.out.println(jsonArray);
            List<List<Double>> path = new ArrayList<>(jsonArray.length());
            for(int i = 0; i < jsonArray.length(); i++){
                JSONObject currObject = jsonArray.getJSONObject(i);
                double lat = Double.parseDouble(currObject.getString("lat"));
                double lon = Double.parseDouble(currObject.getString("lon"));
                List<Double> currPoint = new ArrayList<>(2);
                currPoint.add(lat);
                currPoint.add(lon);
                path.add(currPoint);
            }
            System.out.println("path: "+path);
            planRoute(path);       //规划路径
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void planRoute(List<List<Double>> path){
        if(path == null){
            onError(R.string.error_cluebased_no_route);
            return;
        }
        //初始化markers和POIs数组
        markers = new ArrayList<>(path.size());
        POIs = new ArrayList<>(path.size());
        for(List<Double> location : path){
            //添加路径中的节点
            waypoints.add(Point.fromLngLat(location.get(1), location.get(0)));
            //反向编码并设置marker
            String point = location.get(0) + "," + location.get(1);
            new FetchGeocodingTask(this, getString(R.string.gh_key)).execute(new FetchGeocodingConfig(null, getLanguageFromSharedPreferences().getLanguage(), 1, true, point, "default"));
        }
        fetchRoute();
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

//    private void showRouteInfo(DirectionsRoute route){      //显示路径的时间距离等信息
//        RelativeLayout mainLayout = (RelativeLayout) findViewById(R.id.routeInfoLayout);
//        View view = LayoutInflater.from(this).inflate(R.layout.route_information,mainLayout);
//        TextView originText = (TextView) view.findViewById(R.id.routeOrigin);
//        TextView destinationText = (TextView) view.findViewById(R.id.routeDestination);
//        TextView distanceText = (TextView) view.findViewById(R.id.routeDistance);
//        TextView timeText = (TextView) view.findViewById(R.id.routeTime);
//        originText.setText(POIs.get(0).first);
//        int POISize = POIs.size();
//        System.out.println("POI size: "+ POISize + "   " + POIs.get(POISize - 1).first);
//        destinationText.setText(POIs.get(POISize - 1).first);
//        distanceText.setText(String.format("%.2f",(route.distance())/1000) + "km");
//        timeText.setText(Double.toString(Math.round((route.duration())/60)) + "min");
//    }

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
    }

    private void clearGeocodingResults() {
        if (markers != null) {
            for (Marker marker : markers) {
                this.mapboxMap.removeMarker(marker);
            }
            markers.clear();
        }
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        initMapRoute();

        this.mapboxMap.setOnInfoWindowClickListener(new MapboxMap.OnInfoWindowClickListener() {
            @Override
            public boolean onInfoWindowClick(@NonNull Marker marker) {
//                for (Marker geocodingMarker : markers) {
//                    if (geocodingMarker.getId() == marker.getId()) {
//                        LatLng position = geocodingMarker.getPosition();
//                        addPointToRoute(position.getLatitude(), position.getLongitude());
//                        updateRouteAfterWaypointChange();
//                        marker.hideInfoWindow();
//                        for (Marker marker1 : markers){
//                            marker1.remove();
//                        }
//                        return true;
//                    }
//                }
                clickAmarker(marker);
                return true;
            }
        });

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
        currLocationMarker = mapboxMap.addMarker(new MarkerOptions()
                .position(new LatLng(DefaultConfig.defaultLocation.latitude(),DefaultConfig.defaultLocation.longitude()))
                .icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.currlocation32_icon)));
//        mapboxMap.addMarker(new MarkerOptions()
//                .position(new LatLng(DefaultConfig.defaultLocation.latitude(),DefaultConfig.defaultLocation.longitude()))
//                .icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.currlocation32_icon)));
        animateCamera(new LatLng(DefaultConfig.defaultLocation.latitude(),DefaultConfig.defaultLocation.longitude()));
    }

    private void initMapRoute() {
        mapRoute = new NavigationMapRoute(mapView, mapboxMap);
        mapRoute.setOnRouteSelectionChangeListener(this);
    }

    public void clickAmarker(Marker marker){
        for (Marker geocodingMarker : markers) {
            if (geocodingMarker.getId() == marker.getId()) {
                LatLng position = geocodingMarker.getPosition();
                addPointToRoute(position.getLatitude(), position.getLongitude());
                updateRouteAfterWaypointChange();
                marker.hideInfoWindow();
                if(currentMarker!=null) {   //显示之前选中的出发地
                    mapboxMap.addMarker(new MarkerOptions().position(currentMarker.getPosition()));
                }
//                        return true;
            }
            else{
                mapboxMap.removeMarker(geocodingMarker);     //删除地图上的其他markers
            }
        }
    }

    private void changeRouteDistAndTime(DirectionsRoute route){
        TextView distanceText = (TextView) findViewById(R.id.routeDistance);
        TextView timeText = (TextView) findViewById(R.id.routeTime);
        distanceText.setText(String.format("%.2f",(route.distance())/1000) + "km");
        timeText.setText(Double.toString(Math.round((route.duration())/60)) + "min");
    }

    private void changeOriAndDesti(){
        TextView originText = (TextView) findViewById(R.id.routeOrigin);
        TextView destinationText = (TextView) findViewById(R.id.routeDestination);
        originText.setText(POIs.get(0).first);
        int POISize = POIs.size();
        System.out.println("POI size: "+ POISize + "   " + POIs.get(POISize - 1).first);
        destinationText.setText(POIs.get(POISize - 1).first);
    }

    private void fetchRoute() {
        mapboxMap.removeMarker(currLocationMarker);
        NavigationRoute.Builder builder = NavigationRoute.builder(this)
                .accessToken("pk." + getString(R.string.gh_key))
                .baseUrl(getString(R.string.base_url))
                .user("gh")
                .alternatives(true);
        //如果路径中节点个数少于2，无法规划
        if(waypoints.size() < 2){
            onError(R.string.error_not_enough_waypoints);
            return;
        }
        for(int i = 0;i < waypoints.size(); i++){
            Point point = waypoints.get(i);
            if(i == 0){     //第一个点为路径起点
                builder.origin(point);
            }
            else if(i < waypoints.size() - 1){    //路径的中间节点
                builder.addWaypoint(point);
            }
            else{    //最后一个点是路径的终点
                builder.destination(point);
            }
        }
        showLoading();
        setFieldsFromSharedPreferences(builder);
        builder.build().getRoute(new SimplifiedCallback() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                if (validRouteResponse(response)) {
                    route = response.body().routes().get(0);
                    mapRoute.addRoutes(response.body().routes());
                    boundCameraToRoute();
                    changeRouteDistAndTime(route);
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
            setCurrentMarkerPosition(latLng);
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

    // 设置是否从当前位置出发
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

        //已经将当前位置加入route中了，不用再次询问是否从当前位置出发
        _launchNavigationWithRoute();

//        Location lastKnownLocation = getLastKnownLocation();
//        if (lastKnownLocation != null && waypoints.size() > 1) {
//            float[] distance = new float[1];
//            Location.distanceBetween(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), waypoints.get(0).latitude(), waypoints.get(0).longitude(), distance);
//
//            //Ask the user if he would like to recalculate the route from his current positions
//            if (distance[0] > 100) {
//                AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                builder.setTitle(R.string.error_too_far_from_start_title);
//                builder.setMessage(R.string.error_too_far_from_start_message);
//                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        waypoints.set(0, Point.fromLngLat(lastKnownLocation.getLongitude(), lastKnownLocation.getLatitude()));
//                        fetchRoute();
//                    }
//                });
//                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        _launchNavigationWithRoute();
//                    }
//                });
//
//                AlertDialog dialog = builder.create();
//                dialog.show();
//            } else {
//                _launchNavigationWithRoute();
//            }
//        } else {
//            _launchNavigationWithRoute();
//        }

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

    @Override
    public void onPostExecuteGeocodingSearch(List<GeocodingLocation> locations) {
        if (locations.isEmpty()) {
            onError(R.string.error_geocoding_no_location);
            return;
        }
        List<LatLng> bounds = new ArrayList<>();
//        Location lastKnownLocation = getLastKnownLocation();
//        if (lastKnownLocation != null)
//            bounds.add(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()));

        GeocodingLocation location = locations.get(0);   //反向编码时一个经纬度只对应一个地点，所以location的size等于1
        System.out.println("POI： " + location);
        POIs.add(new Pair<>(location.getName(),location.getStreet()));    //向POIs里添加地点名称和街道信息，便于向用户展示
        if(POIs.size() == waypoints.size()){   //只有当反向编码完成后，才能显示路径信息
            changeOriAndDesti();
            Button navigate_btn = (Button) findViewById(R.id.navigate);
            navigate_btn.setEnabled(true);    //此时按钮可用
            RelativeLayout routeInfoLayout = (RelativeLayout) findViewById(R.id.routeInfoLayout);
            routeInfoLayout.setVisibility(View.VISIBLE);
//            showRouteInfo(route);
        }
        GeocodingPoint point = location.getPoint();
        MarkerOptions markerOptions = new MarkerOptions();
        LatLng latLng = new LatLng(point.getLat(), point.getLng());
        markerOptions.position(latLng);
        bounds.add(latLng);
        markerOptions.title(location.getName());
        String snippet = "";
        if (location.getStreet() != null) {
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
        switch (POIindex){
            case 0 :
                markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_0));
                break;
            case 1 :
                markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_1));
                break;
            case 2 :
                markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_2));
                break;
            case 3 :
                markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_3));
                break;
            case 4 :
                markerOptions.icon(IconFactory.getInstance(this.getApplicationContext()).fromResource(R.drawable.ic_map_marker_4));
                break;

        }
        POIindex++;
        markers.add(mapboxMap.addMarker(markerOptions));

        // For bounds we need at least 2 entries
//        if (bounds.size() >= 2) {
//            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
//            boundsBuilder.includes(bounds);
//            animateCameraBbox(boundsBuilder.build(), CAMERA_ANIMATION_DURATION, padding);
//        } else if (bounds.size() == 1) {
//            // If there is only 1 result (=>current location unknown), we just zoom to that result
//            animateCamera(bounds.get(0));
//        }
        hideLoading();
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
