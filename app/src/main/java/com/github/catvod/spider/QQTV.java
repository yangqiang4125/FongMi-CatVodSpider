package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;
import com.github.catvod.utils.Trans;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.*;

public class QQTV extends Spider {
    private JSONObject ext;
    private String extend;
    private static final String siteUrl = "https://www.voflix.com";
    private static final String listUrl = siteUrl + "/show/";
    private static final String wUrl = "---.html";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Utils.CHROME);
        return headers;
    }

    private void fetchRule() throws Exception {
        String result = OkHttp.string(extend);
        if (!TextUtils.isEmpty(result)) extend = result;
        ext = new JSONObject(extend);
    }

    @Override
    public void init(Context context, String extend) {
        try {
            this.extend = extend;
            fetchRule();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getUrl(String siteUrl, String url){
        if(!url.startsWith("http"))url = siteUrl+url;
        return url;
    }
    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        String[] fenleis = "韩剧$15-韩国-------#国产剧$13--------#综艺$3--------".split("#");
        for (String fenlei : fenleis) {
            String[] info = fenlei.split("\\$");
            Class c = new Class(info[1],info[0]);
            classes.add(c);
        }
        List<Vod> list = getVods(classes.get(0).getTypeId(), "1");
        return Result.string(classes, list);
    }
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        pg = String.valueOf(Integer.parseInt(pg) + 1);
        List<Vod> list = getVods(tid, pg);
        return Result.string(list);
    }

    public List<Vod> getVods(String tid, String pg) {
        List<Vod> list = new ArrayList<>();
        String furl = listUrl + tid +pg+ wUrl;
        Document doc2 = Jsoup.parse(OkHttp.string(furl, getHeaders()));
        String elbox = "div.module-items a";
        String elurl = "@href";
        String elname = "@title";
        String elpic = "img@src";
        String elremarks = ".module-item-note";
        for (Element element : doc2.select(elbox)) {
            String id = getText(element,elurl);
            if(id!=null) id = getUrl(siteUrl, id);
            String name = getText(element,elname);
            String pic = getText(element, elpic);
            if(pic!=null) pic = Utils.fixUrl(siteUrl, pic);
            String remarks = null;
            if(elremarks!=null&&!elremarks.isEmpty()) remarks = getText(element, elremarks);
            list.add(new Vod(id, name, pic, remarks));
        }
        return list;
    }
    @Override
    public String detailContent(List<String> ids) {
        String url = ids.get(0);
        String[] info = url.split("\\$\\$\\$");
        url = info[0];

        String ibox = ".module-main .module-info-items .module-info-item:eq(%) .module-info-item-content";
        String iname = ".module-main .module-item-pic img@alt";
        String ipic = ".module-main .module-item-pic img@src";
        String icontent = ".module-main .module-info-items .module-info-introduction-content";
        String itag = ".module-main .module-info-tag-link";

        String idirector="1";
        String iactor="2";
        String iyear="3";
        String iremark = "6";
        String iform = ".module-tab-items .module-tab-item";
        String iurls = "div.module-list .module-play-list-content";
        if (!ibox.isEmpty()) {
            idirector=ibox.replace("%", idirector);
            iactor=ibox.replace("%", iactor);
            iyear=ibox.replace("%", iyear);
            iremark = ibox.replace("%", iremark);
        }
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        String name = getText(doc,iname);
        String pic = getText(doc, ipic);
        String content = getText(doc, icontent);

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodContent(content);
        vod.setVodTag(getText(doc, itag));
        vod.setVodDirector(getText(doc, idirector));
        vod.setVodActor(getText(doc, iactor));
        vod.setVodYear(getText(doc, iyear));
        vod.setVodRemarks(getText(doc,iremark));
        Map<String, String> sites = new LinkedHashMap<>();
        Elements sources = doc.select(iform);
        Elements sourceList = doc.select(iurls);
        for (int i = 0; i < sources.size(); i++) {
            Element source = sources.get(i);
            String sourceName = source.text();
            Elements playList = sourceList.get(i).select("a");
            List<String> vodItems = new ArrayList<>();
            for (int j = 0; j < playList.size(); j++) {
                Element e = playList.get(j);
                vodItems.add(Trans.get(e.text()) + "$" + getUrl(siteUrl, e.attr("href")));
            }
            if (vodItems.size() > 0) {
                sites.put(sourceName, TextUtils.join("#", vodItems));
            }
        }
        if (sites.size() > 0) {
            vod.setVodPlayFrom(TextUtils.join("$$$", sites.keySet()));
            vod.setVodPlayUrl(TextUtils.join("$$$", sites.values()));
        }
        return Result.string(vod);
    }

    public String getText(Element element, String key){
        if(key.isEmpty()) return "";
        String value = null;
        String type = "text";
        String[] arr = key.split("@");
        if (arr.length>1) type = arr[1];
        if (!key.startsWith("@")) {
            Elements el = element.select(arr[0]);
            if (type.equals("text")) {
                value = el.text();
            } else value = el.attr(type);
        }else {
            if (type.equals("text")) {
                value = element.text();
            } else value = element.attr(type);
        }
        if(value!=null&&value.endsWith("/")) value = value.substring(0, value.length()-1);
        return value == null ? "" : value;
    }

    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> list = new ArrayList<>();
        String search = "/search/-------------.html?wd=";
        String target = siteUrl + search + URLEncoder.encode(key);
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        String sbox= ".module-items .module-item";
        String sname=".module-card-item-info .module-card-item-title@text";
        String surl = "a:eq(0)";
        String spic = "a img@src";
        String sremarks = ".module-item-note";
        for (Element element : doc.select(sbox)) {
            String id = element.select(surl).attr("href");
            if(id!=null) id = getUrl(siteUrl, id);
            String name = getText(element, sname);
            String pic = getText(element, spic);
            if(pic!=null) pic = Utils.fixUrl(siteUrl, pic);
            String remarks = null;
            if(sremarks!=null&&!sremarks.isEmpty()) remarks = getText(element, sremarks);
            list.add(new Vod(id, name, pic, remarks));
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).parse().header(getHeaders()).string();
    }
}
