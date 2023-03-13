package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Result;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class PushAgentQQ extends Ali {
    @Override
    public void init(Context context, String extend) {
        fetchRule(true,1);
    }
    private static String douban_api_host = "https://frodo.douban.com/api/v2";
    public static JSONObject getUrls(){
        String _urls = "{" +
                "                \"search\": \"/search/weixin\"," +
                "                \"search_agg\": \"/search\"," +
                "                \"movie_recommend\": \"/movie/recommend\"," +
                "                \"movie_tag\": \"/movie/tag\"," +
                "                \"tv_tag\": \"/tv/tag\"," +
                "                \"movie_search\": \"/search/movie\"," +
                "                \"tv_search\": \"/search/movie\"," +
                "                \"book_search\": \"/search/book\"," +
                "                \"group_search\": \"/search/group\"," +
                "                \"movie_showing\": \"/subject_collection/movie_showing/items\"," +
                "                \"movie_hot_gaia\": \"/subject_collection/movie_hot_gaia/items\"," +
                "                \"movie_soon\": \"/subject_collection/movie_soon/items\"," +
                "                \"movie_top250\": \"/subject_collection/movie_top250/items\"," +
                "                \"movie_scifi\": \"/subject_collection/movie_scifi/items\"," +
                "                \"movie_comedy\": \"/subject_collection/movie_comedy/items\"," +
                "                \"movie_action\": \"/subject_collection/movie_action/items\"," +
                "                \"movie_love\": \"/subject_collection/movie_love/items\"," +
                "                \"tv_hot\": \"/subject_collection/tv_hot/items\"," +
                "                \"tv_domestic\": \"/subject_collection/tv_domestic/items\"," +
                "                \"tv_american\": \"/subject_collection/tv_american/items\"," +
                "                \"tv_japanese\": \"/subject_collection/tv_japanese/items\"," +
                "                \"tv_korean\": \"/subject_collection/tv_korean/items\"," +
                "                \"tv_animation\": \"/subject_collection/tv_animation/items\"," +
                "                \"tv_variety_show\": \"/subject_collection/tv_variety_show/items\"," +
                "                \"tv_chinese_best_weekly\": \"/subject_collection/tv_chinese_best_weekly/items\"," +
                "                \"tv_global_best_weekly\": \"/subject_collection/tv_global_best_weekly/items\"," +
                "                \"show_hot\": \"/subject_collection/show_hot/items\"," +
                "                \"show_domestic\": \"/subject_collection/show_domestic/items\"," +
                "                \"show_foreign\": \"/subject_collection/show_foreign/items\"," +
                "                \"book_bestseller\": \"/subject_collection/book_bestseller/items\"," +
                "                \"book_top250\": \"/subject_collection/book_top250/items\"," +
                "                \"book_fiction_hot_weekly\": \"/subject_collection/book_fiction_hot_weekly/items\"," +
                "                \"book_nonfiction_hot_weekly\": \"/subject_collection/book_nonfiction_hot_weekly/items\"," +
                "                \"music_single\": \"/subject_collection/music_single/items\"," +
                "                \"movie_rank_list\": \"/movie/rank_list\"," +
                "                \"movie_year_ranks\": \"/movie/year_ranks\"," +
                "                \"book_rank_list\": \"/book/rank_list\"," +
                "                \"tv_rank_list\": \"/tv/rank_list\"," +
                "                \"movie_detail\": \"/movie/\"," +
                "                \"movie_rating\": \"/movie/%s/rating\"," +
                "                \"movie_photos\": \"/movie/%s/photos\"," +
                "                \"movie_trailers\": \"/movie/%s/trailers\"," +
                "                \"movie_interests\": \"/movie/%s/interests\"," +
                "                \"movie_reviews\": \"/movie/%s/reviews\"," +
                "                \"movie_recommendations\": \"/movie/%s/recommendations\"," +
                "                \"movie_celebrities\": \"/movie/%s/celebrities\"," +
                "                \"tv_detail\": \"/tv/\"," +
                "                \"tv_rating\": \"/tv/%s/rating\"," +
                "                \"tv_photos\": \"/tv/%s/photos\"," +
                "                \"tv_trailers\": \"/tv/%s/trailers\"," +
                "                \"tv_interests\": \"/tv/%s/interests\"," +
                "                \"tv_reviews\": \"/tv/%s/reviews\"," +
                "                \"tv_recommendations\": \"/tv/%s/recommendations\"," +
                "                \"tv_celebrities\": \"/tv/%s/celebrities\"," +
                "                \"book_detail\": \"/book/\"," +
                "                \"book_rating\": \"/book/%s/rating\"," +
                "                \"book_interests\": \"/book/%s/interests\"," +
                "                \"book_reviews\": \"/book/%s/reviews\"," +
                "                \"book_recommendations\": \"/book/%s/recommendations\"," +
                "                \"music_detail\": \"/music/\"," +
                "                \"music_rating\": \"/music/%s/rating\"," +
                "                \"music_interests\": \"/music/%s/interests\"," +
                "                \"music_reviews\": \"/music/%s/reviews\"," +
                "                \"music_recommendations\": \"/music/%s/recommendations\" }";

        try {
            JSONObject jsonObject = new JSONObject(_urls);
            return jsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static JSONArray getDouban(String key,String sort,Integer count) throws JSONException {
        if(sort==null)sort="U";
        if(count ==null) count =120;
        JSONObject ju = getUrls();
        String url = douban_api_host + ju.getString(key)+"?sort="+sort+"&start=0&count="+count+"&apikey=" + Utils.apikey + "&channel=Douban";
        String json = OkHttp.string(url, getHeaderDB());
        JSONArray jSONArray = new JSONArray();
        JSONObject jo = new JSONObject(json),o1 = null,op1=null,vo=null;

        JSONArray ay = jo.getJSONArray("subject_collection_items");
        String remark = "", title = "",pic="";
        Object o = null;
        for (int i = 0; i < ay.length(); i++) {
            JSONObject v = ay.getJSONObject(i);
            vo = new JSONObject();
            title = v.getString("title");
            o = v.get("rating");
            if (!o.toString().equals("null")) {
                o1 = v.getJSONObject("rating");
                remark = o1.optString("value", "暂无评分");
            }else {
                remark = "暂无评分";
            }
            op1 = v.getJSONObject("pic");
            pic = op1.optString("normal", "");
            vo.put("vod_id", "");
            vo.put("vod_name", title);
            vo.put("vod_remarks", remark);
            vo.put("vod_pic", pic);
            jSONArray.put(vo);
        }
        return jSONArray;
    }

    protected static HashMap<String, String> getHeaderDB() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Host","frodo.douban.com");
        headers.put("Connection", "Keep-Alive");
        headers.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html");
        headers.put("content-type", "application/json");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject jo = Ali.fetchRule(true,1);
            JSONArray classes = new JSONArray();
            String[] fenleis = Ali.getRuleVal(Utils.siteRule,"fenlei", "").split("#");
            for (String fenlei : fenleis) {
                String[] info = fenlei.split("\\$");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type_name", info[0]);
                jsonObject.put("type_id", info[1]);
                classes.put(jsonObject);
            }
            JSONObject result = category("t1", "1", true, null,jo);
            result.put("class", classes);
            return result.toString();
        } catch (
                Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }
    @Override
    public String homeVideoContent() {
        return homeContent(true);
    }

    private JSONObject category(String tid, String pg, boolean filter, HashMap<String, String> extend,JSONObject jo) {
        try {
            JSONArray videos = new JSONArray();
            String url=null,name=null,pic=null,skey=null,sid=null;
            JSONObject jsonObject = null, v = null;
            if (tid.equals("bili")) {
                String json = OkHttp.string("https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all");
                JSONObject j = new JSONObject(json);
                JSONObject o = j.getJSONObject("data");
                JSONArray array = o.getJSONArray("list");
                for (int i = 0; i < array.length(); i++) {
                    jsonObject = array.getJSONObject(i);
                    url = jsonObject.optString("short_link", "");
                    name = jsonObject.optString("title", "");
                    pic = jsonObject.optString("pic", "");
                    v = new JSONObject();
                    v.put("vod_id", url + "$$$" + pic + "$$$" + name);
                    v.put("vod_name", name);
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", "");
                    videos.put(v);
                }
            }else if (tid.startsWith("douban-")) {
                String [] arr = tid.split("-");
                String key = arr[1];
                String sort = null;
                Integer count = 120;
                if(arr.length>2)sort = arr[2];
                if(arr.length>3)count = Integer.parseInt(arr[3]);
                videos = getDouban(key, sort, count);
            }else{
                if (jo == null) jo = Ali.fetchRule(true,1);
                JSONArray array = jo.getJSONArray(tid);
                for (int i = 0; i < array.length(); i++) {
                    jsonObject = array.getJSONObject(i);
                    url = Ali.getRuleVal(jsonObject, "url");
                    name = Ali.getRuleVal(jsonObject, "name");
                    pic = Ali.getRuleVal(jsonObject, "pic");
                    skey = Ali.getRuleVal(jsonObject, "key");
                    sid = url + "$$$" + pic + "$$$" + name;
                    if (tid.equals("t3")&&!sid.isEmpty()) {
                        sid = sid +","+skey;
                    }
                    if(pic.equals("")) pic = Utils.getWebName(url, 1);
                    v = new JSONObject();
                    v.put("vod_id", sid);
                    v.put("vod_name", name);
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", Utils.getWebName(url,0));
                    videos.put(v);
                }
            }
            JSONObject result = new JSONObject();
            result.put("page", pg);
            result.put("pagecount", 1);
            result.put("limit", Integer.MAX_VALUE);
            result.put("total", Integer.MAX_VALUE);
            result.put("list", videos);
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JSONObject obj = category(tid, pg, filter, extend,null);
        return obj != null ? obj.toString() : "";
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0).trim();
        if (url.contains("aliyundrive")) return super.detailContent(ids);
        if (Utils.isVip(url)) return Result.string(vod(url, "官源"));
        if (Utils.isVideoFormat(url)) return Result.string(vod(url, "直连"));
        return Result.string(vod(url, "网页"));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (flag.contains("画")) return super.playerContent(flag, id,vipFlags);
        if (flag.equals("官源")) return Result.get().parse().jx().url(id).string();
        if (flag.equals("网页")) return Result.get().parse().url(id).string();
        return Result.get().url(id).string();
    }
}
