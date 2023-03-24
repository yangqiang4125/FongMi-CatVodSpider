package com.github.catvod.utils;

import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static JSONObject siteRule = null;
    public static String zzy=null;
    public static String spRegx = ".*(Ep|EP|E|第)(\\d+)[\\.|集]?.*";//集数数字正则匹配
    public static Integer isPic=0;
    public static String refreshToken=null;
    public static String tokenInfo=null;
    public static String jsonUrl = "http://test.xinjun58.com/sp/d.json";
    public static String apikey = "0ac44ae016490db2204ce0a042db2916";//豆瓣key
    private static String a = "(https:\\/\\/www.aliyundrive.com\\/s\\/[^\\\"]+)";
    public static final Pattern regexAli = Pattern.compile("(https://www.aliyundrive.com/s/[^\"]+)");
    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36";
    public static final String MOBILE = "Mozilla/5.0 (Linux; Android 11; Ghxi Build/RKQ1.200826.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/12.16 SearchCraft/3.9.1 (Baidu; P1 11)";
    public static boolean isVip(String url) {
        List<String> hosts = Arrays.asList("iqiyi.com", "v.qq.com", "youku.com", "le.com", "tudou.com", "mgtv.com", "sohu.com", "acfun.cn", "bilibili.com", "baofeng.com", "pptv.com");
        for (String host : hosts) if (url.contains(host)) return true;
        return false;
    }

    public static boolean isVideoFormat(String url) {
        if (url.contains("url=http") || url.contains(".js") || url.contains(".css") || url.contains(".html")) return false;
        return Sniffer.RULE.matcher(url).find();
    }

    public static boolean isSub(String ext) {
        return ext.equals("srt") || ext.equals("ass") || ext.equals("ssa");
    }
    public static String getWebName(String url,int type){
        if (url.contains("mgtv.com")) {
            if(type==0) return "芒果TV";
            if(type==1) return "http://image.xinjun58.com/sp/pic/bg/mgtv.jpg";
        }
        if (url.contains("qq.com")) {
            if(type==1) return "http://image.xinjun58.com/sp/pic/bg/qq.jpg";
            return "腾讯视频";
        }
        if (url.contains("iqiyi.com")) {
            if(type==1) return "http://image.xinjun58.com/sp/pic/bg/iqiyi.jpg";
            return "爱奇艺";
        }
        if (url.contains("youku.com")) {
            if(type==1) return "http://image.xinjun58.com/sp/pic/bg/youku.jpg";
            return "优酷";
        }
        if (url.contains("bilibili.com")) {
            if(type==1) return "http://image.xinjun58.com/sp/pic/bg/bili.jpg";
            return "哗哩哔哩";
        }
        if (url.startsWith("magnet")) {
            return "磁力";
        }
        if (url.contains("aliyundrive")) {
            if(type==1) return "http://image.xinjun58.com/sp/pic/bg/ali.jpg";
            return "阿里云";
        }
        if(type==1)return "http://image.xinjun58.com/sp/pic/bg/zl.jpg";
        String host = Uri.parse(url).getHost();
        return host;
    }
    public static String getSize(double size) {
        if (size == 0) return "";
        if (size > 1024 * 1024 * 1024 * 1024.0) {
            size /= (1024 * 1024 * 1024 * 1024.0);
            return String.format(Locale.getDefault(), "%.2f%s", size, "TB");
        } else if (size > 1024 * 1024 * 1024.0) {
            size /= (1024 * 1024 * 1024.0);
            return String.format(Locale.getDefault(), "%.2f%s", size, "GB");
        } else if (size > 1024 * 1024.0) {
            size /= (1024 * 1024.0);
            return String.format(Locale.getDefault(), "%.2f%s", size, "MB");
        } else {
            size /= 1024.0;
            return String.format(Locale.getDefault(), "%.2f%s", size, "KB");
        }
    }

    public static String fixUrl(String base, String src) {
        try {
            if (src.startsWith("//")) {
                Uri parse = Uri.parse(base);
                src = parse.getScheme() + ":" + src;
            } else if (!src.contains("://")) {
                Uri parse = Uri.parse(base);
                src = parse.getScheme() + "://" + parse.getHost() + src;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return src;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) {
            return text.substring(0, text.length() - num);
        } else {
            return text;
        }
    }

    public static Matcher matcher(String regx, String content) {
        Pattern pattern = Pattern.compile(regx);
        return pattern.matcher(content);
    }

    public static boolean isNumeric(String str){
        if(str==null||str.isEmpty())return false;
        Pattern pattern = Pattern.compile("[0-9]*");
        return pattern.matcher(str).matches();
    }

    public static int cs(String msg1,String regx){
        String msg = msg1.replaceAll(regx, "⑩");
        Matcher ma = matcher("⑩", msg);//指定字符串出现的次数
        int c = 0;
        while (ma.find()) {
            c++;
        }
        return c;
    }

    public static String getBstr(String ss,boolean f){
        String s = ss.replace("4K", "").replace("mp4", "");
        if(!f) s = s.replaceFirst("1080", "");
        return s;
    }

    public static String getBx(String vod_play_url){
        int z = 0;//更换第一个
        String[] playUrls = vod_play_url.split("\\$\\$\\$");
        String s = playUrls[z];
        String type = "";
        boolean f = false;
        if (s.contains("4K")) {
            type = "4K";
        }else if (s.contains("4k")) {
            type = "4K";
        }else if (s.contains("1080")) {
            if(!s.contains("1079"))type = "1080";
            else f = true;
        }
        Map<String, String> hashMap = new LinkedHashMap<>();
        String[] urls = s.split("#");

        for (String url : urls) {
            String[] arr = url.split("\\$");
            hashMap.put(arr[0], arr[1]);
        }
        ArrayList<String> arrayList2 = new ArrayList<>(hashMap.keySet());
        hashMap =getBx(arrayList2, hashMap, type,f);

        List<String> zlist = new ArrayList<>();
        for (String k : hashMap.keySet()) {
            zlist.add(k + "$" + hashMap.get(k));
        }
        Collections.sort(zlist, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });
        String zstr = TextUtils.join("#", zlist);
        playUrls[z]=zstr;
        String zs = TextUtils.join("$$$", playUrls);
        return zs;
    }

    public static  Map<String, String> getBx(List<String> list,Map<String, String> map,String type,boolean f){
        String iname="",rname="",zname="";
        String regx = spRegx;//".*(Ep|EP|E|第)(\\d+)[\\.|集]?.*";
        Matcher ma = null;
        boolean flag = false;
        String ss = list.get(0);
        String s0 = getBstr(ss, f);
        if(!s0.equals(ss)) flag = true;
        int c = cs(s0, "\\d+"), index = 0;
        Map<String, String> m = new LinkedHashMap<>();
        for (String name : list) {
            zname = name;
            if (matcher(regx, name).find()) {
                iname = name.replaceAll(regx, "$2");
            }else {
                if (name.startsWith("[")) {
                    name = name.replaceAll("\\[.*?\\](.*)", "$1");
                }
                if (!f && list.size() < 200) {
                    name = name.replaceAll("\\d{4,8}", "");
                }
                name = name.replace("mp4", "").replace("4K","").replace("4k","").replace("1080P","").replace("1080p","");
                if (c==1) {
                    if(flag) rname = getBstr(name,f);
                    else rname = name;
                    ma = matcher("\\d+", rname);
                    while (ma.find()) {
                        iname = ma.group();
                    }
                }else if(matcher(".*(\\d+)集.*", name).find()){
                    iname = name.replaceAll(".*(\\d+)集.*", "$1");
                }else if(matcher("(\\d+).*", name).find()){
                    iname = name.replaceAll(".*?(\\d+).*", "$1");
                }else {
                    iname = name;
                }
            }
            if(iname.contains(".")&&iname.length()>5) iname = iname.substring(0, iname.lastIndexOf("."));
            if(isNumeric(iname)) {
                int zi = Integer.parseInt(iname);
                if(zi>index)index=zi;
                if(iname.length()==1)iname="0"+iname;
            } else iname = zname;
            if (type.isEmpty()||zname.contains(type)) {
                m.put(iname, map.get(zname));
            }
        }
        if (!type.isEmpty() && index > 0 && m.size() != index && list.size() == index) return getBx(list, map, "", f);
        return m;
    }

    public static String trim(String str) {
        return str == null ? str : str.replaceAll("^[\\s　|\\s ]*|[\\s　|\\s ]*$", "");
    }

    public static String getVar(String data, String param) {
        for (String var : data.split("var")) if (var.contains(param)) return var.split("'")[1];
        return "";
    }

    public static String MD5(String src) {
        return MD5(src, "UTF-8");
    }

    public static String MD5(String src, String charset) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(src.getBytes(charset));
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder sb = new StringBuilder(no.toString(16));
            while (sb.length() < 32) sb.insert(0, "0");
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    public static DisplayMetrics getDisplayMetrics() {
        return Init.context().getResources().getDisplayMetrics();
    }

    public static int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getDisplayMetrics());
    }

    public static void loadUrl(WebView webView, String script) {
        loadUrl(webView, script, null);
    }

    public static void loadUrl(WebView webView, String script, ValueCallback<String> callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) webView.evaluateJavascript(script, callback);
        else webView.loadUrl(script);
    }

    public static void addView(View view, ViewGroup.LayoutParams params) {
        try {
            ViewGroup group = Init.getActivity().getWindow().getDecorView().findViewById(android.R.id.content);
            group.addView(view, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void removeView(View view) {
        try {
            ViewGroup group = Init.getActivity().getWindow().getDecorView().findViewById(android.R.id.content);
            group.removeView(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadWebView(String url, WebViewClient client) {
        Init.run(() -> {
            WebView webView = new WebView(Init.context());
            webView.getSettings().setDatabaseEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setJavaScriptEnabled(true);
            addView(webView, new ViewGroup.LayoutParams(0, 0));
            webView.setWebViewClient(client);
            webView.loadUrl(url);
        });
    }
}
