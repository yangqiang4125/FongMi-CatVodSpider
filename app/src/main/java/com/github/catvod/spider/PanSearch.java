package com.github.catvod.spider;

import android.content.Context;

import android.text.TextUtils;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * @author zhixc
 */
public class PanSearch extends Ali {
    @Override
    public void init(Context context, String extend){
        inits(context,extend,"https://www.pansearch.me/");
    }
    private Map<String, String> getSearchHeader() {
        Map<String, String> header = getHeader();
        header.put("x-nextjs-data", "1");
        header.put("referer", siteUrl);
        return header;
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String html = OkHttp.string(siteUrl, getHeader());
        String data = Jsoup.parse(html).select("script[id=__NEXT_DATA__]").get(0).data();
        String buildId = new JSONObject(data).getString("buildId");
        String url = siteUrl + "_next/data/" + buildId + "/search.json?keyword=" + URLEncoder.encode(key) + "&pan=aliyundrive";
        String result = OkHttp.string(url, getSearchHeader());
        JSONArray array = new JSONObject(result).getJSONObject("pageProps").getJSONObject("data").getJSONArray("data");
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String content = item.optString("content");
            String[] split = content.split("\\n");
            if (split.length == 0) continue;
            String id =Jsoup.parse(content).select("a").attr("href");
            String name = split[0].replaceAll("</?[^>]+>", "");
            name = name.replace("名称：", "");
            String remark = item.optString("time");
            remark = remark.replaceAll("T", " ").replace("+08:00", "");
            String pic = item.optString("image");
            String vodId = id + "$$$" + pic + "$$$" + name;
            list.add(new Vod(vodId, name, pic, remark));
        }
        return Result.string(list);
    }
}
