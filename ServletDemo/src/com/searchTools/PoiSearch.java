package com.searchTools;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PoiSearch {
    private static final double EARTH_RADIUS = 6378137;
    private static String rootPath = "";
    private static double rad(double d){
        return d*Math.PI/180.0;
    }

    public PoiSearch(){
        rootPath = getClass().getResource("/").getFile().toString();
    }

    public static double GetDistance(double lng1,double lat1,double lng2,double lat2){
        double radLat1 = rad(lat1);
        double radLat2 = rad(lat2);
        double a = radLat1 - radLat2;
        double b = rad(lng1)-rad(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a/2),2)+Math.cos(radLat1)*Math.cos(radLat2)*Math.pow(Math.sin(b/2),2)));
        s = s * EARTH_RADIUS;
        return s;
    }
    public boolean isPOI(String keywords) throws Exception{
        InputStream inputStream = new FileInputStream(new File(rootPath + "/com/dataset/nav_poi_id.txt"));
        BufferedReader poil = new BufferedReader(new InputStreamReader(inputStream));
        String str;
        Set<String> pois = new HashSet<>();
        while ((str = poil.readLine()) != null)
        {
            String[] temp = str.split(",");
            pois.add(temp[0]);
        }
        return pois.contains(keywords);

    }
    public List<List<Double>> poiSearch(String keywords, double lat, double lon) throws IOException {
        InputStream inputStream = new FileInputStream(new File( rootPath + "/com/dataset/nav_poi_nearest.txt"));
        BufferedReader brl = new BufferedReader(new InputStreamReader(inputStream));
        String str;
        List<List<Double>> result = new ArrayList<>();
        while ((str = brl.readLine()) != null){
            String[] temp = str.split(",");
            String type = temp[3];
            if (type.equals(keywords)){
                double curlon = Double.parseDouble(temp[2]);
                double curlat = Double.parseDouble(temp[1]);
                if (GetDistance(lon,lat,curlon,curlat) < 500){
                    if(result.size() >= 5)
                        break;
                    List<Double> point = new ArrayList<>();
                    // 0：lon   1:lat
                    point.add(curlon);
                    point.add(curlat);
                    result.add(point);
                }
            }
        }
        System.out.println("mmmmmmm" + result.size());
        return result;
    }

}
