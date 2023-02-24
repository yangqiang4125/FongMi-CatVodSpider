package com.github.catvod.spider;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.ali.Data;
import com.github.catvod.bean.ali.Item;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttpUtil;
import com.github.catvod.utils.Misc;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.QRCode;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ColaMint & Adam & FongMi
 */
public class Ali extends Spider{

    private final Pattern pattern = Pattern.compile("www.aliyundrive.com/s/([^/]+)(/folder/([^/]+))?");
    private ScheduledExecutorService service;
    private static String authorization;
    private static String refreshToken;
    private long expiresTime;
    private ImageView view;
    private static String szRegx = ".*(Ep|EP|E|第)(\\d+)[\\.|集]?.*";//集数数字正则匹配
    public Ali(){}
    public Ali(String extend){
        init(null,extend);
    }
    @Override
    public void init(Context context, String extend) {
        if (!TextUtils.isEmpty(extend)) {
            if (extend.startsWith("http")) extend = OkHttpUtil.string(extend);
            refreshToken = Prefers.getString("token", extend);
        }
        fetchRule(false,0);
    }

    public static JSONObject fetchRule(boolean flag,int t) {
        try {
            if (flag || Misc.siteRule == null) {
                String json = OkHttpUtil.string(Misc.jsonUrl+"?t="+Time());
                JSONObject jo = new JSONObject(json);
                if(t==0) {
                    String[] fenleis = getRuleVal(jo,"fenlei", "").split("#");
                    for (String fenlei : fenleis) {
                        String[] info = fenlei.split("\\$");
                        jo.remove(info[1]);
                    }
                    Misc.siteRule = jo;
                    String tk = Misc.siteRule.optString("token","");
                    if(!tk.equals("")&&tk.equals(refreshToken)){
                        refreshToken = tk;
                    }
                    Misc.apikey = Misc.siteRule.optString("apikey", "0ac44ae016490db2204ce0a042db2916");
                    szRegx =  Misc.siteRule.optString("szRegx", szRegx);
                }
                return jo;
            }
        } catch (JSONException e) {
        }
        return Misc.siteRule;
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


    private static HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Misc.CHROME);
        headers.put("Referer", "https://www.aliyundrive.com/");
        return headers;
    }

    private static HashMap<String, String> getHeaders(String shareToken) {
        HashMap<String, String> headers = getHeaders();
        if (authorization != null) headers.put("authorization", authorization);
        headers.put("x-share-token", shareToken);
        return headers;
    }

    private String post(String url, JSONObject body) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        return OkHttpUtil.postJson(url, body.toString(), getHeaders());
    }

    private static String post(String url, JSONObject body, String shareToken) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        return OkHttpUtil.postJson(url, body.toString(), getHeaders(shareToken));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0).trim();
        String[] idInfo = url.split("\\$\\$\\$");
        if (idInfo.length > 0)  url = idInfo[0].trim();
        url = Misc.getRealUrl(url);
        idInfo[0]=url;
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            Vod vod = getVod(matcher,idInfo);
            if (vod != null) return Result.string(vod);
        }
        return "";
    }

    public String playerContent(String flag, String id) {
        try {
            if (id.equals("无数据")) return "";
            String[] ids = id.split("\\+");
            String shareId = ids[0];
            String shareToken = ids[1];
            String fileId = ids[2];
            String sub = getSub(shareId, shareToken, ids);
            if (System.currentTimeMillis() > expiresTime) refreshAccessToken();
            while (TextUtils.isEmpty(authorization)) SystemClock.sleep(250);
            if (flag.contains("原画")) {
                return Result.get().url(getDownloadUrl(shareId, shareToken, fileId)).sub(sub).header(getHeaders()).string();
            } else {
                return Result.get().url(getPreviewUrl(shareId, shareToken, fileId)).sub(sub).header(getHeaders()).string();
            }
        } catch (Exception e) {
            return "";
        }
    }
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return playerContent(flag, id);
    }

    private Vod getVod(Matcher matcher, String[] idInfo) throws Exception {
        String shareId = matcher.group(1);
        String shareToken = getShareToken(shareId);
        List<String> playUrls = new ArrayList<>();
        JSONObject object = null;
        if(shareToken.isEmpty()){
            if (idInfo.length == 1) return null;
        }else {
            try {
                String fileId = matcher.groupCount() == 3 ? matcher.group(3) : "";
                JSONObject body = new JSONObject();
                body.put("share_id", shareId);
                String json = post("adrive/v3/share_link/get_share_by_anonymous", body);
                object = new JSONObject(json);
                LinkedHashMap<Item, String> fileMap = new LinkedHashMap<>();
                Map<String, List<String>> subMap = new HashMap<>();
                String f = getParentFileId(fileId, object);
                if (!f.isEmpty()) {
                    listFiles(new Item(f), fileMap, subMap, shareId, shareToken);
                    List<Item> files = new ArrayList<>(fileMap.keySet());
                    for (Item file : files) playUrls.add(Trans.get(file.getDisplayName()) + "$" + fileMap.get(file) + findSubs(file.getName(), subMap));
                }
            } catch (Exception e) {
            }
        }
        List<String> sourceUrls = new ArrayList<>();
        Vod vod = new Vod(); String type = "";
        if (playUrls.isEmpty()) playUrls.add("无数据$无数据");
        else {
            String s = TextUtils.join("#", playUrls);
            sourceUrls.add(s);
            sourceUrls.add(s);
            if (s.contains("4K")) {
                type = "4K";
            }else if (s.contains("4k")) {
                type = "4K";
            }else if (s.contains("1080")) {
                if(!s.contains("1079"))type = "1080";
            }
        }
        String from = "原画%$$$普画%";
        from = from.replace("%", type);
        vod.setVodId(TextUtils.join("$$$",idInfo));
        vod.setVodContent(idInfo[0]);
        String vpic = "https://inews.gtimg.com/newsapp_bt/0/13263837859/1000";
        String vname=object!=null?object.getString("share_name"):"无名称";
        if (idInfo != null) {
            if(idInfo.length>1&&!idInfo[1].isEmpty()) vpic = idInfo[1];
            if(idInfo.length>2&&!idInfo[2].isEmpty()) vname = idInfo[2];
        }
        vod.setVodPic(vpic);
        vod.setVodName(vname);
        vod.setVodPlayUrl(TextUtils.join("$$$", sourceUrls));
        vod.setVodPlayFrom(from);
        vod.setTypeName("阿里云盘");
        return vod;
    }

    private void listFiles(Item folder, LinkedHashMap<Item, String> name2id, Map<String, List<String>> subMap, String shareId, String shareToken) throws Exception {
        listFiles(folder, name2id, subMap, shareId, shareToken, "");
    }

    private void listFiles(Item parent, LinkedHashMap<Item, String> name2id, Map<String, List<String>> subMap, String shareId, String shareToken, String marker) throws Exception {
        JSONObject body = new JSONObject();
        List<Item> folders = new ArrayList<>();
        body.put("limit", 200);
        body.put("share_id", shareId);
        body.put("parent_file_id", parent.getFileId());
        body.put("order_by", "name");
        body.put("order_direction", "ASC");
        if (marker.length() > 0) body.put("marker", marker);
        Item item = Item.objectFrom(post("adrive/v3/file/list", body, shareToken));
        for (Item file : item.getItems()) {
            if (file.getType().equals("folder")) {
                folders.add(file);
            } else if (file.getCategory().equals("video") || file.getCategory().equals("audio")) {
                name2id.put(file, shareId + "+" + shareToken + "+" + file.getFileId());
            } else if (Misc.isSub(file.getExt())) {
                String key = file.removeExt();
                if (!subMap.containsKey(key)) subMap.put(key, new ArrayList<>());
                subMap.get(key).add(key + "@" + file.getFileId() + "@" + file.getExt());
            }
        }
        if (item.getNextMarker().length() > 0) {
            listFiles(parent, name2id, subMap, shareId, shareToken, item.getNextMarker());
        }
        for (Item folder : folders) {
            listFiles(folder, name2id, subMap, shareId, shareToken);
        }
    }

    private String getParentFileId(String fileId, JSONObject shareInfo) throws Exception {
        JSONArray array = shareInfo.getJSONArray("file_infos");
        if (!TextUtils.isEmpty(fileId)) return fileId;
        if (array.length() == 0) return "";
        JSONObject fileInfo = array.getJSONObject(0);
        if (fileInfo.getString("type").equals("folder")) return fileInfo.getString("file_id");
        if (fileInfo.getString("type").equals("file") && fileInfo.getString("category").equals("video")) return "root";
        return "";
    }

    private void refreshAccessToken() {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            body.put("grant_type", "refresh_token");
            JSONObject object = new JSONObject(post("https://auth.aliyundrive.com/v2/account/token", body));
            authorization = object.getString("token_type") + " " + object.getString("access_token");
            expiresTime = System.currentTimeMillis() + object.getInt("expires_in") * 1000L;
            refreshToken = object.getString("refresh_token");
            SpiderDebug.log("refresh token: " + refreshToken);
        } catch (JSONException e) {
            authorization = null;
            e.printStackTrace();
            checkService();
            getQRCode();
        }
    }

    private String findSubs(String name, Map<String, List<String>> subMap) {
        name = name.substring(0, name.lastIndexOf("."));
        List<String> subs = subMap.get(name);
        if (subs != null && subs.size() > 0) return combineSubs(subs);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : subMap.entrySet()) sb.append(combineSubs(entry.getValue()));
        return sb.toString();
    }

    private String combineSubs(List<String> subs) {
        StringBuilder sb = new StringBuilder();
        for (String sub : subs) sb.append("+").append(sub);
        return sb.toString();
    }

    private String getSub(String shareId, String shareToken, String[] ids) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String text : ids) {
                if (!text.contains("@")) continue;
                String[] arr = text.split("@");
                String url = Proxy.getUrl() + "?do=ali&type=sub&share_id=" + shareId + "&share_token=" + shareToken + "&file_id=" + arr[1];
                sb.append(Trans.get(arr[0])).append("#").append(Misc.getSubMimeType(arr[2])).append("#").append(url).append("$$$");
            }
            return Misc.substring(sb.toString(), 3);
        } catch (Exception e) {
            return "";
        }
    }

    private String getShareToken(String shareId) {
        try {
            JSONObject body = new JSONObject();
            body.put("share_id", shareId);
            body.put("share_pwd", "");
            String json = post("v2/share_link/get_share_token", body);
            return new JSONObject(json).getString("share_token");
        } catch (JSONException e) {
            Init.show("来晚啦，该分享已失效。");
            e.printStackTrace();
            return "";
        }
    }

    private String getPreviewQuality(JSONArray taskList) throws Exception {
        for (String templateId : Arrays.asList("FHD", "HD", "SD", "LD")) {
            for (int i = 0; i < taskList.length(); ++i) {
                JSONObject task = taskList.getJSONObject(i);
                if (task.getString("template_id").equals(templateId)) {
                    return task.getString("url");
                }
            }
        }
        return taskList.getJSONObject(0).getString("url");
    }

    private String getPreviewUrl(String shareId, String shareToken, String fileId) {
        try {
            JSONObject body = new JSONObject();
            body.put("file_id", fileId);
            body.put("share_id", shareId);
            body.put("template_id", "");
            body.put("category", "live_transcoding");
            String json = post("v2/file/get_share_link_video_preview_play_info", body, shareToken);
            JSONArray taskList = new JSONObject(json).getJSONObject("video_preview_play_info").getJSONArray("live_transcoding_task_list");
            Map<String, List<String>> respHeaders = new HashMap<>();
            OkHttpUtil.stringNoRedirect(getPreviewQuality(taskList), getHeaders(), respHeaders);
            return OkHttpUtil.getRedirectLocation(respHeaders);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String getDownloadUrl(String shareId, String shareToken, String fileId) {
        try {
            JSONObject body = new JSONObject();
            body.put("file_id", fileId);
            body.put("share_id", shareId);
            body.put("expire_sec", 600);
            String json = post("v2/file/get_share_link_download_url", body, shareToken);
            String url = new JSONObject(json).optString("download_url");
            Map<String, List<String>> respHeaders = new HashMap<>();
            OkHttpUtil.stringNoRedirect(url, getHeaders(), respHeaders);
            return OkHttpUtil.getRedirectLocation(respHeaders);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
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
            Document doc = Jsoup.parse(OkHttpUtil.string(url));
            vname = doc.select("head > title").text();
        }
        if (vname.isEmpty()) {
            vname = url;
        }
        if (vpic.isEmpty()) {
            vpic = Misc.getWebName(url,1);
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

    public static Object[] vod(Map<String, String> params) {
        String shareId = params.get("share_id");
        String shareToken = params.get("share_token");
        String fileId = params.get("file_id");
        String text = OkHttpUtil.string(getDownloadUrl(shareId, shareToken, fileId), getHeaders(shareToken));
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(text.getBytes());
        return result;
    }

    private void checkService() {
        if (service != null) service.shutdownNow();
        if (view != null) Init.run(() -> Misc.removeView(view));
    }

    private void getQRCode() {
        Data data = Data.objectFrom(OkHttpUtil.string("https://easy-token.cooluc.com/qr"));
        if (data != null) Init.run(() -> showCode(data));
        service = Executors.newScheduledThreadPool(1);
        if (data != null) service.scheduleAtFixedRate(() -> {
            JsonObject params = new JsonObject();
            params.addProperty("t", data.getData().getT());
            params.addProperty("ck", data.getData().getCk());
            Data result = Data.objectFrom(OkHttpUtil.postJson("https://easy-token.cooluc.com/ck", params.toString()));
            if (result.hasToken()) setToken(result.getData().getRefreshToken());
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void setToken(String value) {
        Prefers.put("token", refreshToken = value);
        Init.show("请重新进入播放页");
        checkService();
    }

    private void showCode(Data data) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        Misc.addView(view = create(data.getData().getCodeContent()), params);
        Init.show("请使用阿里云盘 App 扫描二维码");
    }

    private ImageView create(String value) {
        ImageView view = new ImageView(Init.context());
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageBitmap(QRCode.getBitmap(value, 250, 2));
        return view;
    }
}
