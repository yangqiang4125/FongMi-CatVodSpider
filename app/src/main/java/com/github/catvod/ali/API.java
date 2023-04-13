package com.github.catvod.ali;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import com.github.catvod.BuildConfig;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.ali.Auth;
import com.github.catvod.bean.ali.Item;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.Proxy;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Utils;
import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.util.*;
public class API {

    private final List<String> quality;
    private String shareToken;
    private Auth auth;
    private String shareId;
    private static String deUrl;
    private static class Loader {
        static volatile API INSTANCE = new API();
    }

    public static API get() {
        return Loader.INSTANCE;
    }

    private API() {
        auth = Auth.objectFrom(Prefers.getString("aliyundrive"));
        quality = Arrays.asList("UHD","QHD","FHD", "HD", "SD", "LD");
    }

    public void setAuth(boolean flag){
        String tokenInfo = Prefers.getString("tokenInfo", "1");
        if (tokenInfo.equals("1")) {
            if(flag)if(!auth.getAccessTokenOpen().isEmpty())return;
            if (Utils.tokenInfo.length()>10) {
                auth = Auth.objectFrom(Utils.tokenInfo);
                auth.save();
                Init.show("已设置默认token");
            }
        }
    }
    public String getVal(String key,String dval){
        String tk = Utils.siteRule.optString(key,dval);
        return tk;
    }
    public void cleanToken() {
        auth.clean();
        Prefers.put("aliyundrive", "");
        setAuth(false);
    }
    public void setRefreshToken(String token) {
        if (auth.getRefreshToken().isEmpty()) auth.setRefreshToken(token);
    }
    public String getRefreshToken() {
        return auth.getRefreshToken();
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
        headers.put("x-canary", "client=web,app=share,version=v2.3.1");
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
        if (result.contains("AccessTokenInvalid")) {
            Prefers.put("tokenInfo", "0");
            return refreshAccessToken();
        }
        if (result.contains("ShareLinkTokenInvalid") || result.contains("InvalidParameterNotMatch")) return refreshShareToken();
        if (result.contains("QuotaExhausted")) Init.show("账号容量不够啦");
        return false;
    }

    private boolean checkOpen(String result) {
        if (result.contains("AccessTokenInvalid")) return refreshOpenToken();
        return false;
    }

    public void checkAccessToken() {
        if (auth.getAccessToken().isEmpty()) refreshAccessToken();
    }

    private boolean refreshAccessToken() {
        try {
            SpiderDebug.log("refreshAccessToken...");
            JSONObject body = new JSONObject();
            String token = auth.getRefreshToken();
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
            SpiderDebug.log(e);
            cleanToken();
            setAuth(false);
            return true;
        } finally {
            while (auth.isEmpty()) SystemClock.sleep(250);
        }
    }

    private void oauthRequest() throws Exception {
        SpiderDebug.log("OAuth Request...");
        JSONObject body = new JSONObject();
        body.put("authorize", 1);
        body.put("scope", "user:base,file:all:read,file:all:write");
        JSONObject object = new JSONObject(auth("https://open.aliyundrive.com/oauth/users/authorize?client_id=" + BuildConfig.CLIENT_ID + "&redirect_uri=https://alist.nn.ci/tool/aliyundrive/callback&scope=user:base,file:all:read,file:all:write&state=", body, false));
        Log.e("DDD", object.toString());
        oauthRedirect(object.getString("redirectUri").split("code=")[1]);
    }

    private void oauthRedirect(String code) throws Exception {
        SpiderDebug.log("OAuth Redirect...");
        JSONObject body = new JSONObject();
        body.put("code", code);
        body.put("grant_type", "authorization_code");
        JSONObject object = new JSONObject(post("https://api.nn.ci/alist/ali_open/code", body));
        Log.e("DDD", object.toString());
        auth.setRefreshTokenOpen(object.getString("refresh_token"));
    }

    private boolean refreshOpenToken() {
        try {
            SpiderDebug.log("refreshAccessTokenOpen...");
            JSONObject body = new JSONObject();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", auth.getRefreshTokenOpen());
            JSONObject object = new JSONObject(post("https://api.nn.ci/alist/ali_open/token", body));
            Log.e("DDD", object.toString());
            auth.setRefreshTokenOpen(object.optString("refresh_token"));
            auth.setAccessTokenOpen(object.optString("token_type") + " " + object.optString("access_token"));
            auth.save();
            Prefers.put("tokenInfo", "1");
            return true;
        } catch (Exception e) {
            SpiderDebug.log(e);
            cleanToken();
            Init.show(e.getMessage());
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
            LinkedHashMap<String, List<String>> subMap = new LinkedHashMap<>();
            listFiles(new Item(getParentFileId(fileId, object)), files, subMap);
            for (Item file : files) playUrls.add(Trans.get(file.getDisplayName()) + "$" + file.getFileId() + findSubs(file.getName(), subMap));
        } catch (Exception e) {
        }
        boolean fp = playUrls.isEmpty();
        String s = TextUtils.join("#", playUrls);
        List<String> sourceUrls = new ArrayList<>();
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
        String [] fromArr = from.split("\\$\\$\\$");
        String jxStr = Utils.getBx(s);
        from = from.replace("%", type);
        for (int i=0; i < fromArr.length; i++) {
            fromkey = fromArr[i];
            if(i==0||fromkey.endsWith("。"))sourceUrls.add(jxStr);
            else sourceUrls.add(s);
        }
        from = from.replace("。", type);
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
        if (Utils.isPic==1&&!vname.equals("无名称")) vod = getVodInfo(vname, vod, idInfo);
        String tag = vod.vodTag;
        if(tag==null||tag.isEmpty()) tag = "推荐";
        tag = tag + ";" + new Gson().toJson(auth);
        vod.setVodTag(tag);
        return vod;
    }

    public static Vod getVodInfo(String key,Vod vod,String[] idInfo){
        try {
            int sid = -1;
            if(idInfo.length>3&&Utils.isNumeric(idInfo[3])) sid = Integer.parseInt(idInfo[3]);
            if(sid<1)return vod;
            if(deUrl==null) deUrl = Utils.siteRule.optString("deUrl","https://www.voflix.me");
            if (sid == 1) {
                JSONObject response = new JSONObject(OkHttp.string(deUrl+"/index.php/ajax/suggest?mid=1&limit=1&wd=" + key));
                if (response.optInt("code", 0) == 1 && response.optInt("total", 0) > 0) {
                    JSONArray jsonArray = response.getJSONArray("list");
                    if (jsonArray.length() > 0) {
                        JSONObject o = (JSONObject) jsonArray.get(0);
                        sid = o.optInt("id", 0);

                    }
                }
            }
            if (sid > 0) {
                Document doc = Jsoup.parse(OkHttp.string(deUrl+"/detail/"+sid+".html"));
                Elements em = doc.select(".module-main");
                Elements el = doc.select(".module-info-main");
                String tag = el.select(".module-info-tag").text();
                if(tag.endsWith("/"))tag = tag.substring(5, tag.length() - 1);
                el = el.select(".module-info-items");
                String content = el.select(".module-info-introduction-content").text();
                content = Utils.trim(content);
                String director = el.select(".module-info-item-content a").eq(0).text();
                String actor = el.select(".module-info-item-content").eq(2).text();
                String pic = em.select(".module-info-poster .module-item-pic img").attr("data-original");
                String yearText = el.select(".module-info-item-content").eq(3).text();
                String year = yearText.replaceAll("(.*)\\(.*", "$1");
                String area = yearText.replaceAll(".*\\((.*)\\)", "$1");
                actor = actor.substring(0, actor.length() - 1);
                vod.setVodTag(tag);
                vod.setVodContent(content);
                vod.setVodDirector(director);
                vod.setVodActor(actor);
                vod.setVodYear(year);
                vod.setVodArea(area);
                vod.setVodPic(pic);
            }
        } catch (Exception e) {
        }
        return vod;
    }


    private void listFiles(Item folder, List<Item> files, LinkedHashMap<String, List<String>> subMap) throws Exception {
        listFiles(folder, files, subMap, "");
    }

    private void listFiles(Item parent, List<Item> files, LinkedHashMap<String, List<String>> subMap, String marker) throws Exception {
        JSONObject body = new JSONObject();
        List<Item> folders = new ArrayList<>();
        body.put("limit", 200);
        body.put("share_id", shareId);
        body.put("parent_file_id", parent.getFileId());
        body.put("order_by", "name");
        body.put("order_direction", "ASC");
        if (marker.length() > 0) body.put("marker", marker);
        Item item = Item.objectFrom(auth("adrive/v3/file/list", body, true));
        for (Item file : item.getItems()) {
            if (file.getType().equals("folder")) {
                folders.add(file);
            } else if (file.getCategory().equals("video") || file.getCategory().equals("audio")) {
                files.add(file.parent(parent.getName()));
            } else if (Utils.isSub(file.getExt())) {
                String key = file.removeExt();
                if (!subMap.containsKey(key)) subMap.put(key, new ArrayList<>());
                subMap.get(key).add(key + "@@@" + file.getExt() + "@@@" + file.getFileId());
            }
        }
        if (item.getNextMarker().length() > 0) {
            listFiles(parent, files, subMap, item.getNextMarker());
        }
        for (Item folder : folders) {
            listFiles(folder, files, subMap);
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

    public List<Sub> getSub(String[] ids) {
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
            String tempId = copy(fileId);
            JSONObject body = new JSONObject();
            body.put("file_id", tempId);
            body.put("drive_id", auth.getDriveId());
            String url = new JSONObject(oauth("openFile/getDownloadUrl", body.toString(), true)).getString("url");
            Init.execute(() -> delete(tempId));
            return url;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public String getPreviewUrl(String fileId) {
        try {
            String tempId = copy(fileId);
            JSONObject body = new JSONObject();
            body.put("file_id", tempId);
            body.put("drive_id", auth.getDriveId());
            body.put("category", "live_transcoding");
            body.put("url_expire_sec", "14400");
            String json = oauth("openFile/getVideoPreviewPlayInfo", body.toString(), true);
            JSONArray taskList = new JSONObject(json).getJSONObject("video_preview_play_info").getJSONArray("live_transcoding_task_list");
            Init.execute(() -> delete(tempId));
            return getPreviewQuality(taskList);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getPreviewQuality(JSONArray taskList) throws Exception {
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



    private String copy(String fileId) throws Exception {
        String json = "{\"requests\":[{\"body\":{\"file_id\":\"%s\",\"share_id\":\"%s\",\"auto_rename\":true,\"to_parent_file_id\":\"root\",\"to_drive_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"0\",\"method\":\"POST\",\"url\":\"/file/copy\"}],\"resource\":\"file\"}";
        json = String.format(json, fileId, shareId, auth.getDriveId());
        String result = auth("adrive/v2/batch", json, true);
        return new JSONObject(result).getJSONArray("responses").getJSONObject(0).getJSONObject("body").getString("file_id");
    }

    private void delete(String fileId) {
        String json = "{\"requests\":[{\"body\":{\"drive_id\":\"%s\",\"file_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"%s\",\"method\":\"POST\",\"url\":\"/file/delete\"}],\"resource\":\"file\"}";
        json = String.format(json, auth.getDriveId(), fileId, fileId);
        auth("adrive/v2/batch", json, true);
    }

    public Object[] proxySub(Map<String, String> params) {
        String fileId = params.get("file_id");
        String text = OkHttp.string(getDownloadUrl(fileId), getHeaderAuth());
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(text.getBytes());
        return result;
    }
}