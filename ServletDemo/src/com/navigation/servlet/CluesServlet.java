package com.navigation.servlet;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.searchTools.Greedy;
import javafx.util.Pair;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

@WebServlet(name = "CluesServlet")
public class CluesServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("utf-8");
        response.setCharacterEncoding("utf-8");
        //得到前端传过来的json string
        BufferedReader br = request.getReader();
        String str, wholeStr = "";
        while((str = br.readLine()) != null){
            wholeStr += str;
        }
        //处理前端传过来的json string
        JSONObject jsonObject = JSONObject.parseObject(wholeStr);         //将json string转换为json object
        JSONArray cluesArray = jsonObject.getJSONArray("clues");      //得到表示clues的json array
        //得到起点和经纬度
        String origin = jsonObject.getString("origin");
        double origin_lat = Double.parseDouble(origin.split(",")[0]);
        double origin_lon = Double.parseDouble(origin.split(",")[1]);
        //将JSONArray格式的clues转换为list格式
        List<Pair<String,Double>> clues = JsonArray2list(cluesArray);
        try {
            //调用贪婪算法得到路径
            Greedy greedy = new Greedy();
            List<List<Double>> path = greedy.getFeasiblePath(origin_lat,origin_lon,clues);
            String pathStr= list2JsonStr(path);
           //将路径返回给前端
            PrintWriter writer = response.getWriter();
            writer.write(pathStr);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    }

    private List<Pair<String,Double>> JsonArray2list(JSONArray cluesArray){   //将json array转换为List
        List<Pair<String,Double>> clues = new ArrayList<>(cluesArray.size());
        for(int i = 0; i < cluesArray.size(); i++){
            JSONObject currClue = cluesArray.getJSONObject(i);
            String loc = currClue.getString("location");
            double dist = Double.parseDouble(currClue.getString("distance"));
            clues.add(new Pair<>(loc,dist));
        }
        return clues;
    }

    private String list2JsonStr(List<List<Double>> path){          //把贪婪算法计算出的list转换为json string
        JSONArray pathArray = new JSONArray();
        for(int i = 0; i < path.size(); i++){
            JSONObject curr = new JSONObject();
            curr.put("lat",path.get(i).get(0).toString());
            curr.put("lon",path.get(i).get(1).toString());
            pathArray.add(curr);
        }
        JSONObject res = new JSONObject();
        res.put("path",pathArray);
        return res.toString();
    }

}
