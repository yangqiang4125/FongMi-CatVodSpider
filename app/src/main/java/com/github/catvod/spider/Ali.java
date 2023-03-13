package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
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
    public static final Pattern pattern = Pattern.compile("www.aliyundrive.com/s/([^/]+)(/folder/([^/]+))?");
    @Override
    public void init(Context context, String extend) {
        fetchRule(false,0);
    }

    public static JSONObject fetchRule(boolean flag, int t) {
        try {
            String rs = API.get().getRefreshToken();
            if (flag || Utils.siteRule == null ||(rs == null || rs.isEmpty())) {
                String json = OkHttp.string(Utils.jsonUrl+"?t="+Time());
                JSONObject jo = new JSONObject(json);
                if(t==1) {
                    String[] fenleis = getRuleVal(jo,"fenlei", "").split("#");
                    for (String fenlei : fenleis) {
                        String[] info = fenlei.split("\\$");
                        jo.remove(info[1]);
                    }
                    Utils.apikey = Utils.siteRule.optString("apikey", "0ac44ae016490db2204ce0a042db2916");
                    szRegx =  Utils.siteRule.optString("szRegx", szRegx);
                    Utils.isPic = Utils.siteRule.optInt("isPic", 0);
                }
                Utils.siteRule = jo;
                if (rs == null || rs.isEmpty()) {
                    Utils.refreshToken = Utils.siteRule.optString("token", "");
                    API.get().setRefreshToken(Utils.refreshToken);
                }
                return jo;
            }
        } catch (JSONException e) {
        }
        return Utils.siteRule;
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
        fetchRule(false,0);
        String url = ids.get(0).trim();String url2=null;
        String[] idInfo = url.split("\\$\\$\\$");
        if (idInfo.length > 0)  url2 = idInfo[0].trim();
        Matcher matcher = pattern.matcher(url2);
        if (!matcher.find()) return "";
        String shareId = matcher.group(1);
        String fileId = matcher.groupCount() == 3 ? matcher.group(3) : "";
        API.get().setShareId(shareId);
        return Result.string(API.get().getVod(url, fileId));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        API.get().checkAccessToken();
        String[] ids = id.split("\\+");
        if (flag.contains("原画")) {
            return Result.get().url(API.get().getDownloadUrl(ids[0])).subs(API.get().getSub(ids)).header(API.get().getHeader()).parse(0).string();
        } else {
            return Result.get().url(API.get().getPreviewUrl(ids[0])).subs(API.get().getSub(ids)).header(API.get().getHeader()).parse(0).string();
        }
    }

    public static Object[] vod(Map<String, String> params) {
        String type = params.get("type");
        if (type.equals("sub")) return API.get().proxySub(params);
        if (type.equals("m3u8")) return API.get().proxyM3U8(params);
        if (type.equals("media")) return API.get().proxyMedia(params);
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
