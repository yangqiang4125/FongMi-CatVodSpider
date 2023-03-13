package com.github.catvod.spider;

import com.github.catvod.bean.Result;
import com.github.catvod.utils.Utils;

import java.util.List;

public class PushAgent extends Ali {

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0).trim();
        if (url.contains("aliyundrive")) return super.detailContent(ids);
        if (Utils.isVip(url)) return Result.string(super.vod(url, "官源"));
        if (Utils.isVideoFormat(url)) return Result.string(super.vod(url, "直连"));
        return Result.string(super.vod(url, "网页"));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (flag.equals("直连")) return Result.get().url(id).string();
        if (flag.equals("网页")) return Result.get().parse().url(id).string();
        if (flag.equals("官源")) return Result.get().parse().jx().url(id).string();
        return super.playerContent(flag, id, vipFlags);
    }
}