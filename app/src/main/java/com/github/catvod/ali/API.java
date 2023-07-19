package com.github.catvod.ali;

import android.text.TextUtils;
import android.util.Log;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.ali.Auth;
import com.github.catvod.bean.ali.Item;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.Proxy;
import com.github.catvod.spider.PushAgentQQ;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Utils;
import com.google.gson.Gson;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class API {
    private final List<String> tempIds;
    private final List<String> quality;
    private final Map<String,String> qmap;
    private String shareToken;
    private Auth auth;
    private String shareId;
    private String refreshUrl;
    private String CLIENT_ID;
    private static class Loader {
        static volatile API INSTANCE = new API();
    }

    public static API get() {
        return Loader.INSTANCE;
    }

    public API() {
        tempIds = new ArrayList<>();
        qmap = new LinkedHashMap<>();
        auth = Auth.objectFrom(Prefers.getString("aliyundrive"));
        quality = Arrays.asList("UHD","QHD","FHD", "HD", "SD", "LD");
        qmap.put("2K","QHD");
        qmap.put("超清","FHD");
        qmap.put("高清","HD");
    }

    public void setAuth(boolean flag){
        String tokenInfo = Prefers.getString("tokenInfo", "1");
        if (tokenInfo.equals("1")) {
            //if(flag)if(!auth.getAccessTokenOpen().isEmpty())return;
            if (Utils.tokenInfo.length()>10) {
                auth = Auth.objectFrom(Utils.tokenInfo);
                auth.save();
                //Init.show("已设置默认token");
            }
        }
        refreshUrl = getVal("refreshUrl", "");
        if(refreshUrl.length()<10) refreshUrl = "https://api.nn.ci/";
    }
    public String getVal(String key,String dval){
        String tk = Utils.siteRule.optString(key,dval);
        return tk;
    }
    public void cleanToken() {
        auth.clean();
        Prefers.put("aliyundrive", "");
        setAuth(true);
    }
    public void alert(String msg) {
        boolean aflag = Prefers.getBoolean("alert", false);
        if(aflag) Init.show(msg);
    }
    public void setRefreshToken(String token) {
        if (auth.getRefreshToken().isEmpty()&&!token.endsWith(".json")) auth.setRefreshToken(token);
    }
    public String getRefreshToken() {
        return auth.getRefreshToken();
    }
    public void setShareId(String shareId) {
        this.shareId = shareId;
        refreshShareToken();
        checkAccessToken();
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
        if (result.contains("AccessTokenInvalid")) return refreshAccessToken();
        if (result.contains("ShareLinkTokenInvalid") || result.contains("InvalidParameterNotMatch")) return refreshShareToken();
        if (result.contains("QuotaExhausted")) {
            Init.show("账号容量不够啦");
            return refreshAccessToken();
        }
        return false;
    }

    private boolean checkOpen(String result) {
        if (result.contains("AccessTokenInvalid")) return refreshOpenToken();
        return false;
    }

    public void checkAccessToken() {
        try {
            if (auth.getAccessToken().isEmpty()) {
                refreshAccessToken();
            }else if(auth.getRefreshTokenOpen().isEmpty())oauthRequest();
        } catch (Exception e) {
            Init.show("checkAccessToken："+e.getMessage());
        }
    }

    private boolean refreshAccessToken() {
        try {
            SpiderDebug.log("refreshAccessToken...");
            JSONObject body = new JSONObject();
            String token = Utils.refreshToken;
            if (token.startsWith("http")) token = OkHttp.string(token).replaceAll("[^A-Za-z0-9]", "");
            body.put("refresh_token", token);
            body.put("grant_type", "refresh_token");
            JSONObject object = new JSONObject(post("https://auth.aliyundrive.com/v2/account/token", body));
            Log.e("DDD", object.toString());
            auth.setUserId(object.getString("user_id"));
            auth.setDriveId(object.getString("default_drive_id"));
            auth.setRefreshToken(object.getString("refresh_token"));
            auth.setAccessToken(object.getString("token_type") + " " + object.getString("access_token"));
            oauthRequest();
            return true;
        } catch (Exception e) {
            cleanToken();
            return true;
        } finally {
            //while (auth.isEmpty()) SystemClock.sleep(250);
        }
    }

    private void oauthRequest() throws Exception {
        SpiderDebug.log("OAuth Request...");
        if(CLIENT_ID==null) CLIENT_ID = getVal("CLIENT_ID", "76917ccccd4441c39457a04f6084fb2f");
        JSONObject body = new JSONObject();
        body.put("authorize", 1);
        body.put("scope", "user:base,file:all:read,file:all:write");
        JSONObject object = new JSONObject(auth("https://open.aliyundrive.com/oauth/users/authorize?client_id=" + CLIENT_ID + "&redirect_uri=https://alist.nn.ci/tool/aliyundrive/callback&scope=user:base,file:all:read,file:all:write&state=", body, false));
        Log.e("DDD", object.toString());
        if(object.toString().contains("not"))alert("oauthRequest:"+object.toString());
        oauthRedirect(object.getString("redirectUri").split("code=")[1]);
    }

    private void oauthRedirect(String code) throws Exception {
        SpiderDebug.log("OAuth Redirect...");
        JSONObject body = new JSONObject();
        body.put("code", code);
        body.put("grant_type", "authorization_code");

        JSONObject object = new JSONObject(post(refreshUrl+"alist/ali_open/code", body));
        if(object.toString().contains("not"))alert("oauthRedirect:"+object.toString());
        Log.e("DDD", object.toString());
        auth.setRefreshTokenOpen(object.getString("refresh_token"));
    }

    private boolean refreshOpenToken() {
        try {
            SpiderDebug.log("refreshAccessTokenOpen...");
            JSONObject body = new JSONObject();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", auth.getRefreshTokenOpen());
            JSONObject object = new JSONObject(post(refreshUrl + "alist/ali_open/token", body));
            Log.e("DDD", object.toString());
            auth.setRefreshTokenOpen(object.optString("refresh_token"));
            auth.setAccessTokenOpen(object.optString("token_type") + " " + object.optString("access_token"));
            auth.save();
            Prefers.put("tokenInfo", "1");
            return true;
        } catch (Exception e) {
            if(e.getMessage().contains("Too Many Requests"))Init.show("请求过多被封IP，明天再看");
            else{
                alert(e.getMessage());
                SpiderDebug.log(e);
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
            e.printStackTrace();
            return false;
        }
    }

    public Vod getVod(String url, String fileId) throws Exception {
        String[] idInfo = url.split("\\$\\$\\$");
        List<String> playUrls = new ArrayList<>();
        JSONObject object = null;
        try {
            JSONObject body = new JSONObject();
            body.put("share_id", shareId);
            String json = post("adrive/v3/share_link/get_share_by_anonymous", body);
            object = new JSONObject(json);
            List<Item> files = new ArrayList<>();
            List<Item> subs = new ArrayList<>();
            listFiles(new Item(getParentFileId(fileId, object)), files, subs);
            if(files.isEmpty())Init.show("来晚啦，该分享已失效~");
            else for (Item file : files) playUrls.add(file.getDisplayName() + "$" + file.getFileId() + findSubs(file.getName(), subs));
        } catch (Exception e) {
        }
        boolean fp = playUrls.isEmpty();
        String s = TextUtils.join("#", playUrls);
        List<String> sourceUrls = new LinkedList<>();
        Vod vod = new Vod(); String type = "";
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
        //from = "2K%$$$超清%。$$$高清%。$$$原画%。$$$普话%";
        String jxStr = Utils.getBx(s);
        from = from.replace("%", type);
        String [] fromArr = from.split("\\$\\$\\$");
        for (int i=0; i < fromArr.length; i++) {
            fromkey = fromArr[i];
            if(i==0||fromkey.contains("。"))sourceUrls.add(jxStr);
            else sourceUrls.add(s);
        }
        from = from.replace("。", "");
        vod.setVodId(TextUtils.join("$$$",idInfo));
        vod.setVodContent(idInfo[0]);
        String vpic = "http://image.xinjun58.com/sp/pic/bg/ali.jpg";
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
        if (Utils.isPic==1&&!vname.equals("无名称")) {
            Vod vod2 = getVodInfo(vname, vod, idInfo);
            if(vod2!=null) vod = vod2;
        }
        String tag = vod.vodTag;
        if(tag==null||tag.isEmpty()) tag = "推荐";
        tag = tag + ";" + new Gson().toJson(auth);
        vod.setVodTag(tag);
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

    public static Vod getVodInfo(String key,Vod vod,String[] idInfo){
        try {
            int sid = -1;
            if(idInfo.length>3&&Utils.isNumeric(idInfo[3])) sid = Integer.parseInt(idInfo[3]);
            if(sid<1)return vod;
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


    private void listFiles(Item folder, List<Item> files, List<Item> subs) throws Exception {
        listFiles(folder, files, subs, "");
    }

    private void listFiles(Item parent, List<Item> files, List<Item> subs, String marker) throws Exception {
        JSONObject body = new JSONObject();
        List<Item> folders = new ArrayList<>();
        body.put("limit", 200);
        body.put("share_id", shareId);
        body.put("parent_file_id", parent.getFileId());
        body.put("order_by", "name");
        body.put("order_direction", "ASC");
        if (marker.length() > 0) body.put("marker", marker);
        Item item = Item.objectFrom(auth("adrive/v3/file/list", body.toString(), true));
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
            listFiles(parent, files, subs, item.getNextMarker());
        }
        for (Item folder : folders) {
            listFiles(folder, files, subs);
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
            String url = Proxy.getUrl() + "?do=ali&type=sub" + "&file_id=" + split[2];
            sub.add(Sub.create().name(name).ext(ext).url(url));
        }
        return sub;
    }

    public String getDownloadUrl(String fileId) {
        try {
            SpiderDebug.log("getDownloadUrl..." + fileId);
            tempIds.add(0, copy(fileId));
            JSONObject body = new JSONObject();
            body.put("file_id", tempIds.get(0));
            body.put("drive_id", auth.getDriveId());
            String json = oauth("openFile/getDownloadUrl", body.toString(), true);
            return new JSONObject(json).getString("url");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            Init.execute(this::deleteAll);
        }
    }

    public JSONObject getVideoPreviewPlayInfo(String fileId) {
        try {
            SpiderDebug.log("getVideoPreviewPlayInfo..." + fileId);
            tempIds.add(0, copy(fileId));
            JSONObject body = new JSONObject();
            body.put("file_id", tempIds.get(0));
            body.put("drive_id", auth.getDriveId());
            body.put("category", "live_transcoding");
            body.put("url_expire_sec", "14400");
            String json = oauth("openFile/getVideoPreviewPlayInfo", body.toString(), true);
            return new JSONObject(json).getJSONObject("video_preview_play_info");
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        } finally {
            Init.execute(this::deleteAll);
        }
    }

    public String playerContent(String[] ids) {
        return Result.get().url(getDownloadUrl(ids[0])).subs(getSubs(ids)).header(getHeader()).string();
    }

    public String playerContent(String[] ids, String flag) {
        try {
            JSONObject playInfo = getVideoPreviewPlayInfo(ids[0]);
            String url = getPreviewUrl(playInfo, flag);
            List<Sub> subs = getSubs(ids);
            subs.addAll(getSubs(playInfo));
            return Result.get().url(url).subs(subs).header(getHeader()).string();
        } catch (Exception e) {
            alert("player:"+e.getMessage());
            return Result.get().url("").string();
        }
    }

    private String getPreviewUrl(JSONObject playInfo, String flag) throws Exception {
        if (!playInfo.has("live_transcoding_task_list")) return "";
        JSONArray taskList = playInfo.getJSONArray("live_transcoding_task_list");
        if (flag.length() > 2)  flag = flag.substring(0, 2);
        String temp = get().qmap.get(flag);
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

    private List<Sub> getSubs(JSONObject playInfo) throws Exception {
        if (!playInfo.has("live_transcoding_subtitle_task_list")) return Collections.emptyList();
        JSONArray taskList = playInfo.getJSONArray("live_transcoding_subtitle_task_list");
        List<Sub> subs = new ArrayList<>();
        for (int i = 0; i < taskList.length(); ++i) {
            JSONObject task = taskList.getJSONObject(i);
            String lang = task.getString("language");
            String url = task.getString("url");
            subs.add(Sub.create().url(url).name(lang).lang(lang).ext("vtt"));
        }
        return subs;
    }

    private String copy(String fileId) throws Exception {
        SpiderDebug.log("Copy..." + fileId);
        String json = "{\"requests\":[{\"body\":{\"file_id\":\"%s\",\"share_id\":\"%s\",\"auto_rename\":true,\"to_parent_file_id\":\"root\",\"to_drive_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"0\",\"method\":\"POST\",\"url\":\"/file/copy\"}],\"resource\":\"file\"}";
        json = String.format(json, fileId, shareId, auth.getDriveId());
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
            String json = "{\"requests\":[{\"body\":{\"drive_id\":\"%s\",\"file_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"%s\",\"method\":\"POST\",\"url\":\"/file/delete\"}],\"resource\":\"file\"}";
            json = String.format(json, auth.getDriveId(), fileId, fileId);
            String result = auth("adrive/v2/batch", json, true);
            return result.length() == 211;
        } catch (Exception ignored) {
            return false;
        }
    }

    public Object[] proxySub(Map<String, String> params) throws Exception {
        String fileId = params.get("file_id");
        Response res = OkHttp.newCall(getDownloadUrl(fileId), getHeaderAuth());
        byte[] body = Utils.toUtf8(res.body().bytes());
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(body);
        return result;
    }
}
