package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.ali.API;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ColaMint & Adam & FongMi
 */
public class Ali extends Spider {
    private static String szRegx = ".*(Ep|EP|E|第)(\\d+)[\\.|集]?.*";//集数数字正则匹配
    @Override
    public void init(Context context, String extend) {
        if(extend.startsWith("http://test.xinjun58.com/"))Utils.jsonUrl = extend;
        fetchRule(false,0);
    }

    public static JSONObject fetchRule(boolean flag, int t) {
        try {
            if (flag || Utils.siteRule == null ||API.get().isRefresh()) {
                Utils.jsonUrl = Utils.getDataStr(Utils.jsonUrl);
                String jurl =Utils.jsonUrl+"?t="+Time();
                String json = OkHttp.string(jurl);
                JSONObject jo = new JSONObject(json);
                Utils.siteRule = jo;
                /*if(t==0) {
                    String[] fenleis = getRuleVal(jo,"fenlei", "").split("#");
                    for (String fenlei : fenleis) {
                        String[] info = fenlei.split("\\$");
                        jo.remove(info[1]);
                    }
                }*/
                Utils.apikey = Utils.siteRule.optString("apikey", "0ac44ae016490db2204ce0a042db2916");
                Utils.spRegx =  Utils.siteRule.optString("szRegx", szRegx);
                Utils.isPic = Utils.siteRule.optInt("isPic", 0);
                Utils.refreshToken = Utils.siteRule.optString("token", "");
                Utils.tokenInfo = Utils.siteRule.optString("tokenInfo", "0");
                Utils.userAgent = Utils.siteRule.optString("userAgent", "");
                API.get().setAuth(true);
                Utils.etime = Utils.getLongTime(API.get().getAuth().getTime());
                return jo;
            }
        } catch (JSONException e) {
        }
        return Utils.siteRule;
    }

    public static String getPicAgent(String pic){
        if (!Utils.userAgent.isEmpty()&&pic!=null&&pic.contains(".doubanio")) {
            return pic + Utils.userAgent;
        }
        return pic;
    }
    public static String getRuleVal(JSONObject o,String key, String defaultVal) {
        String v = o.optString(key);
        if (v.isEmpty() || v.equals("空"))
            return defaultVal;
        return v;
    }

    public static String getRuleVal(JSONObject o,String key) {
        return getRuleVal(o,key, "");
    }

    public static long Time() {
        return (System.currentTimeMillis() / 1000);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0).trim();String url2=null;
        String[] idInfo = url.split("\\$\\$\\$");
        if (idInfo.length > 0)  url2 = idInfo[0].trim();
        Matcher matcher = Utils.regexAli.matcher(url2);
        if (!matcher.find()) return "";
        String shareId = matcher.group(3);
        String fileId = matcher.groupCount() == 5 ? matcher.group(5) : "";
        //API.get().setShareId(shareId);
        return Result.string(API.get().getVod(url, fileId,shareId));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        Long time = Time();
        if (time - Utils.etime > 3600) {
            fetchRule(true,0);
            if (time - Utils.etime > 3600) {
                API.get().refreshAccessToken(false);
            }
        }
        String[] ids = id.split("\\+");
        return flag.contains("原画") ? API.get().playerContent(ids) : API.get().playerContent(ids, flag);
    }

    public static Object[] proxy(Map<String, String> params) throws Exception {
        String type = params.get("type");
        if (type.equals("sub")) return API.get().proxySub(params);
        return null;
    }

    public Vod vod(String url, String type) {
        String[] idInfo = url.split("\\$\\$\\$");
        if (idInfo.length > 0)  url = idInfo[0].trim();
        String vpic = "";
        String vname="";
        if (idInfo != null) {
            if(idInfo.length>1&&!idInfo[1].isEmpty()) vpic = idInfo[1];
            if(idInfo.length>2&&!idInfo[2].isEmpty()) vname = idInfo[2];
        }

        if (vname.isEmpty()) {
            Document doc = Jsoup.parse(OkHttp.string(url));
            vname = doc.select("head > title").text();
        }
        if (vname.isEmpty()) {
            vname = url;
        }
        if (vpic.isEmpty()) {
            vpic = Utils.getWebName(url,1);
        }
        Vod vod = new Vod();
        vod.setTypeName(type);
        vod.setVodId(url);
        vod.setVodName(vname);
        vod.setVodPlayFrom(type);
        vod.setVodPlayUrl("播放$" + url);
        vod.setVodPic(vpic);
        return vod;
    }
}
