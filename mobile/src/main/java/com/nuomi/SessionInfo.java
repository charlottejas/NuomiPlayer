package com.nuomi;


import android.graphics.drawable.Drawable;

public class SessionInfo {
    public final String packageName;
    public final String appLabel;
    public final Drawable appIcon;
    public final String nowPlaying; // 可能为 null

    public SessionInfo(String pkg, String label, Drawable icon, String nowPlaying) {
        this.packageName = pkg;
        this.appLabel = label;
        this.appIcon = icon;
        this.nowPlaying = nowPlaying;
    }
}
