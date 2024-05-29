package com.github.catvod.spider;

import android.content.Context;
import android.util.Base64;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class YiSo extends Ali {
    private String satoken="b943b8c6-a10a-4ebc-814f-a07686957f5e";
    @Override
    public void init(Context context, String extend) {
        String[] split = inits(context, extend, "https://yiso.fun/");
        if(split.length==3) satoken = split[2];
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 12; V2049A Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/103.0.5060.129 Mobile Safari/537.36");
        headers.put("Referer", siteUrl);
        headers.put("Cookie", "satoken="+satoken);
        return headers;
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String poststr = OkHttp.string(siteUrl+"search?name=" + URLEncoder.encode(key) + "&pageNo=1&from=ali", getHeaders());
        SpiderDebug.log(poststr);
        JSONArray jsonArray = new JSONObject(poststr).getJSONObject("data").getJSONArray("list");
        ArrayList<Vod> arrayList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Vod vod = new Vod();
            String string = jsonArray.getJSONObject(i).getJSONArray("fileInfos").getJSONObject(0).getString("fileName");
            String string2 = jsonArray.getJSONObject(i).getString("gmtCreate");
            vod.setVodId(decrypt(jsonArray.getJSONObject(i).getString("url")));
            vod.setVodName(string);
            vod.setVodRemarks(string2);
            arrayList.add(vod);
        }
        return Result.string(arrayList);
    }

    public static String decrypt(String str) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec("4OToScUFOaeVTrHE".getBytes("UTF-8"), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec("9CLGao1vHKqm17Oz".getBytes("UTF-8"));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(2, secretKeySpec, ivParameterSpec);
            return new String(cipher.doFinal(Base64.decode(str.getBytes(), 0)), "UTF-8");
        } catch (Exception unused) {
            return "";
        }
    }
}