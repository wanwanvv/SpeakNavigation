package com.searchTools;

import javafx.util.Pair;

import java.io.*;
import java.util.*;

public class Greedy{

    private Map<Integer,Double>[] road ;
    private Map<String, List<Integer>>[] keywords;
    private Map<Integer, Pair<Integer,Double>> POI_cross;
    private Map<String,Integer> POILatLon_Id ;
    private Map<String,Integer> LatLon_Id;
    private int nodeNum;
    private double minDist = Double.MAX_VALUE;
    private static final double EARTH_RADIUS = 6378137;
    private static double rad(double d){
        return d*Math.PI/180.0;
    }
    private static String rootPath = "";

    public static double GetDistance(double lng1,double lat1,double lng2,double lat2){
        double radLat1 = rad(lat1);
        double radLat2 = rad(lat2);
        double a = radLat1 - radLat2;
        double b = rad(lng1)-rad(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a/2),2)+Math.cos(radLat1)*Math.cos(radLat2)*Math.pow(Math.sin(b/2),2)));
        s = s * EARTH_RADIUS;
        return s;
    }

    public Greedy() throws IOException {
        nodeNum = 451631;
        road = new Map [nodeNum];
        keywords = new Map[nodeNum];
        POI_cross = new HashMap<>();
        POILatLon_Id = new HashMap<>();
        LatLon_Id = new HashMap<>();

        rootPath = getClass().getResource("/").getFile().toString();


        //初始化LatLon_Id
        InputStream latlonInputStream = new FileInputStream(new File( rootPath + "/com/dataset/nav_id_osmid_lat_lon.txt"));
        BufferedReader latlonBufferReader = new BufferedReader(new InputStreamReader(latlonInputStream));
        String latlonString;
        while ((latlonString=latlonBufferReader.readLine()) != null){
            String[] temp = latlonString.split(",");
            int id = Integer.parseInt(temp[0]);
            LatLon_Id.put(temp[2]+","+temp[3],id);
        }

        //初始化road
        for (int i = 0;i<nodeNum;i++){
            road[i] = new HashMap<>();
        }
        InputStream roadInputStream = new FileInputStream(new File( rootPath + "/com/dataset/nav_source_target_len.txt"));
        BufferedReader roadBufferedReader = new BufferedReader(new InputStreamReader(roadInputStream));
        String roadString;
        while ((roadString=roadBufferedReader.readLine()) != null){
            String[] temp = roadString.split(",");
            int source = Integer.parseInt(temp[0]);
            int target = Integer.parseInt(temp[1]);
            double length = Double.parseDouble(temp[2]);
            road[source].put(target,length);
        }

        //初始化keywords和POI_cross
        for (int i = 0;i<nodeNum;i++){
            keywords[i] = new HashMap<>();
        }
        InputStream keywordInputStream = new FileInputStream(new File( rootPath + "/com/dataset/nav_poi_nearest.txt"));
        BufferedReader keywordsBufferedReader = new BufferedReader(new InputStreamReader(keywordInputStream));
        String keywordsString;
        while ((keywordsString = keywordsBufferedReader.readLine()) != null){
            String[] temp = keywordsString.split(",");
            int poiID = Integer.parseInt(temp[0]);
            String type = temp[3];
            int neighbor = Integer.parseInt(temp[4]);
            double dist = Double.parseDouble(temp[5]);
            POI_cross.put(poiID,new Pair<Integer,Double>(neighbor,dist));
            if (!keywords[neighbor].containsKey(type)){
                keywords[neighbor].put(type,new ArrayList<>());
            }
            keywords[neighbor].get(type).add(poiID);
        }

        //初始化POILatLon_Id
        InputStream inputStream = new FileInputStream(new File(rootPath + "/com/dataset/nav_id_lat_lon_keywords.txt"));
        BufferedReader poiSelf = new BufferedReader(new InputStreamReader(inputStream));
        String str;
        while ((str = poiSelf.readLine()) != null)
        {
            String[] temp = str.split(",");
            String key = temp[1]+","+temp[2];
            Integer value = Integer.valueOf(temp[0]);
            POILatLon_Id.put(key,value);
        }

    }

    //根据用户的当前位置，获取最近的路口
    public int getNearestCross(double lat,double lon){
        int id = -1;
        for(String key : LatLon_Id.keySet()){
            double currlat = Double.parseDouble(key.split(",")[0]);
            double currlon = Double.parseDouble(key.split(",")[1]);
            double currdist = GetDistance(lon,lat,currlon,currlat);
            if(currdist < minDist){
                minDist = currdist;
                id = LatLon_Id.get(key);
            }
        }
        return id;
    }

    //根据边长排序函数
    private <K,V extends Comparable<? super V>> Map<K,V> sortAscend(Map<K, V> map){
        List<Map.Entry<K,V>> list = new ArrayList<Map.Entry<K, V>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                int compare = (o1.getValue()).compareTo(o2.getValue());
                return compare;
            }
        });
        Map<K,V> returnMap = new LinkedHashMap<K,V>();
        for (Map.Entry<K,V> entry : list){
            returnMap.put(entry.getKey(),entry.getValue());
        }
        return returnMap;
    }

    //对road的边长进行排序
    private void sortNetwork(){
        for(int i = 0; i < nodeNum; i++){
            Map<Integer,Double> edges = road[i];
            Map<Integer,Double> sortedEdges = sortAscend(edges);
            road[i] = sortedEdges;
        }
    }

    //计算匹配距离

    private double computeMatchingDistance(double dG, double d, double e){
        double temp = Math.abs(dG-d);
        return temp/(e*d);
    }

    //依据贪婪算法计算下一个节点
    //返回对应的POIId以及CrossId,ans[0] = POIId,ans[1] = crossId
    private int[] findNextMin(int source, String keyword, double d, double e){
        int[] ans = {-1,-1};
        double dMax = d * (1 + e);
        double dMin = d * (1 - e);
        Pair<Integer,Double> node = new Pair<>(source,minDist);
        //存放候选的POI以及该POI到上一个已获得Poi的对应距离
        List<Pair<Integer,Double>> res = new ArrayList<>();
        //队列中存放crossId，距离上一个已获得Poi的距离
        Queue<Pair<Integer,Double>> myQueue = new LinkedList<>();
        boolean[] visited = new boolean[nodeNum];
        myQueue.add(node);
        //存放交叉路口是否被访问过
        visited[source] = true;
        //存放crossId 和 POIId
        while (!myQueue.isEmpty()){
            //cur.first代表当前交叉路口的ID，cur.second代表当前交叉路口到上一个已获得POI的距离
            Pair<Integer,Double> cur = myQueue.poll();
            if (keywords[cur.getKey()].containsKey(keyword)){
                for (Integer integer:keywords[cur.getKey()].get(keyword)){
                    double curPOIdis = cur.getValue() + POI_cross.get(integer).getValue();
                    if ((curPOIdis >= dMin) && (curPOIdis <= dMax)){
                        Pair<Integer,Double> can = new Pair<>(integer,curPOIdis);
                        res.add(can);
                    }
                }
            }
           /*
           每一个未访问的、距离小于dMax的路口节点都要入队：crossId、cross距离上一个已获得的POI的结果
           出队时判断该十字路口是否包含该关键字，若包含，则对应的POI距离是否大于dMin，小于dMax
           若满足，则将poiId和对应的POI距离入候选集数组
            */
            for (Integer integer:road[cur.getKey()].keySet()) {
                //integer是下一个连通的交叉路口
                Double dis = cur.getValue() + road[cur.getKey()].get(integer);
                if ((!visited[integer]) && (dis <= dMax)){
                    Pair<Integer, Double> waitIn = new Pair<>(integer, dis);
                    myQueue.add(waitIn);
                    visited[integer] = true;
                }
            }
        }
        double min = Double.MAX_VALUE;
        for (Pair<Integer, Double> re : res) {
            double curValue = computeMatchingDistance(re.getValue(), d, e);
            if (curValue < min) {
                min = curValue;
                ans[0] = POI_cross.get(re.getKey()).getKey();
                ans[1] = re.getKey();
            }
        }
        return ans;
    }

    //当不指明具体距离时
    private int[] findNextMin(int source,String keyword){
        int[] ans = {-1,-1};
        double dMax = 500;
        Pair<Integer,Double> node = new Pair<>(source,minDist);
        List<Pair<Integer,Double>> res = new ArrayList<>();
        Queue<Pair<Integer,Double>> myQueue = new LinkedList<>();
        boolean[] visited = new boolean[nodeNum];
        myQueue.add(node);
        visited[source] = true;
        while (!myQueue.isEmpty()){
            Pair<Integer,Double> cur = myQueue.poll();
            if (keywords[cur.getKey()].containsKey(keyword)){
                for (Integer integer:keywords[cur.getKey()].get(keyword)){
                    double curPOIdis = cur.getValue() + POI_cross.get(integer).getValue();
                    if (curPOIdis <= dMax){
                        Pair<Integer,Double> can = new Pair<>(integer,curPOIdis);
                        res.add(can);
                    }
                }
            }
            for (Integer integer:road[cur.getKey()].keySet()) {
                Double dis = cur.getValue() + road[cur.getKey()].get(integer);
                if ((!visited[integer]) && (dis <= dMax)){
                    Pair<Integer, Double> waitIn = new Pair<>(integer, dis);
                    myQueue.add(waitIn);
                    visited[integer] = true;
                }
            }
        }
        double min = Double.MAX_VALUE;
        for (Pair<Integer, Double> re : res) {
            if (re.getValue()< min) {
                min = re.getValue();
                ans[0] = POI_cross.get(re.getKey()).getKey();
                ans[1] = re.getKey();
            }
        }
        return ans;

    }

    // 依据findNextMin计算完整路径
    public List<List<Double>> getFeasiblePath(double lat,double lon,List<Pair<String,Double>> clues) throws Exception {
        long start_time = System.currentTimeMillis();
        sortNetwork();
        List<List<Double>> paths = new ArrayList<>();
        List<Double> source = new ArrayList<>();
        source.add(lat);
        source.add(lon);
        paths.add(source);
        int start = getNearestCross(lat,lon);
        System.out.println("size: "+clues.size());
        System.out.println(clues);
        for (int i = 0;i < clues.size();i++){
            if (start == -1){
                return null;
            }
            int[] next = new int[2];
            if (clues.get(i).getValue() == -1.0){
                System.out.println(clues.get(i).getKey());
                next = findNextMin(start,clues.get(i).getKey());
            }else {
                System.out.println(clues.get(i).getKey());
                next = findNextMin(start,clues.get(i).getKey(),clues.get(i).getValue(),0.5);
            }
            paths.add(getPOILatLon(next[1]));
            //start存储的是对应的路口
            start = next[0];
        }
        long end_time = System.currentTimeMillis();
        System.out.println("GCS runtime" + (end_time-start_time));
        return paths;
    }


    // 根据POI的id返回POI经纬度
    public List<Double> getPOILatLon(int POIId){
        List<Double> POILatLon = new ArrayList<>();
        for(String LatLon:POILatLon_Id.keySet()){
            if (POILatLon_Id.get(LatLon) == POIId){
                String[] tmp = LatLon.split(",");
                POILatLon.add(Double.valueOf(tmp[0]));
                POILatLon.add(Double.valueOf(tmp[1]));
            }
        }
        return POILatLon;
    }

}

