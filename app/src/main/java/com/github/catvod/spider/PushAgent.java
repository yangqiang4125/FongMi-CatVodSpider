package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Misc;

import java.util.List;

public class PushAgent extends Spider {

    private Ali ali;

    @Override
    public void init(Context context, String extend) {
        ali = Init.getAli().token(extend);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0).trim();
        if (url.contains("aliyundrive")) return ali.detailContent(ids);
        if (Misc.isVip(url)) return Result.string(ali.vod(url, "官源"));
        if (Misc.isVideoFormat(url)) return Result.string(ali.vod(url, "直连"));
        return Result.string(ali.vod(url, "网页"));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (flag.contains("画")) return ali.playerContent(flag, id);
        if (flag.equals("官源")) return Result.get().parse().jx().url(id).string();
        if (flag.equals("网页")) return Result.get().parse().url(id).string();
        return Result.get().url(id).string();
    }
}