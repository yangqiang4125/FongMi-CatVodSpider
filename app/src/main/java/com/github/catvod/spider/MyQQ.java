package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Utils;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.*;

public class MyQQ extends Spider {
    private JSONObject ext;
    private String extend;
    private static String siteUrl = "https://www.voflix.com";
    private static String wUrl = "---.html";
    private String[] types;
    private Integer total=0;

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        String mtype = Utils.CHROME;
        String m = getVal("ua");
        if (!m.isEmpty()) if(m.equals("0")||m.equals("mobile"))mtype=Utils.MOBILE;
        headers.put("User-Agent", mtype);
        return headers;
    }

    private void fetchRule() {
        try {
            if(extend.startsWith("http")){
                String result = OkHttp.string(extend);
                if (!TextUtils.isEmpty(result)) extend = result;
            }
            ext = new JSONObject(extend);
            siteUrl = getVal("siteUrl");
            wUrl = getVal("end");
            String fl = getVal("types");
            types = fl.split("#");
        } catch (JSONException e) {
        }
    }

    public String getVal(String key){
        if(ext==null)fetchRule();
        return ext.optString(key, "");
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
        try {
            List<Class> classes = new ArrayList<>();
            for (String fenlei : types) {
                String[] info = fenlei.split("\\$");
                Class c = new Class(info[1],info[0]);
                classes.add(c);
            }
            Result result = getVods(classes.get(0).getTypeId(), "1");
            result.classes(classes);
            return result.string();
        } catch (Exception e) {

        }
        return "";
    }
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            Result result = getVods(tid, pg);
            return result.string();
        } catch (NumberFormatException e) {
        }
        return "";
    }

    public Result getVods(String tid, String pg) {
        List<Vod> list = new ArrayList<>();
        String furl = siteUrl + tid +pg+ wUrl;
        Document doc2 = Jsoup.parse(OkHttp.string(furl, getHeaders()));
        String elbox = "div.module-items a";
        String elurl = "@href";
        String elname = "@title";
        String elpic = "img@src";
        String elremarks = ".module-item-note";
        String pageText = getVal("page");
        String page = getText(doc2, pageText);
        page = page.replaceAll(".*?(\\d+)"+wUrl, "$1");
        if(Utils.isNumeric(page)) total = Integer.parseInt(page);
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
        return Result.get().page(pg, list.size(), total).vod(list);
    }
    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = ids.get(0);
            String[] info = url.split("\\$\\$\\$");
            url = info[0];
            String ibox = getVal("ibox");
            String iname = getVal("iname");
            String ipic = getVal("ipic");
            String icontent = getVal("icontent");
            String itag = getVal("itag");

            String idirector=getVal("idirector");
            String iactor=getVal("iactor");
            String iyear=getVal("iyear");
            String iremark = getVal("iremarks");
            String iform = getVal("iform");
            String iurls = getVal("iurls");
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
        } catch (Exception e) {
        }
        return "";
    }

    public String getText(Element element,String key){
        if(key.isEmpty()) return "";
        String value = null;
        String type = "text";
        String [] arr = key.split("@");
        if (arr.length>1) type = arr[1];
        if (!key.startsWith("@")) {
            String kv = arr[0];
            if (kv.contains(":last")) {
                String[] k = kv.split(":last");
                Elements els = element.select(k[0]);
                if(els.size()==0) return "";
                Element el = els.last();
                if (type.equals("text")) {
                    value = el.text();
                } else value = el.attr(type);
            }else {
                Elements el = element.select(arr[0]);
                if (type.equals("text")) {
                    value = el.text();
                } else value = el.attr(type);
            }
        }else {
            if (type.equals("text")) {
                value = element.text();
            } else value = element.attr(type);
        }
        if(value!=null&&value.endsWith("/")) value = value.substring(0, value.length());
        return value == null ? "" : value;
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {

            List<Vod> list = new ArrayList<>();
            String search = getVal("search");
            String target = siteUrl + search + URLEncoder.encode(key);
            Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
            String sbox= getVal("sbox");
            String sname=getVal("sname");
            String surl = getVal("surl");
            String spic = getVal("spic");
            String sremarks = getVal("sremarks");
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
        } catch (Exception e) {
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).parse().header(getHeaders()).string();
    }
}
