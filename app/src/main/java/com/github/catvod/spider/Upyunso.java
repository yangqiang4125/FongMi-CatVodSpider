package com.github.catvod.spider;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.upyun.Data;
import com.github.catvod.bean.upyun.Item;
import com.github.catvod.net.OkHttp;
import com.google.common.io.BaseEncoding;
import com.github.catvod.utils.Utils;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class Upyunso extends Ali {
    private static String b = "https://www.upyunso.com/";
    private String pic = "http://image.xinjun58.com/image/tv/ups.jpg";
    @Override
    public String searchContent(String key, boolean z) throws Exception {
        return searchContent(key, "1");
    }

    private String searchContent(String key, String pg) throws Exception {
        String res = decode(OkHttp.string("https://zyb.upyunso.com/v15/search?keyword=" + URLEncoder.encode(key) + "&page=" + pg + "&s_type=2"));
        List<Vod> list = new ArrayList<>();
        for (Item item : Data.objectFrom(res).getResult().getItems()) {
            String url = decode(item.getPageUrl());
            if (!Utils.regexAli.matcher(url).find()) continue;
            if (item.getTitle().contains(key)) list.add(item.url(url).getVod());
        }
        return Result.string(list);
    }

    private String decode(String data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec("qq1920520460qqzz".getBytes(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec("qq1920520460qqzz".getBytes());
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] encryptDataBytes = decodeHex(data.toUpperCase());
        byte[] decryptData = cipher.doFinal(encryptDataBytes);
        return new String(decryptData, "UTF-8");
    }

    private static byte[] decodeHex(String s) {
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = Integer.valueOf(s.substring(i * 2, i * 2 + 2), 16).byteValue();
        return bytes;
    }
}
