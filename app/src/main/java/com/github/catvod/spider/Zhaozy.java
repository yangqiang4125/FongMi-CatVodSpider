package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Zhaozy extends Ali {
    private final Pattern regexVid = Pattern.compile("(\\S+)");
    private final String siteUrl = "https://zhaoziyuan.me/";
    private Map<String, String> getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Utils.CHROME);
        headers.put("Referer", siteUrl);
        headers.put("Cookie", getCookie());
        return headers;
    }

    private String getCookie() {
        if (Utils.zzy == null) {
            Map<String, String> params = new HashMap<>();
            params.put("username", "412594121@qq.com");
            params.put("password", "qq@4125");
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", Utils.CHROME);
            headers.put("Referer", siteUrl + "stop.html");
            headers.put("Origin", siteUrl);
            StringBuilder sb = new StringBuilder();
            Map<String, List<String>> resp = OkHttp.post(siteUrl + "logiu.html", params, headers).getResp();
            for (String item : resp.get("set-cookie")) sb.append(item.split(";")[0]).append(";");
            Utils.zzy = sb.toString();
        }
        return Utils.zzy;
    }

    @Override
    public String detailContent(List<String> list) throws Exception {
        try {
            String id =list.get(0);
            if (!id.contains("aliyundrive.com")) {
                String[] arr = id.split("\\$\\$\\$");
                Matcher matcher = Utils.regexAli.matcher(OkHttp.string(arr[0], getHeader()));
                if (!matcher.find()) return "";
                arr[0] = matcher.group(1);
                String uid = TextUtils.join("$$$",arr);
                list.set(0, uid);
                return super.detailContent(list);
            }
            return super.detailContent(list);
        } catch (Exception e) {
            Utils.zzy = null;
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "sox?filename=" + URLEncoder.encode(key);
        Document doc = Jsoup.parse(OkHttp.string(url, getHeader()));
        List<Vod> list = new ArrayList<>();
        String pic = "https://inews.gtimg.com/newsapp_bt/0/13263837859/1000";
        for (Element element : doc.select("div.li_con div.news_text")) {
            String href = element.select("div.news_text a").attr("href");
            Matcher matcher = regexVid.matcher(href);
            if (!matcher.find()) continue;
            String id = siteUrl + matcher.group(1);
            String name = element.select("div.news_text a h3").text();
            if (!name.contains(key)) continue;
            String remark = element.select("div.news_text a p").text().split("\\|")[1].split("：")[1];
            Vod vod = new Vod();
            vod.setVodPic(pic);
            vod.setVodId(id + "$$$" + pic + "$$$" + name);
            vod.setVodRemarks(remark);
            vod.setVodName(name);
            list.add(vod);
        }
        return Result.string(list);
    }
}