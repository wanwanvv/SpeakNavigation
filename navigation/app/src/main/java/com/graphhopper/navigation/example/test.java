package com.graphhopper.navigation.example;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class test extends AppCompatActivity {

    private List<Pair<String,Double>> tempClues;    //语音识别出的途径地信息

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test);
    }

    public JSONObject getJson(String location,String distance) throws Exception{
        JSONObject jsonParam = new JSONObject();
        jsonParam.put("location",location);
        jsonParam.put("distance",distance);
        return jsonParam;
    }

    public String initClues(){
        JSONArray jsonArray = new JSONArray();
        try
        {
            jsonArray.put(getJson("bicycle_rental","456321"));
            jsonArray.put(getJson("bicycle_parking","352.0"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return jsonArray.toString();

    }

    public void btnTest(View view){
        String url = "http://222.20.76.82:8000/login";
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json;charset=utf-8");
        String clue_json = initClues();
        JSONObject clues = new JSONObject();
        try{
            clues.put("clues",clue_json);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        RequestBody requestBody = RequestBody.create(JSON,clues.toString());
        final Request request = new Request.Builder().url(url).post(requestBody).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                String  error = e.getMessage();
            }
            @Override
            public void onResponse(Response response) throws IOException {
                if(response.isSuccessful()){
                    String responseText = response.body().toString();
                    System.out.println("response " + responseText);
                }
            }
        });
    }
}
