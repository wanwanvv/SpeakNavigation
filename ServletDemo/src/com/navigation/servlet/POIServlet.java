package com.navigation.servlet;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.searchTools.PoiSearch;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet(name = "POIServlet")
public class POIServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("utf-8");
        response.setCharacterEncoding("utf-8");
        String searchString = request.getParameter("searchString");   //查询字符串
        double currLon = Double.parseDouble(request.getParameter("currLon"));   //用户当前位置的lat
        double currLat = Double.parseDouble(request.getParameter("currLat"));    //用户当前位置的lon
        PoiSearch poiSearch = new PoiSearch();
        try {
            boolean isPoiSearch = poiSearch.isPOI(searchString);
            PrintWriter writer = response.getWriter();
            System.out.print(isPoiSearch);
            if(isPoiSearch){     //如果是POI搜索，则返回搜索到的POI结果
                List<List<Double>> poiList = poiSearch.poiSearch(searchString,currLat,currLon);
                String poiStr = list2JsonStr(poiList);      //将list转换为json string
                writer.write(poiStr);
            }
            else{        //如果是普通的地点搜素，则返回false
                writer.write(String.valueOf(isPoiSearch));
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    private String list2JsonStr(List<List<Double>> poiList){          //把贪婪算法计算出的list转换为json string
        JSONArray poiArray = new JSONArray();
        for(int i = 0; i < poiList.size(); i++){
            JSONObject curr = new JSONObject();
            curr.put("lat",poiList.get(i).get(0).toString());
            curr.put("lon",poiList.get(i).get(1).toString());
            poiArray.add(curr);
        }
        JSONObject res = new JSONObject();
        res.put("POI",poiArray);
        return res.toString();
    }
}
