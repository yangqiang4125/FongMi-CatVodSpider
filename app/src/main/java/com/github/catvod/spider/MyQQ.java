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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;

public class MyQQ extends Spider {
    private JSONObject ext;
    private String extend;
    private static String siteUrl = "https://www.voflix.me";
    private static String wUrl = "---.html";
    private String[] types;
    private Integer total=0;
    private String elBoxHtml = null;
    public MyQQ(String ext){
        extend = ext;
        fetchRule();
    }
    public MyQQ() { }
    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Utils.CHROME);
        return headers;
    }
    private HashMap<String, String> getHeadersUa() {
        HashMap<String, String> headers = new HashMap<>();
        String mtype = Utils.CHROME;
        String m = getVal("ua");
        if (!m.isEmpty()) if(m.equals("0")||m.equals("mobile"))mtype=Utils.MOBILE;
        if(m.length()>12)mtype = m;
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

    public String getVal(String key,String dval){
        if(ext==null)fetchRule();
        return ext.optString(key, dval);
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
        if(tid.contains("%")) furl = siteUrl + tid.replace("%", pg);
        Document doc2 = Jsoup.parse(OkHttp.string(furl, getHeaders()));
        String elbox = getVal("elbox");
        String elurl = getVal("elurl");
        String elname = getVal("elname");
        String elpic = getVal("elpic");
        String elremarks = getVal("elremarks");
        String pageText = getVal("page");
        if(pageText.isEmpty()) {
            if(Utils.isNumeric(pg)) total = Integer.parseInt(pg)+1;
        }else {
            String page = "0";
            try {
                if (pageText.contains("@")) {
                    page = getText(doc2, pageText);
                    page = page.replaceAll(".*?(\\d+)"+wUrl, "$1");
                }else {
                    if(Utils.isNumeric(pageText))page = pageText;
                    else {
                        String html = doc2.html();
                        Matcher matcher = Utils.matcher(pageText, html);
                        while (matcher.find()) {
                            page = matcher.group(1);
                            break;
                        }
                    }
                }
                if(Utils.isNumeric(page)) total = Integer.parseInt(page);
            } catch (Exception e) {
                if(Utils.isNumeric(pg)) total = Integer.parseInt(pg)+1;
            }
        }
        for (Element element : doc2.select(elbox)) {
            String id = getText(element,elurl);
            if(id!=null) id = getUrl(siteUrl, id);
            String name = getText(element,elname);
            String pic = getText(element, elpic);
            if(pic!=null) pic = Utils.fixUrl(siteUrl, pic);
            String remarks = null;
            if(elremarks!=null&&!elremarks.isEmpty()) remarks = getText(element, elremarks);
            id=id + "$$$" + pic + "$$$" + name;
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
            String iboxHtml = getVal("iboxHtml");
            String ibox = getVal("ibox");
            String iname = getVal("iname");
            String ipic = getVal("ipic");
            String icontent = getVal("icontent");
            String ijsnum = getVal("ijsnum");
            String itag = getVal("itag");

            String idirector=getVal("idirector");
            String iactor=getVal("iactor");
            String iyear=getVal("iyear");
            String iremark = getVal("iremarks");
            String iform = getVal("iform");
            String iurls = getVal("iurls");
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            if (!iboxHtml.isEmpty()) elBoxHtml = doc.selectFirst(iboxHtml).html();
            Vod vod = new Vod();
            if (!ibox.isEmpty()) {
                String rbox = ibox.replace(":eq(%)", "");
                Elements els = doc.select(rbox);
                for (Element el : els) {
                    getValue(el.text(),vod);
                }
            }
            String jsa=vod.vodTag;
            String name = getText(doc,iname);
            String pic = getText(doc, ipic);
            String content = getText(doc, icontent);
            if(name.isEmpty()) name = getText(doc,"title");
            vod.setVodId(ids.get(0));
            vod.setVodName(name);
            vod.setVodPic(pic);
            vod.setVodContent(content);
            vod.setVodTag(getText(doc, itag));
            String idirectort = getText(doc, idirector);
            if(!idirectort.isEmpty())vod.setVodDirector(idirectort);
            if(vod.vodDirector.isEmpty())vod.setVodDirector("未知");
            String iactort = getText(doc, iactor);
            if(!iactort.isEmpty())vod.setVodActor(iactort);
            if(vod.vodActor.isEmpty())vod.setVodActor("未知");
            vod.setVodYear(getText(doc, iyear));
            vod.setVodRemarks(getText(doc,iremark));
            String tag = getText(doc, itag);
            if(!tag.isEmpty()){
                String jsnum = getText(doc, ijsnum);
                if(jsnum.isEmpty()&&jsa!=null)jsnum=jsa;
                if(!jsnum.isEmpty()) {
                    jsnum = jsnum.trim();
                    tag = tag+"   评分：无 "+jsnum;
                }
                vod.setVodTag(tag);
                if(vod.vodRemarks!=null&&vod.vodRemarks.isEmpty()) vod.setVodRemarks(jsnum);
            }
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
                String tabfirst = getVal("tabfirst");
                if (!tabfirst.isEmpty()) {
                    sites=moveKeyToFirst(sites, tabfirst);
                }
                vod.setVodPlayFrom(TextUtils.join("$$$", sites.keySet()));
                vod.setVodPlayUrl(TextUtils.join("$$$", sites.values()));
            }
            return Result.string(vod);
        } catch (Exception e) {
        }
        return "";
    }
    public static Map<String, String> moveKeyToFirst(Map<String, String> map, String key) {
        String [] arr=key.split("\\|");
        if(arr.length==1&&!key.contains("\\")&&!map.containsKey(key))return map;
        Map<String, String> newMap = new LinkedHashMap<>();
        for (String k : arr) {
            String mkey = k;
            if (mkey.contains("\\")) {
                for (String mk : map.keySet()) {
                    boolean fb = Utils.matcher(mkey, mk).matches();
                    if (fb) {
                        mkey = mk;
                        break;
                    }
                }
            }
            if (map.containsKey(mkey)) {
                newMap.put(mkey, map.get(mkey));
                map.remove(mkey);
                break;
            }
        }
        newMap.putAll(map);
        return newMap;
    }
    public String getText(Element element,String key){
        if(key.isEmpty()) return "";
        String rhtml = null;
        if(key.contains("(.*?)")){
            rhtml = Utils.getStrByRegex(key, elBoxHtml!=null?elBoxHtml:element.html());
        }else rhtml = getText(element, key, null);
        if(rhtml.endsWith("/")) rhtml = rhtml.replaceAll("(.*?)\\/+$", "$1");
        return rhtml;
    }
    public String getText(Element element,String key,Vod vod){
        if(key.isEmpty()) return "";
        String value = null;
        try {
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
                    if(k.length>1)el = el.selectFirst(k[1]);
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
            value = getValue(value,vod);
        } catch (Exception e) {
        }
        return value == null ? "" : value;
    }

    public String getValue(String value,Vod vod){
        if(value!=null) {
            if(value.contains("http")||Utils.isSpUrl(value)) value = Utils.trim(value);
            if (!value.startsWith("http")) {
                if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
                value = value.replace("&nbsp;", " ");
                value = value.replace("详情", "");
                Matcher m = Utils.matcher("(.*)(:|：)(.*)", value);
                if (m.matches()) {
                    value = m.group(3);
                    if (vod != null) {
                        String k = m.group(1);
                        if (k.contains("演员") || k.contains("主演")) vod.setVodActor(value);
                        else if (k.contains("导演")) vod.setVodDirector(value);
                        else if (k.contains("集")||k.contains("备注")) vod.setVodTag(value);
                        else if (k.contains("简介") || k.contains("介绍") || k.contains("详情")) vod.setVodContent(value);
                    }
                }
            }
        }
        return value;
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            List<Vod> list = new ArrayList<>();
            String search = getVal("search");
            key = URLEncoder.encode(key);
            String sname=null,surl=null,spic = null;
            if (search.isEmpty()) {
                String durl = getVal("idetail","/detail/%.html");
                String id = "id";
                if (!durl.contains("%")) {
                    id = durl.replaceAll(".*\\((\\w+)\\).*", "$1");
                    durl = durl.replace("(" + id + ")", "%");
                }
                String result = OkHttp.string(siteUrl+"/ajax/suggest?mid=1&wd="+key);
                JSONObject response = new JSONObject(result);
                if (response.optInt("code", 0) == 1 && response.optInt("total", 0) > 0) {
                    JSONArray jsonArray = response.getJSONArray("list");
                    for (int i=0;i<jsonArray.length();i++) {
                        JSONObject o = (JSONObject) jsonArray.get(i);
                        sname = o.optString("name", "");
                        spic = o.optString("pic", "");
                        if(spic!=null) spic = Utils.fixUrl(siteUrl, spic);
                        surl = o.optString(id, "");
                        surl = durl.replace("%", surl);
                        if(surl!=null) surl = getUrl(siteUrl, surl);
                        surl= surl + "$$$" + spic + "$$$" + sname;
                        list.add(new Vod(surl, sname, spic));
                    }
                }
            }else {
                String target = siteUrl + search + key;
                Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
                String sbox= getVal("sbox");
                sname=getVal("sname");
                surl = getVal("surl");
                spic = getVal("spic");
                String sremarks = getVal("sremarks");

                for (Element element : doc.select(sbox)) {
                    String id = element.select(surl).attr("href");
                    if(id!=null) id = getUrl(siteUrl, id);
                    String name = getText(element, sname);
                    String pic = getText(element, spic);
                    if(pic!=null) pic = Utils.fixUrl(siteUrl, pic);
                    String remarks = null;
                    if(sremarks!=null&&!sremarks.isEmpty()) remarks = getText(element, sremarks);
                    id=id + "$$$" + pic + "$$$" + name;
                    list.add(new Vod(id, name, pic, remarks));
                }
            }
            return Result.string(list);
        } catch (Exception e) {
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).parse().header(getHeadersUa()).string();
    }
}
