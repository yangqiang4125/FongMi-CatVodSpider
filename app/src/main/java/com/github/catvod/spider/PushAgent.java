package com.github.catvod.spider;

import android.net.Uri;
import android.text.TextUtils;
import com.github.catvod.ali.API;
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
    private String zlpic="http://image.xinjun58.com/sp/pic/bg/zl.jpg";
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        String [] arr=null;
        if(url.contains(","))arr = url.split(",");
        else if(url.contains(" "))arr = url.split(" ");
        if(arr!=null&&arr.length>1)url = arr[1]+"$$$"+zlpic+"$$$"+arr[0];
        ids.set(0, url);
        if (Utils.regexAli.matcher(url).find()) return super.detailContent(ids);
        return Result.string(vod(url));
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
        String spName = url2;boolean flag = false;
        String pic=zlpic;
        if (idInfo.length > 2)  {
            flag=true;
            if(!idInfo[1].isEmpty()) pic = idInfo[1];
            spName = idInfo[2].trim();
            url = url+"$$$1";
            idInfo = url.split("\\$\\$\\$");
        } else  spName = Utils.getWebName(url2, 0);
        Vod vod = new Vod();
        vod.setVodId(url2);
        vod.setTypeName("QQPush");
        if(!Utils.matcher("[\\u4e00-\\u9fa5]",spName).matches()) spName = spName + ".";
        vod.setVodName(spName);
        vod.setVodPic(pic);
        vod.setVodPlayFrom(TextUtils.join("$$$", Arrays.asList("直连", "嗅探", "解析")));
        vod.setVodPlayUrl(TextUtils.join("$$$", Arrays.asList("播放$" + url2, "播放$" + url2, "播放$" + url2)));
        if(flag) vod = API.get().getVodInfo(spName, vod, idInfo);
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
