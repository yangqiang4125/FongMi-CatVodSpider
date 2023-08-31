package com.github.catvod.spider;

import android.net.Uri;
import android.text.TextUtils;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PushAgent extends Ali {

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (Ali.pattern.matcher(ids.get(0)).find()) return super.detailContent(ids);
        return Result.string(vod(ids.get(0)));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (flag.equals("直连")) return Result.get().url(id).subs(getSubs(id)).string();
        if (flag.equals("嗅探")) return Result.get().parse().url(id).string();
        if (flag.equals("解析")) return Result.get().parse().jx().url(id).string();
        if(Utils.matcher("[a-z0-9]{36,46}",id).matches())return super.playerContent(flag, id,vipFlags);
        return Result.get().parse(0).url(id).string();
    }

    private Vod vod(String url) {
        String[] idInfo = url.split("\\$\\$\\$");
        String url2 = idInfo[0];
        String spName = url2;
        if (idInfo.length > 2)  spName = idInfo[2].trim();
        else  spName = Utils.getWebName(url2, 0);
        Vod vod = new Vod();
        vod.setVodId(url2);
        vod.setTypeName("QQPush");
        vod.setVodName(spName);
        vod.setVodPic("http://image.xinjun58.com/sp/pic/bg/zl.jpg");
        vod.setVodPlayFrom(TextUtils.join("$$$", Arrays.asList("直连", "嗅探", "解析")));
        vod.setVodPlayUrl(TextUtils.join("$$$", Arrays.asList("播放$" + url2, "播放$" + url2, "播放$" + url2)));
        return vod;
    }

    private List<Sub> getSubs(String url) {
        List<Sub> subs = new ArrayList<>();
        if (url.startsWith("file://")) setFileSub(url, subs);
        if (url.startsWith("http://")) setHttpSub(url, subs);
        return subs;
    }

    private void setHttpSub(String url, List<Sub> subs) {
        List<String> vodTypes = Arrays.asList("mp4", "mkv");
        List<String> subTypes = Arrays.asList("srt", "ass");
        if (!vodTypes.contains(Utils.getExt(url))) return;
        for (String ext : subTypes) detectSub(url, ext, subs);
    }

    private void detectSub(String url, String ext, List<Sub> subs) {
        url = Utils.removeExt(url).concat(".").concat(ext);
        if (OkHttp.string(url).length() < 100) return;
        String name = Uri.parse(url).getLastPathSegment();
        subs.add(Sub.create().name(name).ext(ext).url(url));
    }

    private void setFileSub(String url, List<Sub> subs) {
        File file = new File(url.replace("file://", ""));
        if (file.getParentFile() == null) return;
        for (File f : file.getParentFile().listFiles()) {
            String ext = Utils.getExt(f.getName());
            if (Utils.isSub(ext)) subs.add(Sub.create().name(Utils.removeExt(f.getName())).ext(ext).url("file://" + f.getAbsolutePath()));
        }
    }
}