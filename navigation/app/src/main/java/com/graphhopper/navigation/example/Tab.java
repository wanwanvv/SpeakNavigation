package com.graphhopper.navigation.example;

import android.annotation.SuppressLint;
import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;

public class Tab extends TabActivity {
    @SuppressLint("ResourceType")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tabtest);
        TabHost tabHost = getTabHost();
        tabHost.addTab(tabHost.newTabSpec("Tab1")
                .setIndicator("location-based")
                .setContent(new Intent(this, TwoLocationNavigate.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        tabHost.addTab(tabHost.newTabSpec("Tab2")
                .setIndicator("clue-based")
                .setContent(new Intent(this, ClueNavigate.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        // 添加这句话，会使得每次跳转到该页面都是新建一个页面，以往的数据状态会丢失

    }
}