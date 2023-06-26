package com.github.catvod.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA 2020.1
 * author: YangQiang
 * DateTime: 2023-06-26 11:27
 * updateTime: 2023-06-26 11:27
 * Description:视频一级菜单
 */
public class VmenuUtil {
    public static JSONArray getMenuArray(JSONArray classes, JSONObject filterConfig, String pname) throws JSONException {
        JSONObject newCls;
        JSONArray extendsAll2=null;
        String[] arr=pname.split(",");
        int ki = 100;
        for (String s : arr) {
            String key = String.valueOf(ki);
            newCls = new JSONObject();
            newCls.put("type_id", key);
            newCls.put("type_name", s);
            classes.put(newCls);
            extendsAll2 = myCat(s);
            filterConfig.put(key, extendsAll2);
            ki++;
        }
        return extendsAll2;
    }

    public static int getYear(){
        Calendar cd = Calendar.getInstance();
        return  cd.get(Calendar.YEAR);
    }
    public static JSONArray myCat(String catName) throws JSONException {
        int year = getYear();
        String years = "";
        if (year > 2023) {
            JSONArray yms = new JSONArray();
            JSONObject ym = null;
            String key = null;
            for(int i=2023;i<=2023;i++){
                key = String.valueOf(i);
                ym = new JSONObject();
                ym.put("v", key);
                ym.put("n", key);
                yms.put(ym);
            }
            years = yms.toString();
            years = years.substring(1);
            years = years.replace("]", ",");
        }
        String mycatType = "[{\"name\":\"年份\",\"value\":["+years+"{\"v\":\"2023\",\"n\":\"2023\"},{\"v\":\"2022\",\"n\":\"2022\"},{\"v\":\"2021\",\"n\":\"2021\"},{\"v\":\"2020\",\"n\":\"2020\"},{\"v\":\"2019\",\"n\":\"2019\"},{\"v\":\"2018\",\"n\":\"2018\"},{\"v\":\"2017\",\"n\":\"2017\"},{\"v\":\"2016\",\"n\":\"2016\"},{\"v\":\"2015\",\"n\":\"2015\"},{\"v\":\"lt|2015\",\"n\":\"2015之前\"}],\"key\":\"year\"},{\"name\":\"地区\",\"value\":[],\"key\":\"area\"}]";
        JSONArray myCat = new JSONArray(mycatType);

        JSONObject myCatSono = myCat.getJSONObject(1);
        JSONArray myCatSon = myCatSono.getJSONArray("value");
        Map<String, String> map = new HashMap<>();
        map.put("国产剧", "中国大陆");
        map.put("韩剧", "韩国");
        map.put("美剧", "美国");
        map.put("台剧", "中国台湾");
        map.put("港剧", "中国香港");
        map.put("日剧", "日本");
        Map<String, String> amap = new HashMap<>();
        amap.put("n", catName);
        amap.put("v", map.get(catName));
        myCatSon.put(amap);
        return myCat;
    }
}
