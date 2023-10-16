package com.github.catvod.bean.ali;

import android.text.TextUtils;
import com.github.catvod.utils.Utils;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.github.catvod.ali.API;
public class Auth {

    @SerializedName("refreshToken")
    private String refreshToken;
    @SerializedName("refreshTokenOpen")
    private String refreshTokenOpen;
    @SerializedName("accessToken")
    private String accessToken;
    @SerializedName("accessTokenOpen")
    private String accessTokenOpen;
    @SerializedName("signature")
    private String signature;
    @SerializedName("userId")
    private String userId;
    @SerializedName("driveId")
    public String driveId;
    @SerializedName("nickName")
    public String nickName;
    @SerializedName("jtype")
    private String jtype;
    @SerializedName("time")
    public String time;

    public static Auth objectFrom(String str) {
        if(str.isEmpty())return new Auth();
        Auth item = new Gson().fromJson(str, Auth.class);
        return item == null ? new Auth() : item;
    }

    public String getRefreshToken() {
        if(!Utils.isToken(refreshToken)) refreshToken = "";
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshTokenOpen() { return Utils.getStr(refreshTokenOpen); }

    public void setRefreshTokenOpen(String refreshTokenOpen) {
        this.refreshTokenOpen = refreshTokenOpen;
    }

    public String getAccessToken() {
        return Utils.getStr(accessToken);
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessTokenOpen() { return Utils.getStr(accessTokenOpen); }

    public void setAccessTokenOpen(String accessTokenOpen) {
        this.accessTokenOpen = accessTokenOpen;
    }

    public String getSignature() { return Utils.getStr(signature); }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getDriveId() { return Utils.getStr(driveId); }

    public void setDriveId(String driveId) { this.driveId = driveId; }

    public String getNickName() { return Utils.getStr(nickName); }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getTime() { return Utils.getStr(time); }

    public void setTime(String time) {
        this.time = time;
    }

    public String getJtype() { return Utils.getStr(jtype); }

    public void setJtype(String jtype) {
        this.jtype = jtype;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isEmpty() {
        return getRefreshToken().isEmpty()||getAccessTokenOpen().isEmpty()||getRefreshTokenOpen().isEmpty();
    }

    public Auth clean() {
        setRefreshTokenOpen("");
        setAccessTokenOpen("");
        setRefreshToken("");
        setAccessToken("");
        setSignature("");
        return this;
    }

    public void save() {
        String time = Utils.getTime();
        setTime(time);
        setJtype(API.get().jtype);
        Prefers.put("aliyundrive", new Gson().toJson(this));
    }
    public String toJson() {
        return new Gson().toJson(this);
    }
}
