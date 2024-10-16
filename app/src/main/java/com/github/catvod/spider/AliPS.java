package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ColaMint & FongMi
 */
public class AliPS extends Ali {

    private final String siteUrl = "https://www.alipansou.com";
    private Map<String, String> getHeaders(String id) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Utils.CHROME);
        headers.put("Referer", siteUrl+id);
        headers.put("_bid", "6d14a5dd6c07980d9dc089a693805ad8");
        return headers;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        if (!Utils.regexAli.matcher(id).find()) {
            String [] arr=id.split("\\$\\$\\$");
            String url = arr[0].replace("/s/", "/cv/");
            Map<String, List<String>> respHeaders = new HashMap<>();
            OkHttp.stringNoRedirect(url, getHeaders(arr[0]), respHeaders);
            url = OkHttp.getRedirectLocation(respHeaders);
            arr[0] = url;
            String uid = TextUtils.join("$$$",arr);
            return super.detailContent(Arrays.asList(uid));
        }
        return super.detailContent(ids);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        Map<String, String> types = new HashMap<>();
        types.put("7", "资料夹");
        types.put("1", "影片");
        List<Vod> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("(时间: \\S+)");
        Iterator entries = types.entrySet().iterator();
        String dx = null, pic = "https://inews.gtimg.com/newsapp_bt/0/13263837859/1000";
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            String sb2 = siteUrl + "/search?k=" + URLEncoder.encode(key) + "&t=" + (String) entry.getKey();
            Document doc = Jsoup.parse(OkHttp.string(sb2));
            Elements Data = doc.select("van-row a");
            for (int i = 0; i < Data.size(); i++) {
                Element next = Data.get(i);
                String filename = next.select("template div").text();
                Matcher matcher = pattern.matcher(filename);
                if (!matcher.find())
                    continue;
                String remark = matcher.group(1);
                remark = remark.replace("时间: ", "");
                if (filename.contains(key)) {
                    String id = siteUrl + next.attr("href");
                    filename = filename.trim().replace("\uD83D\uDD25","");
                    String title = filename;
                    title = filename.replaceAll(" 时间: .*", "");
                    if (filename.contains("大小")) {
                        dx = filename.replaceAll(".*大小: (.*)", "$1");
                        remark = remark + " " + dx;
                    }
                    Vod vod = new Vod();
                    vod.setVodId(id + "$$$" + pic + "$$$" + title);
                    vod.setVodName(title);
                    vod.setVodPic(pic);
                    vod.setVodRemarks(remark);
                    list.add(vod);
                }
            }
        }
        return Result.string(list);
    }
}
