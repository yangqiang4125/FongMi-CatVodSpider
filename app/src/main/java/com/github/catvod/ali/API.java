package com.github.catvod.ali;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.UrlQuerySanitizer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.github.catvod.BuildConfig;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.ali.Auth;
import com.github.catvod.bean.ali.Data;
import com.github.catvod.bean.ali.Item;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Ali;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.Proxy;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.QRCode;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Utils;
import com.starkbank.ellipticcurve.Ecdsa;
import com.starkbank.ellipticcurve.PrivateKey;
import com.starkbank.ellipticcurve.utils.BinaryAscii;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class API {

    private ScheduledExecutorService service;
    private Map<String, String> mediaId2Url;
    private final ReentrantLock lock;
    private AlertDialog dialog;
    private final Auth auth;
    private final List<String> quality;
    private static class Loader {
        static volatile API INSTANCE = new API();
    }

    public static API get() {
        return Loader.INSTANCE;
    }

    private API() {
        quality = Arrays.asList("UHD","QHD","FHD", "HD", "SD", "LD");
        this.auth = new Auth();
        this.lock = new ReentrantLock(true);
    }
    public String getRefreshToken() {
        return auth.getRefreshToken();
    }
    public void setRefreshToken(String token) {
        auth.setRefreshToken(token);
    }

    public void setShareId(String shareId) {
        auth.setShareId(shareId);
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
        headers.put("x-share-token", auth.getShareToken());
        return headers;
    }

    private HashMap<String, String> getHeaderSign() {
        HashMap<String, String> headers = getHeaderAuth();
        headers.put("x-device-id", auth.getDeviceId());
        headers.put("x-signature", auth.getSignature());
        return headers;
    }

    private String post(String url, JSONObject body) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        return OkHttp.postJson(url, body.toString(), getHeader());
    }

    private String auth(String url, JSONObject body, boolean retry) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        String result = OkHttp.postJson(url, body.toString(), getHeaderAuth());
        if (retry && check401(result)) return auth(url, body, false);
        return result;
    }

    private String sign(String url, JSONObject body, boolean retry) {
        url = url.startsWith("https") ? url : "https://api.aliyundrive.com/" + url;
        String result = OkHttp.postJson(url, body.toString(), getHeaderSign());
        if (retry && check401(result)) return sign(url, body, false);
        return result;
    }

    private boolean check401(String result) {
        if (result.contains("AccessTokenInvalid")) return refreshAccessToken();
        if (result.contains("ShareLinkTokenInvalid") || result.contains("InvalidParameterNotMatch")) return refreshShareToken();
        if (result.contains("UserDeviceOffline") || result.contains("UserDeviceIllegality") || result.contains("DeviceSessionSignatureInvalid")) return refreshSignature();
        return false;
    }

    public void checkAccessToken() {
        if (auth.getAccessToken().isEmpty()) refreshAccessToken();
    }

    private void checkSignature() {
        if (auth.getSignature().isEmpty()) refreshSignature();
    }

    private boolean refreshAccessToken() {
        try {
            JSONObject body = new JSONObject();
            String token = Utils.refreshToken;
            if (token.startsWith("http")) token = OkHttp.string(token).replaceAll("[^A-Za-z0-9]", "");
            body.put("refresh_token", token);
            body.put("grant_type", "refresh_token");
            JSONObject object = new JSONObject(post("https://auth.aliyundrive.com/v2/account/token", body));
            auth.setUserId(object.getString("user_id"));
            auth.setDeviceId(object.getString("device_id"));
            auth.setAccessToken(object.getString("token_type") + " " + object.getString("access_token"));
            auth.setRefreshToken(object.getString("refresh_token"));
            return true;
        } catch (Exception e) {
            stopService();
            auth.clean();
            Ali.fetchRule(true, 0);
            getQRCode();
            return true;
        } finally {
            while (auth.isEmpty()) SystemClock.sleep(250);
        }
    }

    public boolean refreshShareToken() {
        try {
            JSONObject body = new JSONObject();
            body.put("share_id", auth.getShareId());
            body.put("share_pwd", "");
            JSONObject object = new JSONObject(post("v2/share_link/get_share_token", body));
            auth.setShareToken(object.getString("share_token"));
            return true;
        } catch (Exception e) {
            Init.show("来晚啦，该分享已失效");
            e.printStackTrace();
            return false;
        }
    }

    private boolean refreshSignature() {
        try {
            PrivateKey privateKey = new PrivateKey();
            String pubKey = "04" + BinaryAscii.hexFromBinary(privateKey.publicKey().toByteString().getBytes());
            String message = BuildConfig.APP_ID + ":" + auth.getDeviceId() + ":" + auth.getUserId() + ":" + 0;
            String signature = BinaryAscii.hexFromBinary(Ecdsa.sign(message, privateKey).toDer().getBytes());
            auth.setSignature(signature.substring(signature.length() - 128) + "01");
            JSONObject body = new JSONObject();
            body.put("deviceName", "samsung");
            body.put("modelName", "SM-G9810");
            body.put("nonce", 0);
            body.put("pubKey", pubKey);
            body.put("refreshToken", Utils.refreshToken);
            JSONObject object = new JSONObject(sign("users/v1/users/device/create_session", body, false));
            if (!object.getBoolean("success")) throw new Exception(object.toString());
            return true;
        } catch (Exception e) {
            auth.setSignature("");
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
            body.put("share_id", auth.getShareId());
            String json = API.get().post("adrive/v3/share_link/get_share_by_anonymous", body);
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
        sourceUrls.add(Utils.getBx(s));
        sourceUrls.add(s);
        sourceUrls.add(s);
        String from = "普画%$$$普画i%$$$原画%";
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
        if (Utils.isPic==1&&!vname.equals("无名称")) vod = getVodInfo(vname, vod, idInfo);
        return vod;
    }

    public static Vod getVodInfo(String key,Vod vod,String[] idInfo){
        try {
            int sid = -1;
            if(idInfo.length>3&&Utils.isNumeric(idInfo[3])) sid = Integer.parseInt(idInfo[3]);
            if(sid<1)return vod;
            if (sid == 1) {
                JSONObject response = new JSONObject(OkHttp.string("https://www.voflix.com/index.php/ajax/suggest?mid=1&limit=1&wd=" + key));
                if (response.optInt("code", 0) == 1 && response.optInt("total", 0) > 0) {
                    JSONArray jsonArray = response.getJSONArray("list");
                    if (jsonArray.length() > 0) {
                        JSONObject o = (JSONObject) jsonArray.get(0);
                        sid = o.optInt("id", 0);

                    }
                }
            }
            if (sid > 0) {
                Document doc = Jsoup.parse(OkHttp.string("https://www.voflix.com/detail/"+sid+".html"));
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
        body.put("share_id", auth.getShareId());
        body.put("parent_file_id", parent.getFileId());
        body.put("order_by", "name");
        body.put("order_direction", "ASC");
        if (marker.length() > 0) body.put("marker", marker);
        Item item = Item.objectFrom(API.get().auth("adrive/v3/file/list", body, true));
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

    public String getPreviewUrl(String fileId) {
        return Proxy.getUrl() + "?do=ali&type=m3u8&file_id=" + fileId;
    }

    public String getDownloadUrl(String fileId) {
        try {
            JSONObject body = new JSONObject();
            body.put("file_id", fileId);
            body.put("share_id", auth.getShareId());
            body.put("expire_sec", 600);
            String json = API.get().auth("v2/file/get_share_link_download_url", body, true);
            String url = new JSONObject(json).optString("download_url");
            Map<String, List<String>> respHeaders = new HashMap<>();
            OkHttp.stringNoRedirect(url, API.get().getHeader(), respHeaders);
            return OkHttp.getRedirectLocation(respHeaders);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public Object[] proxySub(Map<String, String> params) {
        String fileId = params.get("file_id");
        String text = OkHttp.string(getDownloadUrl(fileId), API.get().getHeaderAuth());
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(text.getBytes());
        return result;
    }

    public Object[] proxyM3U8(Map<String, String> params) {
        String fileId = params.get("file_id");
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/vnd.apple.mpegurl";
        result[2] = new ByteArrayInputStream(refreshM3U8(fileId).getBytes());
        return result;
    }

    public Object[] proxyMedia(Map<String, String> params) {
        try {
            String fileId = params.get("file_id");
            String mediaId = params.get("media_id");
            lock.lock();
            String mediaUrl = mediaId2Url.get(mediaId);
            long expires = Long.parseLong(new UrlQuerySanitizer(mediaUrl).getValue("x-oss-expires"));
            long current = System.currentTimeMillis() / 1000;
            if (expires - current <= 60) {
                refreshM3U8(fileId);
                mediaUrl = mediaId2Url.get(mediaId);
            }
            lock.unlock();
            Object[] result = new Object[3];
            result[0] = 200;
            result[1] = "video/MP2T";
            result[2] = OkHttp.newCall(mediaUrl, getHeader()).body().byteStream();
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String refreshM3U8(String fileId) {
        try {
            checkSignature();
            JSONObject body = new JSONObject();
            body.put("file_id", fileId);
            body.put("share_id", auth.getShareId());
            body.put("template_id", "");
            body.put("category", "live_transcoding");
            String json = sign("v2/file/get_share_link_video_preview_play_info", body, true);
            JSONArray taskList = new JSONObject(json).getJSONObject("video_preview_play_info").getJSONArray("live_transcoding_task_list");
            Map<String, List<String>> respHeaders = new HashMap<>();
            OkHttp.stringNoRedirect(getPreviewQuality(taskList), getHeader(), respHeaders);
            String location = OkHttp.getRedirectLocation(respHeaders);
            String m3u8 = OkHttp.string(location, getHeader());
            String mediaUrlPrefix = location.substring(0, location.lastIndexOf("/")) + "/";
            List<String> lines = new ArrayList<>();
            int mediaId = 0;
            mediaId2Url = new HashMap<>();
            for (String line : m3u8.split("\n")) {
                if (line.contains("x-oss-expires")) {
                    mediaId += 1;
                    mediaId2Url.put(String.valueOf(mediaId), mediaUrlPrefix + line);
                    line = Proxy.getUrl() + "?do=ali&type=media" + "&file_id=" + fileId + "&media_id=" + mediaId;
                }
                lines.add(line);
            }
            return TextUtils.join("\n", lines);
        } catch (Exception e) {
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

    private void getQRCode() {
        Data data = Data.objectFrom(OkHttp.string("https://passport.aliyundrive.com/newlogin/qrcode/generate.do?appName=aliyun_drive&fromSite=52&appName=aliyun_drive&appEntrance=web&isMobile=false&lang=zh_CN&returnUrl=&bizParams=&_bx-v=2.2.3")).getContent().getData();
        Init.run(() -> showQRCode(data));
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
            dialog = new AlertDialog.Builder(Init.getActivity()).setView(frame).setOnDismissListener(this::dismiss).show();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            Init.execute(() -> startService(data.getParams()));
            Init.show("请使用阿里云盘 App 扫描二维码");
        } catch (Exception ignored) {
        }
    }

    private void startService(Map<String, String> params) {
        service = Executors.newScheduledThreadPool(1);
        service.scheduleAtFixedRate(() -> {
            Data result = Data.objectFrom(OkHttp.post("https://passport.aliyundrive.com/newlogin/qrcode/query.do?appName=aliyun_drive&fromSite=52&_bx-v=2.2.3", params)).getContent().getData();
            if (result.hasToken()) setToken(result.getToken());
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void setToken(String value) {
        Prefers.put("token", value);
        Init.show("请重新进入播放页");
        auth.setRefreshToken(value);
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
