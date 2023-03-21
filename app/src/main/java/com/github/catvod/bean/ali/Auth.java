package com.github.catvod.bean.ali;

import android.text.TextUtils;
import com.github.catvod.utils.Prefers;

public class Auth {

    private String refreshToken;
    private String accessToken;
    private String shareToken;
    private String signature;
    private String deviceId;
    private String shareId;
    private String userId;

    public String getRefreshToken() {
        return TextUtils.isEmpty(refreshToken) ? "" : refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        if(getAccessToken().isEmpty()||refreshToken.isEmpty()) Prefers.put("token", refreshToken);
    }

    public String getAccessToken() {
        return Prefers.getString("token");
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getShareToken() {
        return TextUtils.isEmpty(shareToken) ? "" : shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public String getSignature() {
        return TextUtils.isEmpty(signature) ? "" : signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getShareId() {
        return TextUtils.isEmpty(shareId) ? "" : shareId;
    }

    public void setShareId(String shareId) {
        this.shareId = shareId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isEmpty() {
        return getAccessToken().isEmpty();
    }

    public void clean() {
        setRefreshToken("");
        setAccessToken("");
        setShareId("");
    }
}
