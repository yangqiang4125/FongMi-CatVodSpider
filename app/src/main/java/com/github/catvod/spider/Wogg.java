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
 * @author zhixc
 */
public class Wogg extends Ali {
    private final String siteURL = "https://tvfan.xxooo.cf";
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
                "  \"name\": \"Wogg\",\n" +
                "  \"siteUrl\": \"https://tvfan.xxooo.cf\", \n" +
                "  \"types\": \"国产剧$/index.php/vodshow/21--------#韩剧$/index.php/vodshow/23-韩国-------#港台剧$/index.php/vodshow/31--------#电影$/index.php/vodshow/1--------#综艺$/index.php/vodshow/28--------#动漫$/index.php/vodshow/24--------#音乐MV$/index.php/vodshow/32--------\", \n" +
                "  \"end\": \"---.html\", \n" +
                "  \n" +
                "  \"search\":\"\",\n" +
                "  \"sbox\":\"\",\n" +
                "  \"idetail\":\"/index.php/voddetail/%.html\",\n" +
                "  \n" +
                "  \"page\":\"#page a:last@href\",\n" +
                "  \"elbox\": \".module-items .module-item\",\n" +
                "  \"elurl\": \".module-item-cover .module-item-pic a@href\", \n" +
                "  \"elname\": \".module-item-cover .module-item-pic a@title\",\n" +
                "  \"elpic\": \".module-item-cover .module-item-pic img@src\",\n" +
                "  \"elremarks\": \".module-item-text\"\n" +
                "} ";
                qq = new MyQQ(extJson);
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
    public String searchContent(String key, boolean quick) throws Exception {
        String searchURL = siteURL + "/index.php/vodsearch/-------------.html?wd=" + URLEncoder.encode(key);
        String html = OkHttp.string(searchURL, getHeader());
        Elements items = Jsoup.parse(html).select(".module-search-item");
        List<Vod> list = new ArrayList<>();
        for (Element item : items) {
            String vodId =  siteURL+item.select(".video-serial").attr("href");
            String name = item.select(".video-serial").attr("title");
            String pic = item.select(".module-item-pic > img").attr("data-src");
            String remark = item.select(".video-tag-icon").text();
            list.add(new Vod(vodId + "$$$" + pic + "$$$" + name, name, pic, remark));
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> list) throws Exception {
        String id =list.get(0);
        if (!id.contains("aliyundrive.com")) {
            String[] arr = id.split("\\$\\$\\$");
            Matcher matcher = Utils.regexAli.matcher(OkHttp.string(arr[0], getHeader()));
            if (!matcher.find()) return "";
            arr[0] = matcher.group(1);
            String uid = TextUtils.join("$$$",arr);
            list.set(0, uid);
        }
        return super.detailContent(list);
    }
}