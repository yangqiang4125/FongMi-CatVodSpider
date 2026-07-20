package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.crawler.Spider;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Utils;
import com.github.catvod.utils.okhttp.OkHttp;
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
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.Comparator;

public class MyQQ extends Spider {
    private JSONObject ext;
    private String siteUrl = "https://www.voflix.me";
    private String wUrl = "---.html";
    private String pageUrl = "";
    private String cookie = "",errMsg=null;
    private String[] types;
    private Integer total=0;
    private String elBoxHtml = null;
    private boolean isdebug=false;
    private List<Class> classes = new ArrayList<>();
    public MyQQ(String ext){
        fetchRule(ext);
    }
    public MyQQ() { }
    @Override
    public void init(Context context, String extend) {
        fetchRule(extend);
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        String mtype = Utils.MOBILE;
        String m = getVal("ua");
        if (m.equals("pc"))mtype=Utils.CHROME;
        if(!cookie.isEmpty()) headers.put("Cookie", cookie);
        headers.put("User-Agent", mtype);
        return headers;
    }


    private void fetchRule(String extend) {
        try {
            String [] arr= null;
            if(extend.startsWith("http")){
                if(extend.contains(";")){
                    arr= extend.split(";");
                    extend = arr[0];
                }
                String result = OkHttp.string(extend);
                if (!TextUtils.isEmpty(result)) extend = result;
            }
            ext = new JSONObject(extend);
            siteUrl = getVals("siteUrl","host");
            if (arr != null) {
                String str = arr[1];
                JSONObject json = parseExtend(str);
                if (json.has("host")) {
                    str = json.optString("host", "");
                    if(!str.isEmpty())siteUrl = str;
                }
                merge(this.ext, json);
            }
            wUrl = getVal("end");
            pageUrl = getVal("pageUrl");
            String debug = getVal("debug");
            isdebug = debug.equals("1");
            cookie = getVal("cookie");
            String fl = getVal("types");
            types = fl.split("#");
            if (!pageUrl.isEmpty()) {
                int z=0;
                for (String v : types) {
                    arr = v.split("\\$");
                    String num = arr[1];
                    String purl =num;
                    if(!purl.startsWith("/")) purl = pageUrl.replaceFirst("%", num);
                    v=arr[0]+"$"+purl;
                    types[z] = v;
                    z++;
                }
            }
            for (String fenlei : types) {
                String[] info = fenlei.split("\\$");
                Class c = new Class(info[1],info[0]);
                classes.add(c);
            }
        } catch (Exception e) {
            errMsg = e.getMessage()+"\n"+extend;
        }
    }
    public JSONObject merge(JSONObject base, JSONObject override){
        try {
            Iterator<String> keys = override.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                // put 方法会自动处理覆盖：如果 key 已存在，则更新 value；不存在则新增
                base.put(key, override.optString(key,""));
            }
        } catch (JSONException e) {
        }
        return base;
    }
    private JSONObject parseExtend(String extendStr) {
        JSONObject result = new JSONObject();
        try {
            // 2. 按照 '@' 分割字符串
            String[] items = extendStr.split("@");
            for (String item : items) {
                // 去除首尾空格
                item = item.trim();
                // 如果去除空格后为空，跳过当前项
                if (item.isEmpty())  continue;
                if (item.contains("=")) {
                    // 有等号：正常键值对解析
                    // 使用 limit=2 确保只按第一个 '=' 分割，防止 value 中包含 '=' 被截断
                    String[] keyValue = item.split("=", 2);
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    result.put(key, value);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public JSONObject getMetaProperties(Document doc) {
        JSONObject jsonObject = new JSONObject();
        // 2. 选择所有包含 'property' 属性的 meta 标签
        Elements metaTags = doc.select("meta[property]");
        try {
            for (Element meta : metaTags) {
                // 3. 获取 property 和 content 属性的值
                String propertyValue = meta.attr("property");
                String contentValue = meta.attr("content");

                // 4. 检查 property 和 content 是否存在且不为空
                if (!propertyValue.isEmpty() && !contentValue.isEmpty()) {
                    // 5. 提取 property 中最后一个冒号 ':' 之后的部分
                    int lastColonIndex = propertyValue.lastIndexOf(':');
                    if (lastColonIndex != -1 && lastColonIndex < propertyValue.length() - 1) {
                        String key = propertyValue.substring(lastColonIndex + 1);
                        jsonObject.put(key, contentValue);
                    }
                }
            }
            String title = jsonObject.optString("title"),name = title;
            if(!jsonObject.has("title"))name = doc.title();
            if (name.length() > 10) {
                if(name.contains("《")) name = title.replaceAll("^.*?《|》.*$|^.*$\"", "");
                else name = title.replaceAll("(全集|免费).*", "");
            }
            jsonObject.put("title", name);
        } catch (Exception e) {
        }
        return jsonObject;
    }
    public String getVal(String key){
        return ext.optString(key, "");
    }

    public String getVal(String key,String dval){
        return ext.optString(key, dval);
    }
    private String getVals(String key,String key1){
        String v = ext.optString(key);
        return v==null?ext.optString(key1,""):v;
    }
    @Override
    public String homeContent(boolean filter) {
        try {
            Result result = Result.get().classes(classes);
            return result.toString();
        } catch (Exception e) {
            if(isdebug) Init.show(e.getMessage());
        }
        return "";
    }
    @Override
    public String homeVideoContent() {
        try {
            Result result = getVods(classes.get(0).getTypeId(), "1");
            return result.toString();
        } catch (Exception e) {
        }
        return "";
    }
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if(errMsg!=null)Init.show(errMsg);
            Result result = getVods(tid, pg);
            return result.string();
        } catch (Exception e) {
            if(isdebug) Init.show(e.getMessage());
        }
        return "";
    }
    public String getUrl(String siteUrl, String url){
        if(!url.startsWith("http"))url = siteUrl+url;
        return url;
    }
    public Result getVods(String tid, String pg) {
        List<Vod> list = new ArrayList<>();
        String furl = siteUrl + tid +pg+ wUrl;
        if (!pageUrl.isEmpty() && pageUrl.contains("{")) {
            furl = pageUrl.replace("{tid}", tid).replace("{page}", pg);
        } else if (tid.contains("%")) furl = siteUrl + tid.replace("%", pg);
        else if (Utils.isNumeric(tid) && !pageUrl.isEmpty()) {
            String purl = pageUrl.replaceFirst("%", tid);
            furl = siteUrl + purl.replace("%", pg);
        }
        if(isdebug) Init.show("列表: " + furl);
        Document doc2 = Jsoup.parse(OkHttp.string(furl, getHeaders()));
        String elbox = getVal("elbox");
        String elurl = getVal("elurl","@href");
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
        Vod vod = new Vod();
        try {
            String url = ids.get(0);
            String[] info = url.split("\\$\\$\\$");
            String id = info[0];
            if(isdebug) Init.show("info:"+id);
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
            String iform = getVals("iform","ifrom");
            String iurls = getVal("iurls");
            Document doc = Jsoup.parse(OkHttp.string(id, getHeaders()));
            if (!iboxHtml.isEmpty()) {
                Element el = doc.selectFirst(iboxHtml);
                if(el!=null) elBoxHtml = el.html();
            }
            vod.setTypeName("QQ解析");
            if (!ibox.isEmpty()) {
                String rbox = ibox.replace(":eq(%)", "");
                Elements els = doc.select(rbox);
                for (Element el : els) {
                    getValue(el.text(),vod);
                }
            }
            String jsa=vod.vodTag,sourceName=null;
            String name = getText(doc,iname);
            if(isdebug) Init.show("info Name:"+name);
            String pic = getText(doc, ipic),gname="播放";
            if (info.length > 2) {
                if(name.isEmpty())name = info[2];
                if(pic.isEmpty())pic = info[1];
            }
            if (name.isEmpty() || pic.isEmpty()) {
                JSONObject mjson = getMetaProperties(doc);
                vod.setVodName(mjson.optString("title"));
                if (mjson.length()>1) {
                    pic=mjson.optString("image");
                    vod.setVodContent(mjson.optString("description"));
                    vod.setVodDirector(mjson.optString("director"));
                    vod.setVodActor(mjson.optString("actor"));
                    vod.setVodYear(mjson.optString("date"));
                }
            }
            if (pic.isEmpty()) pic = getStrByRegexkh("https?://.*?\\.(png|jpg)", doc.html());
            if(!name.isEmpty())vod.setVodName(name);
            vod.setVodPic(getUrl(siteUrl,pic));
            vod.setVodId(url);
            vod.setVodName(name);
            vod.setVodPic(getUrl(siteUrl,pic));
            String content = getText(doc, icontent);
            if(!content.isEmpty()) vod.setVodContent(content);
            String year=getText(doc, iyear);
            if(!year.isEmpty())vod.setVodYear(year);
            vod.setVodRemarks(getText(doc,iremark));
            String tag = getText(doc, itag);
            String jsnum = getText(doc, ijsnum);
            if(jsnum.isEmpty()&&jsa!=null)jsnum=jsa;
            if (!jsnum.isEmpty() || !tag.isEmpty()) {
                if(!jsnum.isEmpty()) {
                    jsnum = jsnum.trim();
                    tag = tag+"   评分：无 "+jsnum;
                }
                vod.setVodTag(tag);
                String idirectort = getText(doc, idirector);
                if(!idirectort.isEmpty())vod.setVodDirector(idirectort);
                if(vod.vodDirector!=null&&vod.vodDirector.isEmpty())vod.setVodDirector("未知");
                String iactort = getText(doc, iactor);
                if(!iactort.isEmpty())vod.setVodActor(iactort);
                if(vod.vodActor!=null&&vod.vodActor.isEmpty())vod.setVodActor("未知");
            }
            if(vod.vodRemarks!=null&&vod.vodRemarks.isEmpty()) vod.setVodRemarks(jsnum);
            Map<String, String> sites = new LinkedHashMap<>();
            String tabfirst = getVal("tabfirst");
            if (tabfirst.equals("xiu")) {
                vod.setVodPlayFrom("嗅探");
                vod.setVodPlayUrl(gname+"$"+id);
            }else {
                Elements sources = null;
                Elements sourceList = doc.select(iurls);
                if (sourceList.isEmpty()) Init.show("未找到视频播放链接信息 url:"+url);
                String iurlsn = getVal("iurlsn");
                int z=-1,ilen=sourceList.size();

                if(!iform.equals("线路")) {
                    sources = doc.select(iform);
                    if (sources.isEmpty())sources=null;
                    else ilen= sources.size();
                }
                if(Utils.isNumeric(iurlsn)) z = Integer.parseInt(iurlsn);
                for (int i = 0; i < ilen; i++) {
                    if(sources!=null) {
                        Element source = sources.get(i);
                        sourceName = source.text();
                    } else sourceName = "线路"+(i+1);

                    Elements playList = sourceList.get(i).select("a");
                    List<String> vodItems = new ArrayList<>();
                    for (int j = 0; j < playList.size(); j++) {
                        Element e = playList.get(j);
                        String text = e.text();
                        if(z>-1){
                            if(j<z-1)continue;
                        } else if (!iurlsn.isEmpty()) {
                            if(Utils.matcher(iurlsn, text).matches())continue;
                        }
                        vodItems.add(Trans.get(text) + "$" + getUrl(siteUrl, e.attr("href")));
                    }
                    if (vodItems.size() > 0) {
                        List<String> sortedList = sortEpisodes(vodItems);
                        sites.put(sourceName, TextUtils.join("#", sortedList));
                    }
                }
                if (sites.size() > 0) {
                    if (!tabfirst.isEmpty()) {
                        sites=moveKeyToFirst(sites, tabfirst);
                    }
                    String lineSort = getVal("lineSort");
                    if (!lineSort.isEmpty()) {
                        String [] arr=lineSort.split("\\|");
                        sites = sortMapByList(sites, arr);
                    }
                    vod.setVodPlayFrom(TextUtils.join("$$$", sites.keySet()));
                    vod.setVodPlayUrl(TextUtils.join("$$$", sites.values()));
                }
            }
        } catch (Exception e) {
            Init.show(e.getMessage());
            return "";
        }
        return Result.string(vod);
    }

    public List<String> sortEpisodes(List<String> episodes) {
        // 1. 处理空值或空列表
        if (episodes == null || episodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 预编译正则表达式（避免在排序中重复编译，提升性能）
        final Pattern pattern = Pattern.compile(".*(Ep|EP|E|第)(\\d+)[\\.|集]?.*");

        // 3. 创建一个新列表，避免修改原始传入的列表（保持与原 Stream 代码行为一致）
        List<String> sortedList = new ArrayList<>(episodes);

        // 4. 使用传统的 Collections.sort 配合自定义 Comparator
        Collections.sort(sortedList, new Comparator<String>() {
            @Override
            public int compare(String ep1, String ep2) {
                int num1 = extractEpisodeNumber(ep1, pattern);
                int num2 = extractEpisodeNumber(ep2, pattern);
                return Integer.compare(num1, num2);
            }
        });
        return sortedList;
    }

    /**
     * 提取集数数字的辅助方法
     */
    private int extractEpisodeNumber(String episode, Pattern pattern) {
        if (episode == null) return 0;
        Matcher matcher = pattern.matcher(episode);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    public Map<String, String> sortMapByList(Map<String, String> map, String [] arr) {
        List<String> list = Arrays.asList(arr);
        // 1. 使用 LinkedHashMap 保证插入顺序
        Map<String, String> result = new LinkedHashMap<>();

        // 2. 将 List 转为 Set，提高 contains 查找效率 (O(1))
        Set<String> listSet = new HashSet<>(list);

        // 3. 第一轮：按照 List 的正序，把 Map 中存在的 key 放进去
        for (String key : list) {
            if (map.containsKey(key)) {
                result.put(key, map.get(key));
            }
        }
        // 4. 第二轮：遍历原 Map，把不在 List 中的 key 按原顺序追加进去
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!listSet.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    public Map<String, String> moveKeyToFirst(Map<String, String> map, String key) {
        String [] arr=key.split("\\|");
        if(arr.length==1&&!key.contains("\\")&&!map.containsKey(key))return map;
        Map<String, String> newMap = new LinkedHashMap<>();
        for (String k : arr) {
            String mkey = k;
            boolean zflag = mkey.startsWith("regx:");
            if(zflag)mkey=mkey.substring(5);
            if (mkey.contains("\\")||zflag) {
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
    public String getStrByRegexkh(String regex, String htmlStr){
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(htmlStr);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }
    public String getText(Element element,String key){
        if(key.isEmpty()) return "";
        String rhtml = null;
        if(key.contains("(.*?)")){
            rhtml = Utils.getStrByRegex(key, elBoxHtml!=null?elBoxHtml:element.html());
        }else rhtml = getText(element, key, null);
        if(rhtml.endsWith("/")) rhtml = rhtml.replaceAll("(.*?)\\/+$", "$1");
        if(rhtml!=null)rhtml=rhtml.trim();
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
        } catch (Exception e) {
            if(isdebug) Init.show(e.getMessage());
        }
        return value == null ? "" : value;
    }

    public String getValue(String value,Vod vod){
        if(!value.isEmpty()) {
            value = value.replace("//", "/");
            String val = value;
            if(value.contains("http")||Utils.isSpUrl(value)) value = Utils.trim(value);
            if (!value.startsWith("http")) {
                value = value.replace("&nbsp;", " ");
                value = value.replace("详情", "");
                Matcher m = Utils.matcher("(.*)(:|：)(.*)", value);
                if (m.matches()) {
                    value = m.group(3);
                    if (vod != null) {
                        value = value.trim();
                        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
                        if(value.startsWith("/")) value = value.substring(1, value.length());
                        String k = m.group(1);
                        if (k.contains("演员") || k.contains("主演")) vod.setVodActor(value);
                        else if (k.contains("导演")) vod.setVodDirector(value);
                        else if (k.contains("首映")||k.contains("上映")||k.contains("时间")||k.contains("年份")) {
                            if (value.length() < 3) {
                                value = m.group(1).replaceAll("(.*?)(:|：)(.*)", "$3");
                            }
                            if(vod.vodYear==null||value.length()>5)vod.setVodYear(value);
                        }
                        else if (k.contains("集")||k.contains("备注")||k.contains("更新")||k.contains("状态")) {
                            if(Utils.matcher(".*\\d+集.*",value).matches()||value.contains("全集")||value.contains("完结")) vod.setVodTag(value);
                            else if (val.contains("集")) {
                                value = val.replaceAll(".*?(\\d+)集.*", "$1集");
                                vod.setVodTag(value);
                            }
                        } else if (k.contains("简介") || k.contains("介绍") || k.contains("详情")|| k.contains("剧情")) vod.setVodContent(value);
                    }
                }
            }
        }
        return value;
    }
    private String decodeUnicode(String input) {
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    @Override
    public String searchContent(String key, boolean quick) {
        try {
            List<Vod> list = new ArrayList<>();
            String search = getVal("search");
            key = decodeUnicode(key);
            String sname=null,surl=null,spic = null;
            if (search.isEmpty()) {
                String durl = getVal("idetail","/detail/%.html");
                String id = "id";
                if (!durl.contains("%")) {
                    id = durl.replaceAll(".*\\((\\w+)\\).*", "$1");
                    durl = durl.replace("(" + id + ")", "%");
                }
                String result = OkHttp.string(siteUrl+"/ajax/suggest?mid=1&wd="+key, getHeaders());
                JSONObject response = new JSONObject(result);
                if (response.optInt("code", 0) == 1 && response.optInt("total", 0) > 0) {
                    JSONArray jsonArray = response.getJSONArray("list");
                    for (int i=0;i<jsonArray.length();i++) {
                        JSONObject o = (JSONObject) jsonArray.get(i);
                        sname = o.optString("name", "");
                        if(!sname.startsWith(key))continue;
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
                if(search.contains("%")) target = siteUrl + search.replace("%",key);
                Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
                String sbox= getVal("sbox");
                sname=getVal("sname");
                surl = getVal("surl","@href");
                if(surl.equals("a"))surl="@href";
                spic = getVal("spic");
                String sremarks = getVal("sremarks");
                for (Element element : doc.select(sbox)) {
                    String id =  getText(element,surl);
                    if(id!=null) id = getUrl(siteUrl, id);
                    String name = getText(element, sname);
                    if(!name.startsWith(key))continue;
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
            if(isdebug) Init.show(e.getMessage());
        }
        return "";
    }
    private String resolveUrl(String url) {
        return Utils.fixUrl(siteUrl, url);
    }
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        id = decodeUnicode(id);
        if(isdebug) Init.show("播放id: " + id);

        String iplay= getVal("iplay");
        if(iplay.equals("1")) {
            id = getUrl(siteUrl, id);
            return Result.get().parse().url(id).string();
        }
        Result result = Result.get().url(id).parse(0).header(getHeaders());
        try {
            if (!id.endsWith(".html")) {
                // 1. 【极速通道】判断是否为视频直链
                String lowerId = id.toLowerCase();
                for (String ext : Utils.VIDEO_EXTENSIONS) {
                    if (lowerId.endsWith(ext)) {
                        if(isdebug) Init.show("直接播放id: " + id);
                        return result.string();
                    }
                }
            }
            // 2. 【补全链接】处理相对路径
            String playUrl = resolveUrl(id);
            // 3. 【网络请求】获取播放页源码
            String html = OkHttp.string(playUrl, getHeaders());
            // 4. 【正则极速嗅探】
            String regexPattern = "\"url\"\\s*:\\s*\"([^\"]+\\.(?:m3u8|mp4|flv|ts)[^\"]*)\"";
            Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String realUrl = matcher.group(1).replace("\\/", "/");
                id=resolveUrl(realUrl);
                id = decodeUnicode(id);
                if(isdebug) Init.show("正则播放id: " + id);
                result.url(id);
                return result.string();
            }

            // 5. 【Jsoup 兜底解析】
            Document doc = Jsoup.parse(html);
            // 查找包含 var player_aaaa 的 script 标签
            for (Element script : doc.select("script")) {
                String scriptData = script.data();
                if (scriptData != null && scriptData.contains("var player_aaaa")) {
                    Pattern jsPattern = Pattern.compile("\"url\"\\s*:\\s*\"(.*?)\"");
                    Matcher jsMatcher = jsPattern.matcher(scriptData);
                    if (jsMatcher.find()) {
                        String realUrl = jsMatcher.group(1).replace("\\/", "/");
                        id=resolveUrl(realUrl);
                        id=decodeUnicode(id);
                        if(isdebug) Init.show("aaa播放id: " + id);
                        result.url(id);
                        return result.string();
                    }
                    break; // 找到包含 player_aaaa 的 script 后无需继续
                }
            }

        } catch (Exception e) {
            Init.show("播放页解析出错: " + e.getMessage());
        }
        id = getUrl(siteUrl, id);
        return Result.get().parse().url(id).string();
    }
}
