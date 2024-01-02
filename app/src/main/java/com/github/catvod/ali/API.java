package com.github.catvod.ali;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.UrlQuerySanitizer;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.FrameLayout;
import android.widget.EditText;
import java.util.concurrent.TimeUnit;
import android.widget.ImageView;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.github.catvod.bean.ali.*;
import com.github.catvod.utils.QRCode;
import java.util.concurrent.TimeoutException;
import com.github.catvod.utils.ProxyVideo;
import android.text.TextUtils;
import android.util.Log;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Ali;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.Proxy;
import com.github.catvod.spider.PushAgentQQ;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.locks.ReentrantLock;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class API {
    private final Map<String, Map<String, String>> m3u8MediaMap;
    private final Map<String, String> shareDownloadMap;
    private final Map<String, String> downloadMap;
    private final ReentrantLock lock;
    private ScheduledExecutorService service;
    private AlertDialog dialog;
    private final List<String> tempIds;
    private final List<String> quality;
    public final Map<String,String> qmap;
    private String shareToken;
    private Auth auth;
    private Auth auths;
    private String shareId;
    private String refreshUrl;
    private String CLIENT_ID;
    private String parentDir = "root";
    private String updateTk = "0";
    private String vodInfo = "1";
    private String updateAliData;
    private String refreshTokenOpen="";
    private String refreshToken="";
    public String jtype="0";
    private String dkey;
    private static class Loader {
        static volatile API INSTANCE = new API();
    }

    public static API get() {
        return Loader.INSTANCE;
    }

    public API() {
        downloadMap = new HashMap<>();
        m3u8MediaMap = new HashMap<>();
        shareDownloadMap = new HashMap<>();
        lock = new ReentrantLock();
        tempIds = new ArrayList<>();
        qmap = new LinkedHashMap<>();
        auth = Auth.objectFrom(Prefers.getString("aliyundrive"));
        if(auth.isEmpty())cleanToken();
        quality = Arrays.asList("UHD","QHD","FHD", "HD", "SD", "LD");
        qmap.put("2K","QHD");
        qmap.put("极清","QHD");
        qmap.put("智能","QHD");
        qmap.put("超清","FHD");
        qmap.put("高清","HD");
        qmap.put("标清", "SD");
        qmap.put("流畅", "LD");
    }

    public void setAuth(boolean flag){
        if (Utils.tokenInfo.length()>10) {
            auths = Auth.objectFrom(Utils.tokenInfo);
            if(!auths.isEmpty()){
                auth = auths;
                auth.save();
            } else auths = null;
        } else {
            auths = null;
        }
        refreshUrl = getVal("refreshUrl", "");
        parentDir = getVal("parentDir", "root");
        vodInfo = getVal("vodInfo", "1");
        updateTk = getVal("updateTk", "0");
        updateAliData = getVal("updateAliData", "");
        dkey = getVal("dkey", "");
        if(refreshUrl.length()<10) refreshUrl = "https://api.nn.ci/";
    }
    public Auth getAuth(){
        return auth;
    }
    public String getVal(String key,String dval){
        String tk = Utils.siteRule.optString(key,dval);
        return tk;
    }
    public void cleanToken() {
        auth.clean();
        Prefers.put("aliyundrive", "");
    }
    public void alert(String msg) {
        boolean aflag = Prefers.getBoolean("alert", false);
        if(aflag) Init.show(msg);
    }

    public boolean isRefresh() {
        if(auth.getRefreshToken().isEmpty()||auth.isEmpty())return true;
        return false;
    }
    public void setShareId(String shareId) {
        this.shareId = shareId;
        refreshShareToken();
    }

    public HashMap<String, String> getHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Utils.CHROME);
        headers.put("Referer", "https://www.aliyundrive.com/");
        return headers;
    }

    private HashMap<String, String> getHeaderAuth() {
        HashMap<String, String> headers = getHeader();
        headers.put("authorization", auth.getAccessToken());
        headers.put("X-Canary", "client=Android,app=adrive,version=v4.3.1");
        headers.put("x-share-token", shareToken);
        return headers;
    }

    private HashMap<String, String> getHeaderOpen() {
        HashMap<String, String> headers = getHeader();
        headers.put("authorization", auth.getAccessTokenOpen());
        return headers;
    }

    private String post(String url, JSONObject body) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        return OkHttp.postJson(url, body.toString(), getHeader());
    }

    private String auth(String url, JSONObject body, boolean retry) {
        return auth(url, body.toString(), retry);
    }

    private String auth(String url, String json, boolean retry) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        String result = OkHttp.postJson(url, json, getHeaderAuth());
        Log.e("auth", result);
        if (retry && checkAuth(result)) return auth(url, json, false);
        return result;
    }

    private String oauth(String url, String json, boolean retry) {
        url = url.startsWith("https") ? url : "https://open.aliyundrive.com/adrive/v1.0/" + url;
        String result = OkHttp.postJson(url, json, getHeaderOpen());
        Log.e("oauth", result);
        if (retry && checkOpen(result)) return oauth(url, json, false);
        return result;
    }

    private boolean checkAuth(String result) {
        //if (result.contains("Invalid")) alert("checkAuth:"+result);
        if (result.contains("AccessTokenInvalid")) {
            refreshToken = auth.getRefreshToken();
            return refreshAccessToken();
        }
        if (result.contains("ShareLinkTokenInvalid") || result.contains("InvalidParameterNotMatch")) return refreshShareToken();
        if (result.contains("QuotaExhausted")) {
            Init.show("账号容量不够啦");
            Init.execute(this::deleteAll);
            return true;
        }
        refreshToken = "";
        return false;
    }

    private boolean checkOpen(String result) {
        //if (result.contains("Invalid")) alert("checkOpen:"+result);
        if (result.contains("AccessTokenInvalid")) {
            refreshTokenOpen = auth.getRefreshTokenOpen();
            auth.setAccessTokenOpen("");
            return refreshOpenToken();
        }
        if(auths==null){
            jtype="3";            
            updateData();
        }
        refreshTokenOpen = "";
        return false;
    }

    public void updateData() {
        if (!updateAliData.isEmpty()) {            
            postData(dkey+"tokenInfo "+auth.toJson(),"jar"+jtype);
            auths = auth;
        }
    }

    public void postData(String key,String type) {
        try {
            String [] arr= updateAliData.split(",");
            JSONObject params = new JSONObject();
            params.put("pwd", arr[1]);
            params.put("dkey", dkey);
            params.put("type", type);
            params.put("key", key);
            OkHttp.postJson(arr[0], params.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean refreshAccessToken() {
        return refreshAccessToken(true);
    }
    public boolean refreshAccessToken(boolean iflag) {
        try {
            if(auth.getRefreshToken().isEmpty()||!refreshToken.isEmpty()||!iflag){
                if(iflag) Ali.fetchRule(true, 0);
                if (!auth.isEmpty()&&!refreshToken.equals(Utils.refreshToken)) {
                    if(!auth.getAccessToken().isEmpty()&&!auth.getRefreshToken().equals(refreshToken))return true;                        
                }
                if (Utils.isToken(Utils.refreshToken))auth.setRefreshToken(Utils.refreshToken);
            }
            if(auth.getRefreshToken().isEmpty()) throw new Exception("refreshToken无效");
            if(updateTk.equals("0"))return true;
            JSONObject body = new JSONObject();
            String token = auth.getRefreshToken();
            if (token.startsWith("http")) token = OkHttp.string(token).replaceAll("[^A-Za-z0-9]", "");
            body.put("refresh_token", token);
            body.put("grant_type", "refresh_token");
            String json = post("https://auth.aliyundrive.com/v2/account/token", body);
            JSONObject object = new JSONObject(json);
            auth.setRefreshToken(object.getString("refresh_token"));
            //if(auth.getRefreshToken().isEmpty())throw new Exception(json);
            refreshToken = "";
            auth.setUserId(object.getString("user_id"));
            auth.setNickName(object.optString("nick_name"));
            auth.setDriveId(object.getString("default_drive_id"));
            auth.setAccessToken(object.getString("token_type") + " " + object.getString("access_token"));
            oauthRequest();
            return true;
        } catch (Exception e) {
            if (e instanceof TimeoutException) return onTimeout();
            cleanToken();
            String qrcode = getVal("qrcode", "0");
            if (qrcode.equals("1")) {
                startPen();
            } else {
                postData(e.getMessage(), "msg");
                Init.show("阿里账号已失效，请稍后重试~");                
            }
            return true;
        } finally {
            while (auth.getAccessToken().isEmpty()) SystemClock.sleep(250);
        }
    }

    private void oauthRequest() throws Exception {
        SpiderDebug.log("OAuth Request...");
        if(CLIENT_ID==null) CLIENT_ID = getVal("CLIENT_ID", "76917ccccd4441c39457a04f6084fb2f");
        JSONObject body = new JSONObject();
        body.put("authorize", 1);
        body.put("scope", "user:base,file:all:read,file:all:write");
        JSONObject object = new JSONObject(auth("https://open.aliyundrive.com/oauth/users/authorize?client_id=" + CLIENT_ID + "&redirect_uri=https://alist.nn.ci/tool/aliyundrive/callback&scope=user:base,file:all:read,file:all:write&state=", body, true));
        Log.e("DDD", object.toString());
        //if(object.toString().contains("not"))alert("oauthRequest:"+object.toString());
        oauthRedirect(object.getString("redirectUri").split("code=")[1]);
    }

    private void oauthRedirect(String code) throws Exception {
        SpiderDebug.log("OAuth Redirect...");
        JSONObject body = new JSONObject();
        body.put("code", code);
        body.put("grant_type", "authorization_code");

        JSONObject object = new JSONObject(post(refreshUrl+"alist/ali_open/code", body));
        auth.setRefreshTokenOpen(object.getString("refresh_token"));
        auth.setAccessTokenOpen(object.optString("token_type") + " " + object.optString("access_token"));
        jtype = "1";
        save();
    }
    public void save(){
        String time = Utils.getTime();
        Utils.etime = Utils.getLongTime(time);
        auth.setTime(time);
        auth.setJtype(jtype);
        auth.save();
        updateData();
    }
    private boolean refreshOpenToken() {
        try {
            Ali.fetchRule(true, 0);
            if(updateTk.equals("0"))return true;
            if (auth.getRefreshTokenOpen().isEmpty()) oauthRequest();
            if(!auth.isEmpty()&&!auth.getRefreshTokenOpen().equals(refreshTokenOpen))return true;
            SpiderDebug.log("refreshAccessTokenOpen...");
            JSONObject body = new JSONObject();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", auth.getRefreshTokenOpen());
            JSONObject object = new JSONObject(post(refreshUrl + "alist/ali_open/token", body));
            Log.e("DDD", object.toString());
            auth.setRefreshTokenOpen(object.optString("refresh_token"));
            auth.setAccessTokenOpen(object.optString("token_type") + " " + object.optString("access_token"));
            jtype="2";
            save();
            return true;
        } catch (Exception e) {
            if(e.getMessage().contains("Too Many Requests"))Init.show("请求过多被封IP，明天再看");
            else{
                alert(e.getMessage());
                cleanToken();
            }
            return false;
        }
    }

    public boolean refreshShareToken() {
        try {
            SpiderDebug.log("refreshShareToken...");
            JSONObject body = new JSONObject();
            body.put("share_id", shareId);
            body.put("share_pwd", "");
            JSONObject object = new JSONObject(post("v2/share_link/get_share_token", body));
            shareToken = object.getString("share_token");
            return true;
        } catch (Exception e) {
            Init.show("来晚啦，该分享已失效");
            return false;
        }
    }

    public Vod getVod(String url, String fileId,String shareId) {
        String[] idInfo = url.split("\\$\\$\\$");
        List<String> playUrls = new ArrayList<>();
        Vod vod = new Vod();
        JSONObject object = null;String vname=null;
        try {
            vod.setVodId(TextUtils.join("$$$",idInfo));
            vod.setVodContent(idInfo[0]);
            String vpic = "http://image.xinjun58.com/sp/pic/bg/ali.jpg";
            if (idInfo != null) {
                if(idInfo.length>1&&!idInfo[1].isEmpty()) vpic = idInfo[1];
                if(idInfo.length>2&&!idInfo[2].isEmpty()) vname = idInfo[2];
            }
            vod.setVodPic(vpic);
            vod.setVodName(vname);
            vod.setTypeName("阿里云盘");
            if (Utils.isPic==1) {
                Vod vod2 = getVodInfo(vname, vod, idInfo);
                if(vod2!=null) vod = vod2;
            }
            String tag = vod.vodTag;
            if(tag==null||tag.isEmpty()) tag = "推荐";
            tag = tag + ";" + new Gson().toJson(auth);
            vod.setVodTag(tag);

            setShareId(shareId);
            JSONObject body = new JSONObject();
            body.put("share_id", shareId);
            String json = post("adrive/v3/share_link/get_share_by_anonymous", body);
            object = new JSONObject(json);
            vname=object!=null?object.getString("share_name"):"未找到";
            List<Item> files = new ArrayList<>();
            List<Item> subs = new ArrayList<>();
            listFiles(shareId, new Item(getParentFileId(fileId,object)), files, subs);
            if(files.isEmpty()){               
                Init.show("资源已失效~");
                return vod;
            }
            if (Utils.getStr(vod.getVodName()).isEmpty())vod.setVodName(vname);
            for (Item file : files) playUrls.add(file.getDisplayName() + "$" + shareId + "+" + file.getFileId() + findSubs(file.getName(), subs));
        } catch (Exception e) {
            return vod;
        }
        boolean fp = playUrls.isEmpty();
        String s = TextUtils.join("#", playUrls);
        List<String> sourceUrls = new LinkedList<>();
        String type = "";
        if (!fp){
            if (s.contains("4K")) {
                type = "4K";
            }else if (s.contains("4k")) {
                type = "4K";
            }else if (s.contains("1080")) {
                if(!s.contains("1079"))type = "1080";
            }
        }
        String from = getVal("aliFrom","原画%$$$普话%"),fromkey="";
        from = "原画%。$$$原画F%。$$$普画%。$$$原画i%$$$普画i%";
        String jxStr = Utils.getBx(s);
        from = from.replace("%", type);
        String [] fromArr = from.split("\\$\\$\\$");
        for (int i=0; i < fromArr.length; i++) {
            fromkey = fromArr[i];
            if(i==0||fromkey.contains("。"))sourceUrls.add(jxStr);
            else sourceUrls.add(s);
        }
        from = from.replace("。", "");
        vod.setVodPlayUrl(TextUtils.join("$$$", sourceUrls));
        vod.setVodPlayFrom(from);
        return vod;
    }

    /**
     * @param string
     * @return 转换之后的内容
     * @Title: unicodeDecode
     * @Description: unicode解码 将Unicode的编码转换为中文
     */
    public static String unicodeDecode(String string) {
        Pattern pattern = Pattern.compile("(\\\\u(\\p{XDigit}{4}))");
        Matcher matcher = pattern.matcher(string);
        char ch;
        while (matcher.find()) {
            ch = (char) Integer.parseInt(matcher.group(2), 16);
            string = string.replace(matcher.group(1), ch + "");
        }
        return string;
    }

    public Vod getVodInfo(String key,Vod vod,String[] idInfo){
        try {
            if(key.equals("磁力")||key.contains("."))return vod;
            int sid = -1;
            if(idInfo.length>3&&Utils.isNumeric(idInfo[3])) {
                sid = Integer.parseInt(idInfo[3]);
                if(sid<1)return vod;
            }else {
                if(vodInfo.equals("1"))sid=1;
            }
            if (sid == 1) {
                String url = PushAgentQQ.douban_api_host+"/search/movie?q="+key+"&count=1&page_start=0&apikey=" + Utils.apikey + "&channel=Douban";
                String json = OkHttp.string(url, PushAgentQQ.getHeaderDB());
                JSONObject sp = new JSONObject(json);
                JSONArray ao = sp.getJSONArray("items");
                if(ao.length()==0)return null;
                JSONObject jo = ao.getJSONObject(0);
                String spId = jo.getString("target_id");
                url =" https://frodo.douban.com/api/v2/movie/"+spId+"?count=1&page_start=0&apikey=" + Utils.apikey + "&channel=Douban";
                json = OkHttp.string(url, PushAgentQQ.getHeaderDB());
                String str = unicodeDecode(json);
                sp = new JSONObject(str);
                String card_subtitle= sp.optString("card_subtitle","");
                String tag = "",rating,pic="",title="",intro="",actors="",episodes_info,directors="",area = "",year="";
                if (!card_subtitle.isEmpty()) {
                    String [] subs = card_subtitle.split(" / ",4);
                    area = subs[1];
                    tag = subs[1]+" / "+subs[2];
                }
                jo = sp.getJSONObject("pic");
                pic = jo.getString("normal");
                pic = Ali.getPicAgent(pic);
                jo = sp.getJSONObject("rating");
                rating = jo.optString("value","0")+"分";
                title = sp.optString("title");
                intro = sp.optString("intro");
                episodes_info = sp.optString("episodes_count","0")+"集全";
                String pubdate = sp.optString("pubdate","");
                year = pubdate.replaceAll(".*?(\\d+-\\d+-\\d+).*","$1");
                ao = sp.getJSONArray("actors");
                for (int i = 0; i < ao.length(); i++) {
                    jo = ao.getJSONObject(i);
                    actors += jo.getString("name")+" / ";
                }
                if (!actors.isEmpty()) {
                    actors = actors.substring(0, actors.length() - 3);
                } else actors = "未知";

                ao = sp.getJSONArray("directors");
                for (int i = 0; i < ao.length(); i++) {
                    jo = ao.getJSONObject(i);
                    directors += jo.getString("name")+" / ";
                }
                if (!directors.isEmpty()) {
                    directors = directors.substring(0, directors.length() - 3);
                } else directors = "未知";
                tag = tag+"   评分："+rating+" "+episodes_info;
                vod.setVodName(key);
                vod.setVodTag(tag);
                vod.setVodContent(intro);
                vod.setVodDirector(directors);
                vod.setVodActor(actors);
                vod.setVodYear(year);
                vod.setVodArea(area);
                vod.setVodPic(pic);
            }
        } catch (Exception e) {
        }
        return vod;
    }


    private void listFiles(String shareId, Item folder, List<Item> files, List<Item> subs) {
        listFiles(shareId, folder, files, subs, "");
    }

    private void listFiles(String shareId, Item parent, List<Item> files, List<Item> subs, String marker) {
        List<Item> folders = new ArrayList<>();
        JsonObject param = new JsonObject();
        param.addProperty("limit", 200);
        param.addProperty("share_id", shareId);
        param.addProperty("parent_file_id", parent.getFileId());
        param.addProperty("order_by", "name");
        param.addProperty("order_direction", "ASC");
        if (marker.length() > 0) param.addProperty("marker", marker);
        Item item = Item.objectFrom(auth("adrive/v3/file/list", param.toString(), true));
        for (Item file : item.getItems()) {
            if (file.getType().equals("folder")) {
                folders.add(file);
            } else if (file.getCategory().equals("video") || file.getCategory().equals("audio")) {
                files.add(file.parent(parent.getName()));
            } else if (Utils.isSub(file.getExt())) {
                subs.add(file);
            }
        }
        if (item.getNextMarker().length() > 0) {
            listFiles(shareId, parent, files, subs, item.getNextMarker());
        }
        for (Item folder : folders) {
            listFiles(shareId, folder, files, subs);
        }
    }

    private String getParentFileId(String fileId, JSONObject shareInfo) throws Exception {
        if (!TextUtils.isEmpty(fileId)) return fileId;
        JSONArray array = shareInfo.getJSONArray("file_infos");
        if (array.length() == 0) return "";
        JSONObject fileInfo = array.getJSONObject(0);
        if (fileInfo.getString("type").equals("folder")) return fileInfo.getString("file_id");
        if (fileInfo.getString("type").equals("file") && fileInfo.getString("category").equals("video")) return parentDir;
        return "root";
    }

    private void pair(String name1, List<Item> items, List<Item> subs) {
        for (Item item : items) {
            String name2 = Utils.removeExt(item.getName()).toLowerCase();
            if (name1.contains(name2) || name2.contains(name1)) subs.add(item);
        }
    }

    private String findSubs(String name1, List<Item> items) {
        List<Item> subs = new ArrayList<>();
        pair(Utils.removeExt(name1).toLowerCase(), items, subs);
        if (subs.isEmpty()) subs.addAll(items);
        StringBuilder sb = new StringBuilder();
        for (Item sub : subs) sb.append("+").append(Utils.removeExt(sub.getName())).append("@@@").append(sub.getExt()).append("@@@").append(sub.getFileId());
        return sb.toString();
    }

    public List<Sub> getSubs(String[] ids) {
        List<Sub> sub = new ArrayList<>();
        for (String text : ids) {
            if (!text.contains("@@@")) continue;
            String[] split = text.split("@@@");
            String name = split[0];
            String ext = split[1];
            String url = Proxy.getUrl() + "?do=ali&type=sub&shareId=" + ids[0] + "&fileId=" + split[2];
            sub.add(Sub.create().name(name).ext(ext).url(url));
        }
        return sub;
    }

    public String getShareDownloadUrl(String shareId, String fileId) {
        try {
            if (shareDownloadMap.containsKey(fileId) && shareDownloadMap.get(fileId) != null && !isExpire(shareDownloadMap.get(fileId))) return shareDownloadMap.get(fileId);
            this.shareId=shareId;
            refreshShareToken();
            SpiderDebug.log("getShareDownloadUrl..." + fileId);
            JsonObject param = new JsonObject();
            param.addProperty("file_id", fileId);
            param.addProperty("share_id", shareId);
            param.addProperty("expire_sec", 600);
            String json = auth("v2/file/get_share_link_download_url", param.toString(), false);
            String url = JsonParser.parseString(json).getAsJsonObject().get("download_url").getAsString();
            shareDownloadMap.put(fileId, url);
            return url;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    public String getDownloadUrl(String shareId, String fileId) {
        try {
            if (downloadMap.containsKey(fileId) && downloadMap.get(fileId) != null && !isExpire(downloadMap.get(fileId))) return downloadMap.get(fileId);
            this.shareId=shareId;
            refreshShareToken();
            SpiderDebug.log("getDownloadUrl..." + fileId);
            tempIds.add(0, copy(fileId));
            JsonObject param = new JsonObject();
            param.addProperty("file_id", tempIds.get(0));
            param.addProperty("drive_id", auth.getDriveId());
            param.addProperty("expire_sec", 900);
            String json = oauth("openFile/getDownloadUrl", param.toString(), true);
            String url = Download.objectFrom(json).getUrl();
            downloadMap.put(fileId, url);
            return url;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            Init.execute(this::deleteAll);
        }
    }

    public Preview.Info getVideoPreviewPlayInfo(String shareId, String fileId) {
        try {
            this.shareId = shareId;
            refreshShareToken(shareId);
            SpiderDebug.log("getVideoPreviewPlayInfo..." + fileId);
            tempIds.add(0, copy( fileId));
            JsonObject param = new JsonObject();
            param.addProperty("file_id", tempIds.get(0));
            param.addProperty("drive_id", auth.getDriveId());
            param.addProperty("category", "live_transcoding");
            param.addProperty("url_expire_sec", 900);
            String json = oauth("openFile/getVideoPreviewPlayInfo", param.toString(), true);
            return Preview.objectFrom(json).getVideoPreviewPlayInfo();
        } catch (Exception e) {
            e.printStackTrace();
            return new Preview.Info();
        } finally {
            Init.execute(this::deleteAll);
        }
    }

    public String playerContent(String[] ids) {
        return Result.get().url(getDownloadUrl(ids[0],ids[1])).subs(getSubs(ids)).header(getHeader()).string();
    }

    public String playerContent(String[] ids, String flag) {
        try {
            if (!flag.contains("原画")) {//代理普画
                return getPreviewContent(ids,flag);
            } else if (flag.contains("原画F")) {//分享原画
                return Result.get().url(proxyVideoUrl("share", ids[0], ids[1])).octet().subs(getSubs(ids)).header(getHeader()).string();
            } else if (flag.contains("原画")) {//转存原画
                return Result.get().url(proxyVideoUrl("open", ids[0], ids[1])).octet().subs(getSubs(ids)).header(getHeader()).string();
            } else {
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().url("").string();
        }
    }

    private String getPreviewContent(String[] ids, String flag) {
        Preview.Info info = getVideoPreviewPlayInfo(ids[0],ids[1]);
        List<String> url = getPreviewUrl(info, ids[0], ids[1], true);
        List<Sub> subs = getSubs(ids);
        subs.addAll(getSubs(info));
        return Result.get().url(url).m3u8().subs(subs).header(getHeader()).string();
    }

    private List<String> getPreviewUrl(Preview.Info info, String shareId, String fileId, boolean proxy) {
        List<Preview.LiveTranscodingTask> tasks = info.getLiveTranscodingTaskList();
        List<String> url = new ArrayList<>();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            url.add(tasks.get(i).getTemplateId());
            url.add(proxy ? proxyVideoUrl("preview", shareId, fileId, tasks.get(i).getTemplateId()) : tasks.get(i).getUrl());
        }
        return url;
    }

    private String getPreviewUrl(JSONObject playInfo, String flag) throws Exception {
        if (!playInfo.has("live_transcoding_task_list")) return "";
        JSONArray taskList = playInfo.getJSONArray("live_transcoding_task_list");
        if (flag.length() > 2)  flag = flag.substring(0, 2);
        String temp = qmap.get(flag);
        if(temp!=null){
            for (int i = 0; i < taskList.length(); ++i) {
                JSONObject task = taskList.getJSONObject(i);
                if (task.getString("template_id").equals(temp)) {
                    return task.getString("url");
                }
            }
        }
        for (String templateId : quality) {
            for (int i = 0; i < taskList.length(); ++i) {
                JSONObject task = taskList.getJSONObject(i);
                if (task.getString("template_id").equals(templateId)) {
                    return task.getString("url");
                }
            }
        }
        return taskList.getJSONObject(0).getString("url");
    }
    private List<Sub> getSubs(Preview.Info info) {
        List<Sub> subs = new ArrayList<>();
        for (Preview.LiveTranscodingTask task : info.getLiveTranscodingSubtitleTaskList()) subs.add(task.getSub());
        return subs;
    }
    private String copy(String fileId) throws Exception {
        SpiderDebug.log("Copy..." + fileId);
        String json = "{\"requests\":[{\"body\":{\"file_id\":\"%s\",\"share_id\":\"%s\",\"auto_rename\":true,\"to_parent_file_id\":\"%s\",\"to_drive_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"0\",\"method\":\"POST\",\"url\":\"/file/copy\"}],\"resource\":\"file\"}";
        json = String.format(json, fileId, shareId, parentDir, auth.getDriveId());
        String result = auth("adrive/v2/batch", json, true);
        if (result.contains("ForbiddenNoPermission.File")) return copy(fileId);
        return new JSONObject(result).getJSONArray("responses").getJSONObject(0).getJSONObject("body").getString("file_id");
    }

    private void deleteAll() {
        List<String> ids = new ArrayList<>(tempIds);
        for (String id : ids) {
            boolean deleted = delete(id);
            if (deleted) tempIds.remove(id);
        }
    }

    private boolean delete(String fileId) {
        try {
            SpiderDebug.log("Delete..." + fileId);
            String json = "{\"requests\":[{\"body\":{\"drive_id\":\"%s\",\"file_id\":\"%s\",\"to_parent_file_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"%s\",\"method\":\"POST\",\"url\":\"/file/delete\"}],\"resource\":\"file\"}";
            json = String.format(json, auth.getDriveId(), fileId, parentDir, fileId);
            String result = auth("adrive/v2/batch", json, true);
            return result.length() == 211;
        } catch (Exception ignored) {
            return false;
        }
    }
    private String proxyVideoUrl(String cate, String shareId, String fileId) {
        int thread = 1;
        String url = String.format(Proxy.getUrl() + "?do=ali&type=video&cate=%s&shareId=%s&fileId=%s", cate, shareId, fileId);
        if ("open".equals(cate)) thread = 10;
        if ("share".equals(cate)) thread = 10;
        return thread == 1 ? url : ProxyVideo.url(url, thread);
    }

    private String proxyVideoUrl(String cate, String shareId, String fileId, String templateId) {
        return String.format(Proxy.getUrl() + "?do=ali&type=video&cate=%s&shareId=%s&fileId=%s&templateId=%s", cate, shareId, fileId, templateId);
    }

    private String proxyVideoUrl(String cate, String shareId, String fileId, String templateId, String mediaId) {
        return String.format(Proxy.getUrl() + "?do=ali&type=video&cate=%s&shareId=%s&fileId=%s&templateId=%s&mediaId=%s", cate, shareId, fileId, templateId, mediaId);
    }

    private static boolean isExpire(String url) {
        String expires = new UrlQuerySanitizer(url).getValue("x-oss-expires");
        if (TextUtils.isEmpty(expires)) return false;
        return Long.parseLong(expires) - Utils.Time() <= 60;
    }



    public Object[] proxyVideo(Map<String, String> params) throws Exception {
        if (dialog != null && dialog.isShowing()) return null;
        String templateId = params.get("templateId");
        String response = params.get("response");
        String shareId = params.get("shareId");
        String mediaId = params.get("mediaId");
        String fileId = params.get("fileId");
        String cate = params.get("cate");
        String downloadUrl = "";

        if ("preview".equals(cate)) {
            return previewProxy(shareId, fileId, templateId);
        }

        if ("open".equals(cate)) {
            downloadUrl = getDownloadUrl(shareId, fileId);
        } else if ("share".equals(cate)) {
            downloadUrl = getShareDownloadUrl(shareId, fileId);
        } else if ("m3u8".equals(cate)) {
            lock.lock();
            String mediaUrl = m3u8MediaMap.get(fileId).get(mediaId);
            if (isExpire(mediaUrl)) {
                getM3u8(shareId, fileId, templateId);
                mediaUrl = m3u8MediaMap.get(fileId).get(mediaId);
            }
            lock.unlock();
            downloadUrl = mediaUrl;
        }

        if ("url".equals(response)) return new Object[]{200, "text/plain; charset=utf-8", new ByteArrayInputStream(downloadUrl.getBytes("UTF-8"))};
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : params.keySet()) headers.put(key, params.get(key));
        headers.remove("do");
        headers.remove("host");
        headers.remove("type");
        headers.remove("cate");
        headers.remove("fileId");
        headers.remove("shareId");
        headers.remove("mediaId");
        headers.remove("templateId");
        headers.remove("remote-addr");
        headers.remove("http-client-ip");
        return new Object[]{ProxyVideo.proxy(downloadUrl, headers)};
    }

    private Object[] previewProxy(String shareId, String fileId, String templateId) {
        return new Object[]{200, "application/vnd.apple.mpegurl", new ByteArrayInputStream(getM3u8(shareId, fileId, templateId).getBytes())};
    }

    private String getM3u8Url(String shareId, String fileId, String templateId) {
        Preview.Info info = getVideoPreviewPlayInfo(shareId, fileId);
        List<String> url = getPreviewUrl(info, shareId, fileId, false);
        Map<String, String> previewMap = new HashMap<>();
        for (int i = 0; i < url.size(); i = i + 2) {
            previewMap.put(url.get(i), url.get(i + 1));
        }
        return previewMap.get(templateId);
    }

    private String getM3u8(String shareId, String fileId, String templateId) {
        String m3u8Url = getM3u8Url(shareId, fileId, templateId);
        String m3u8 = OkHttp.string(m3u8Url, getHeader());
        String[] m3u8Arr = m3u8.split("\n");
        List<String> listM3u8 = new ArrayList<>();
        Map<String, String> media = new HashMap<>();
        String site = m3u8Url.substring(0, m3u8Url.lastIndexOf("/")) + "/";
        int mediaId = 0;
        for (String oneLine : m3u8Arr) {
            String thisOne = oneLine;
            if (oneLine.contains("x-oss-expires")) {
                media.put(String.valueOf(mediaId), site + thisOne);
                thisOne = proxyVideoUrl("m3u8", shareId, fileId, templateId, String.valueOf(mediaId));
                mediaId++;
            }
            listM3u8.add(thisOne);
        }
        m3u8MediaMap.put(fileId, media);
        return TextUtils.join("\n", listM3u8);
    }

    public Object[] proxySub(Map<String, String> params) throws Exception {
        String fileId = params.get("fileId");
        String shareId = params.get("shareId");
        Response res = OkHttp.newCall(getDownloadUrl(shareId, fileId), getHeaderAuth());
        byte[] body = Utils.toUtf8(res.body().bytes());
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(body);
        return result;
    }


    private void startPen() {
        cleanToken();
        stopService();
        startFlow();
    }
    private void startFlow() {
        Init.run(this::showInput);
    }

    private void showInput() {
        try {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(Utils.dp2px(16), Utils.dp2px(16), Utils.dp2px(16), Utils.dp2px(16));
            FrameLayout frame = new FrameLayout(Init.context());
            EditText input = new EditText(Init.context());
            frame.addView(input, params);
            dialog = new AlertDialog.Builder(Init.getActivity()).setTitle("请输入阿里Token").setView(frame).setNeutralButton("阿里云盘APP授权", (dialog, which) -> onNeutral()).setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, (dialog, which) -> onPositive(input.getText().toString())).show();
        } catch (Exception ignored) {
        }
    }

    private void onNeutral() {
        dismiss();
        Init.execute(this::getQRCode);
    }

    private void onPositive(String text) {
        dismiss();
        Init.execute(() -> {
            if (text.startsWith("http")) setToken(OkHttp.string(text));
            else if (text.length() == 32) setToken(text);
            else if (text.contains(":")) setToken(OkHttp.string("http://" + text + "/proxy?do=ali&type=token"));
        });
    }

    private void getQRCode() {
        String json = OkHttp.string("https://passport.aliyundrive.com/newlogin/qrcode/generate.do?appName=aliyun_drive&fromSite=52&appName=aliyun_drive&appEntrance=web&isMobile=false&lang=zh_CN&returnUrl=&bizParams=&_bx-v=2.2.3");
        Data data = Data.objectFrom(json).getContent().getData();
        Init.run(() -> openApp(json, data));
    }

    private void openApp(String json, Data data) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName("com.alicloud.databox", "com.taobao.login4android.scan.QrScanActivity");
            intent.putExtra("key_scanParam", json);
            Init.getActivity().startActivity(intent);
        } catch (Exception e) {
            showQRCode(data);
        } finally {
            Init.execute(() -> startService(data.getParams()));
        }
    }

    private void showQRCode(Data data) {
        try {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Utils.dp2px(240), Utils.dp2px(240));
            ImageView image = new ImageView(Init.context());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(QRCode.getBitmap(data.getCodeContent(), 240, 2));
            FrameLayout frame = new FrameLayout(Init.context());
            params.gravity = Gravity.CENTER;
            frame.addView(image, params);
            dialog = new AlertDialog.Builder(Init.getActivity()).setView(frame).setOnCancelListener(this::dismiss).setOnDismissListener(this::dismiss).show();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            Init.show("请使用阿里云盘 App 扫描二维码");
        } catch (Exception ignored) {
        }
    }

    private void startService(Map<String, String> params) {
        service = Executors.newScheduledThreadPool(1);
        service.scheduleAtFixedRate(() -> {
            String result = OkHttp.post("https://passport.aliyundrive.com/newlogin/qrcode/query.do?appName=aliyun_drive&fromSite=52&_bx-v=2.2.3", params);
            Data data = Data.objectFrom(result).getContent().getData();
            if (data.hasToken()) setToken(data.getToken());
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void setToken(String value) {
        SpiderDebug.log("Token:" + value);
        //Init.show("Token:" + value);
        auth.setRefreshToken(value);
        refreshAccessToken();
        stopService();
    }
    private boolean onTimeout() {
        stopService();
        return false;
    }
    private void stopService() {
        if (service != null) service.shutdownNow();
        Init.run(this::dismiss);
    }

    private void dismiss(DialogInterface dialog) {
        stopService();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }
}
