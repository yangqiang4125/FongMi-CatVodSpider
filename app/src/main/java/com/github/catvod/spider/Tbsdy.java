package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * 土拨鼠
 * @author Y q
 */
public class Tbsdy extends Ali{

    private final String siteURL = "https://www.tbsdy.com";
    private MyQQ qq;
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Utils.CHROME);
        return header;
    }

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
        String extJson = extend;
        if(extend==null||extend.isEmpty()||!extend.endsWith(".json"))extJson ="{\n" +
                "  \"ua\":1,\n" +
                "  \"name\": \"土拨鼠\",\n" +
                "  \"siteUrl\": \"https://www.tbsdy.com\", \n" +
                "  \"types\": \"电影$/classify.html?category=982105&sort_type=3&paged=#国产剧$/classify.html?category=982110&area=982305&sort_type=3&paged=#韩剧$/classify.html?category=982110&area=982330&sort_type=3&paged=#日剧$/classify.html?category=982110&area=982325&sort_type=3&paged=#综艺$/classify.html?category=982115&sort_type=3&paged=#动漫$/classify.html?category=982120&sort_type=3&paged=\", \n" +
                "  \"end\": \"\", \n" +
                "  \"search\":\"\",\n" +
                "  \"sbox\":\"\",\n" +
                "  \"idetail\":\"/video/%.html\",\n" +
                "  \"page\":\"var totalPage = \\\"(\\\\d+?)\\\";\",\n" +
                "  \"elbox\": \".classify_main_resources a\",\n" +
                "  \"elurl\": \"@href\", \n" +
                "  \"elname\": \".resource_name@text\",\n" +
                "  \"elpic\": \".hot_resource_one_t img@data-url\",\n" +
                "  \"elremarks\": \".resource_score@text\"\n" +
                "} ";
        qq = new MyQQ(extJson);
    }

    @Override
    public String homeContent(boolean filter) {
        return qq.homeContent(filter);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            Result result = qq.getVods(tid, pg);
            return result.string();
        } catch (NumberFormatException e) {
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception{
        String searchURL = siteURL + "/search.html?keyword=" + URLEncoder.encode(key);
        String html = OkHttp.string(searchURL, getHeader());
        Element it = Jsoup.parse(html).selectFirst(".search_result_list .search_result_item");
        List<Vod> list = new ArrayList<>();
        Element el = null;
        String id=null,vodId=null,vodName=null,pic = null,remark="";
        el = it.selectFirst("a.search_result_item_left");
        id = siteURL + qq.getText(el, "@href");
        pic = qq.getText(el, "img@src");
        remark = qq.getText(el, ".search_video_img_point@text");
        id = getUrl(id);

        html = OkHttp.string(id, getHeader());
        Elements items = Jsoup.parse(html).select(".video_download_link_info .video_download_link_item");
        for (Element item : items) {
            el = item.selectFirst(".video_download_link_name div");
            vodId = qq.getText(el, "@href");
            if(vodId.contains("aliyundrive.com/")){
                vodName = qq.getText(el, "@text");
                if(vodName.contains(key)||key.contains(vodName))
                list.add(new Vod(vodId + "$$$" + pic + "$$$" + vodName, vodName, pic, remark));
            }
        }
        return Result.string(list);
    }

    public String getUrl(String url){
        String uri = url.replace(".html", "/cloud.html");
        return uri;
    }

    @Override
    public String detailContent(List<String> list) throws Exception{
        String ids =list.get(0);
        if (!ids.contains("aliyundrive.com")) {
            String[] arr = ids.split("\\$\\$\\$");
            String id = getUrl(arr[0]);
            String html = OkHttp.string(id, getHeader());
            Matcher matcher = Utils.regexAli.matcher(html);
            if (!matcher.find()) return "";
            arr[0] = matcher.group(1);
            String uid = TextUtils.join("$$$",arr);
            list.set(0, uid);
        }
        return super.detailContent(list);
    }
}
