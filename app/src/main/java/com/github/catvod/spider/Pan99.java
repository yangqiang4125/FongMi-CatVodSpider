package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Qile
 */
public class Pan99 extends Ali {

    private static String siteUrl = "https://pan99.xyz";
    private String douban = "@Referer=https://api.douban.com/@User-Agent=" + Utils.CHROME;

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Utils.CHROME);
        return header;
    }

    @Override
    public void init(Context context, String extend) {
        String[] split = extend.split("\\$");
        if (split.length == 2 && split[1].length() > 0) siteUrl = split[1];
        super.init(context, split[0]);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("dy", "tv", "tv/geng", "tv/netflix");
        List<String> typeNames = Arrays.asList("电影", "完结剧集", "追更剧集", "Netflix");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        List<Vod> list = new ArrayList<>();
        for (Element li : doc.select("div.col a.media-img")) {
            String vid = li.attr("href");
            String name = li.attr("title");
            String pic = li.attr("data-bg") + douban;
            list.add(new Vod(vid, name, pic));
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateId = extend.get("cateId") == null ? tid : extend.get("cateId");
        String cateUrl = siteUrl + String.format("/category/%s/page/%s", cateId, pg);
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeader()));
        List<Vod> list = new ArrayList<>();
        for (Element li : doc.select("div.col a.media-img")) {
            String vid = li.attr("href");
            String name = li.attr("title");
            String pic = li.attr("data-bg") + douban;
            list.add(new Vod(vid, name, pic));
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> list) throws Exception {
        try {
            String id =list.get(0);
            if (!Utils.regexAli.matcher(id).find()) {
                String[] arr = id.split("\\$\\$\\$");
                Matcher matcher = Utils.regexAli.matcher(OkHttp.string(arr[0], getHeader()));
                if (!matcher.find()) return "";
                arr[0] = matcher.group(1);
                String uid = TextUtils.join("$$$",arr);
                list.set(0, uid);
            }
            return super.detailContent(list);
        } catch (Exception e) {
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = siteUrl + "/?cat=&s=" + URLEncoder.encode(key);
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));
        List<Vod> list = new ArrayList<>();
        for (Element li : doc.select("div.col a.media-img")) {
            String vid = li.attr("href");
            String name = li.attr("title");
            String pic = li.attr("data-bg") + douban;
            list.add(new Vod(vid, name, pic));
        }
        return Result.string(list);
    }
}
