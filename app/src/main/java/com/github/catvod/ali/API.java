package com.github.catvod.ali;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.ali.*;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.spider.Ali;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.Proxy;
import com.github.catvod.spider.PushAgentQQ;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.QRCode;
import com.github.catvod.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class API {
    private ScheduledExecutorService service;
    private AlertDialog dialog;
    private final List<String> tempIds;
    public final Map<String,String> qmap;
    private String refreshToken;
    private Auth auth;
    private Share share;
    private String refreshUrl;
    private String CLIENT_ID;
    private String parentDir = "root";
    private String updateTk = "0";
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
    }

    public void setAuth(boolean flag){
        boolean aflag = Prefers.getBoolean("alert", false);
        if (!aflag) {
            String tokenInfo = Prefers.getString("tokenInfo", "1");
            if (tokenInfo.equals("1")) {
                //if(flag)if(!auth.getAccessTokenOpen().isEmpty())return;
                if (Utils.tokenInfo.length()>10) {
                    auth = Auth.objectFrom(Utils.tokenInfo);
                    auth.save();
                    //Init.show("已设置默认token");
                }
            }
        }
        refreshUrl = getVal("refreshUrl", "");
        parentDir = getVal("parentDir", "root");
        updateTk = getVal("updateTk", "0");
        if(refreshUrl.length()<10) refreshUrl = "https://api.nn.ci/";
    }
    public String getVal(String key,String dval){
        String tk = Utils.siteRule.optString(key,dval);
        return tk;
    }
    public void cleanToken() {
        auth.clean().save();
        Prefers.put("tokenInfo", "0");
    }
    public void alert(String msg) {
        boolean aflag = Prefers.getBoolean("alert", false);
        if(aflag) Init.show(msg);
    }
    public void setRefreshToken(String token) {
        this.refreshToken = token;
    }
    public Object[] getToken() {
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "text/plain";
        result[2] = new ByteArrayInputStream(auth.getRefreshToken().getBytes());
        return result;
    }

    public HashMap<String, String> getHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Utils.CHROME);
        headers.put("Referer", "https://www.aliyundrive.com/");
        return headers;
    }

    private HashMap<String, String> getHeaderAuth() {
        HashMap<String, String> headers = getHeader();
        headers.put("x-share-token", share.getShareToken());
        headers.put("X-Canary", "client=Android,app=adrive,version=v4.3.1");
        if (auth.isAuthed()) headers.put("authorization", auth.getAccessToken());
        return headers;
    }

    private HashMap<String, String> getHeaderOpen() {
        HashMap<String, String> headers = getHeader();
        headers.put("authorization", auth.getAccessToken());
        return headers;
    }

    private boolean alist(String url, JsonObject param) {
        String api = refreshUrl+"alist/ali_open/" + url;
        OkResult result = OkHttp.post(api, param.toString(), getHeader());
        if (isManyRequest(result.getBody())) return false;
        try {
            JSONObject object = new JSONObject(result.getBody());
            auth.setRefreshTokenOpen(object.optString("refresh_token"));
            auth.setAccessTokenOpen(object.optString("token_type") + " " + object.optString("access_token"));
            auth.save();
            Init.show("tokeInfo已更新");
            Prefers.put("tokenInfo", "1");
        } catch (JSONException e) {
            cleanToken();
        }
        return true;
    }

    private String post(String url, JsonObject param) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        OkResult result = OkHttp.post(url, param.toString(), getHeader());
        SpiderDebug.log(result.getCode() + "," + url + "," + result.getBody());
        return result.getBody();
    }

    private String auth(String url, String json, boolean retry) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        OkResult result = OkHttp.post(url, json, getHeaderAuth());
        SpiderDebug.log(result.getCode() + "," + url + "," + result.getBody());
        if (retry && result.getCode() == 401 && refreshAccessToken()) return auth(url, json, false);
        if (retry && result.getCode() == 429) return auth(url, json, false);
        return result.getBody();
    }

    private String oauth(String url, String json, boolean retry) {
        url = url.startsWith("https") ? url : "https://open.aliyundrive.com/adrive/v1.0/" + url;
        OkResult result = OkHttp.post(url, json, getHeaderOpen());
        SpiderDebug.log(result.getCode() + "," + url + "," + result.getBody());
        if (retry && (result.getCode() == 400 || result.getCode() == 401) && refreshOpenToken()) return oauth(url, json, false);
        return result.getBody();
    }

    private boolean isManyRequest(String result) {
        if (!result.contains("Too Many Requests")) return false;
        Init.show("请求过多被封IP，明天再看");
        cleanToken();
        return true;
    }

    private boolean onTimeout() {
        stopService();
        return false;
    }

    private void refreshShareToken(String shareId) {
        if (share != null && share.alive(shareId)) return;
        SpiderDebug.log("refreshShareToken...");
        JsonObject param = new JsonObject();
        param.addProperty("share_id", shareId);
        param.addProperty("share_pwd", "");
        String json = post("v2/share_link/get_share_token", param);
        share = Share.objectFrom(json).setShareId(shareId).setTime();
        if (share.getShareToken().isEmpty()) Init.show("来晚啦，该分享已失效");
    }

    private boolean refreshOpenToken() {
        if (auth.getRefreshTokenOpen().isEmpty()) return oauthRequest();
        SpiderDebug.log("refreshOpenToken...");
        JsonObject param = new JsonObject();
        param.addProperty("grant_type", "refresh_token");
        param.addProperty("refresh_token", auth.getRefreshTokenOpen());
        return alist("token", param);
    }

    private boolean refreshAccessToken() {
        try {
            SpiderDebug.log("refreshAccessToken...");
            JsonObject param = new JsonObject();
            String token = auth.getRefreshToken();
            if (token.isEmpty()) token = refreshToken;
            if (token != null && token.startsWith("http")) token = OkHttp.string(token).trim();
            param.addProperty("refresh_token", token);
            param.addProperty("grant_type", "refresh_token");
            String json = post("https://auth.aliyundrive.com/v2/account/token", param);
            JSONObject object = new JSONObject(json);
            auth.setUserId(object.getString("user_id"));
            auth.setDriveId(object.getString("default_drive_id"));
            auth.setRefreshToken(object.getString("refresh_token"));
            auth.setAccessToken(object.getString("token_type") + " " + object.getString("access_token"));
            if (auth.getAccessToken().isEmpty()) throw new Exception(json);
            return true;
        } catch (Exception e) {
            if (e instanceof TimeoutException) return onTimeout();
            alert(e.getMessage());
            startPen();
            return true;
        } finally {
            while (auth.getAccessToken().isEmpty()) SystemClock.sleep(250);
        }
    }

    private void startPen() {
        cleanToken();
        stopService();
        startFlow();
    }

    private boolean oauthRequest(){
        SpiderDebug.log("OAuth Request...");
        if(CLIENT_ID==null) CLIENT_ID = getVal("CLIENT_ID", "76917ccccd4441c39457a04f6084fb2f");
        JsonObject param = new JsonObject();
        param.addProperty("authorize", 1);
        param.addProperty("scope", "user:base,file:all:read,file:all:write");
        String url = "https://open.aliyundrive.com/oauth/users/authorize?client_id=" + CLIENT_ID + "&redirect_uri=https://alist.nn.ci/tool/aliyundrive/callback&scope=user:base,file:all:read,file:all:write&state=";
        String json = auth(url, param.toString(), true);
        Code code = Code.objectFrom(json);
        return oauthRedirect(code.getCode());
    }

    private boolean oauthRedirect(String code) {
        SpiderDebug.log("OAuth Redirect...");
        JsonObject param = new JsonObject();
        param.addProperty("code", code);
        param.addProperty("grant_type", "authorization_code");
        return alist("code", param);
    }

    public Vod getVod(String url, String shareId, String fileId) {
        Vod vod = new Vod();
        try {
            refreshShareToken(shareId);
            String[] idInfo = url.split("\\$\\$\\$");
            JsonObject param = new JsonObject();
            param.addProperty("share_id", shareId);
            Share share = Share.objectFrom(post("adrive/v3/share_link/get_share_by_anonymous", param));
            List<Item> files = new ArrayList<>();
            List<Item> subs = new ArrayList<>();
            listFiles(shareId, new Item(getParentFileId(fileId, share)), files, subs);
            List<String> playFrom = Arrays.asList("原画", "普画");
            List<String> episode = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            for (Item file : files) episode.add(file.getDisplayName() + "$" + shareId + "+" + file.getFileId() + findSubs(file.getName(), subs));
            for (int i = 0; i < playFrom.size(); i++) playUrl.add(TextUtils.join("#", episode));
            vod.setVodId(url);
            vod.setVodContent(idInfo[0]);
            String vpic = "http://image.xinjun58.com/sp/pic/bg/ali.jpg";
            String vname=share.getShareName()!=null?share.getShareName():"无名称";
            if (idInfo != null) {
                if(idInfo.length>1&&!idInfo[1].isEmpty()) vpic = idInfo[1];
                if(idInfo.length>2&&!idInfo[2].isEmpty()) vname = idInfo[2];
            }
            vod.setVodPic(vpic);
            vod.setVodName(vname);
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
            vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
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
        } catch (Exception e) {
            alert(e.getMessage());
        }
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
            if(key.equals("磁力")||key.startsWith("http"))return vod;
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

    private String getParentFileId(String fileId, Share share) {
        if (!TextUtils.isEmpty(fileId)) return fileId;
        if (share.getFileInfos().isEmpty()) return "";
        Item item = share.getFileInfos().get(0);
        return item.getType().equals("folder") ? item.getFileId() : parentDir;
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

    public String getDownloadUrl(String shareId, String fileId) {
        try {
            refreshShareToken(shareId);
            SpiderDebug.log("getDownloadUrl..." + fileId);
            tempIds.add(0, copy(shareId, fileId));
            JsonObject param = new JsonObject();
            param.addProperty("file_id", tempIds.get(0));
            param.addProperty("drive_id", auth.getDriveId());
            String json = oauth("openFile/getDownloadUrl", param.toString(), true);
            return new JSONObject(json).getString("url");
        } catch (Exception e) {
            alert(e.getMessage());
            return "";
        } finally {
            Init.execute(this::deleteAll);
        }
    }

    public Preview.Info getVideoPreviewPlayInfo(String shareId, String fileId) {
        try {
            refreshShareToken(shareId);
            SpiderDebug.log("getVideoPreviewPlayInfo..." + fileId);
            tempIds.add(0, copy(shareId, fileId));
            JsonObject param = new JsonObject();
            param.addProperty("file_id", tempIds.get(0));
            param.addProperty("drive_id", auth.getDriveId());
            param.addProperty("category", "live_transcoding");
            param.addProperty("url_expire_sec", "14400");
            String json = oauth("openFile/getVideoPreviewPlayInfo", param.toString(), true);
            return Preview.objectFrom(json).getVideoPreviewPlayInfo();
        } catch (Exception e) {
            e.printStackTrace();
            return new Preview.Info();
        } finally {
            Init.execute(this::deleteAll);
        }
    }

    public String playerContent(String[] ids, boolean original) {
        boolean aflag = Prefers.getBoolean("alert", false);
        if (aflag&&auth.getRefreshToken().isEmpty())  {
            stopService();
            startFlow();
        }
        if (original) return Result.get().url(getDownloadUrl(ids[0], ids[1])).octet().subs(getSubs(ids)).header(getHeader()).string();
        else return getPreviewContent(ids);
    }

    private String getPreviewContent(String[] ids) {
        Preview.Info info = getVideoPreviewPlayInfo(ids[0], ids[1]);
        List<String> url = getPreviewUrl(info);
        List<Sub> subs = getSubs(ids);
        subs.addAll(getSubs(info));
        return Result.get().url(url).m3u8().subs(subs).header(getHeader()).string();
    }

    private List<String> getPreviewUrl(Preview.Info info) {
        List<Preview.LiveTranscodingTask> tasks = info.getLiveTranscodingTaskList();
        List<String> url = new ArrayList<>();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            url.add(tasks.get(i).getTemplateId());
            url.add(tasks.get(i).getUrl());
        }
        return url;
    }

    private List<Sub> getSubs(Preview.Info info) {
        List<Sub> subs = new ArrayList<>();
        for (Preview.LiveTranscodingTask task : info.getLiveTranscodingSubtitleTaskList()) subs.add(task.getSub());
        return subs;
    }

    private String copy(String shareId, String fileId) throws JSONException {
        SpiderDebug.log("Copy..." + fileId);
        String json = "{\"requests\":[{\"body\":{\"file_id\":\"%s\",\"share_id\":\"%s\",\"auto_rename\":true,\"to_parent_file_id\":\"%s\",\"to_drive_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"0\",\"method\":\"POST\",\"url\":\"/file/copy\"}],\"resource\":\"file\"}";
        json = String.format(json, fileId, shareId, parentDir, auth.getDriveId());
        String result = auth("adrive/v2/batch", json, true);
        if (result.contains("ForbiddenNoPermission.File")) return copy(shareId,fileId);
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
        SpiderDebug.log("Delete..." + fileId);
        String json = "{\"requests\":[{\"body\":{\"drive_id\":\"%s\",\"file_id\":\"%s\"},\"headers\":{\"Content-Type\":\"application/json\"},\"id\":\"%s\",\"method\":\"POST\",\"url\":\"/file/delete\"}],\"resource\":\"file\"}";
        json = String.format(json, auth.getDriveId(), fileId, fileId);
        Res res = Res.objectFrom(auth("adrive/v2/batch", json, true));
        return res.getResponse().getStatus() == 404;
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
        Init.show("Token:" + value);
        auth.setRefreshToken(value);
        refreshAccessToken();
        stopService();
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
