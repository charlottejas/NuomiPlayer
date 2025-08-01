package com.example.myapplication.shared;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Bundle;

import androidx.annotation.NonNull;

import android.support.v4.media.MediaBrowserCompat;


import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.MediaBrowserServiceCompat;


import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;


import java.util.Collections;
import java.util.List;


public class MyMusicService extends MediaBrowserServiceCompat {

    private MediaSessionCompat mSession;                       // 本地 MediaSession
    private MediaControllerCompat remoteCtrl;                  // 指向 QQ 音乐的控制器
    private final MediaControllerCompat.Callback remoteCb = new RemoteCallback(); // 监听 QQ 音乐状态变化

    // =========================================================
    // 🔁 QQ 音乐状态回调：将元数据 / 播放状态同步给本地 Session
    // =========================================================
    private class RemoteCallback extends MediaControllerCompat.Callback {
        @Override public void onMetadataChanged(MediaMetadataCompat m) {
            mirror(m, null);
        }

        @Override public void onPlaybackStateChanged(PlaybackStateCompat s) {
            mirror(null, s);
        }
    }

    // =========================================================
    // 🪞 同步 QQ 音乐信息到本地 Session
    // =========================================================
    private void mirror(MediaMetadataCompat meta, PlaybackStateCompat st) {

        // --- 1. 同步元数据 ---
        if (meta != null) {
            mSession.setMetadata(meta);

            // （可选）告知 Android Auto 有播放队列
            /*
            MediaSessionCompat.QueueItem qi = new MediaSessionCompat.QueueItem(meta.getDescription(), 0);
            mSession.setQueue(Collections.singletonList(qi));
            mSession.setActiveQueueItemId(0L); // 注意：ID 是 long 型
            */
        }

        // --- 2. 同步播放状态 ---
        if (st != null) {
            int code = st.getState();

            // 忽略 STOPPED/NONE 保持 UI 稳定
            if (code == PlaybackStateCompat.STATE_NONE ||
                    code == PlaybackStateCompat.STATE_STOPPED) {
                return;
            }

            // 其余状态正常写入（PLAYING/PAUSED/...）
            mSession.setPlaybackState(st);
        }
    }

    // =========================================================
    // 📡 本地广播接收器：接收 QQ 音乐的 Token 并构建 Controller
    // =========================================================
    private final BroadcastReceiver tokenRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            MediaSessionCompat.Token tk = i.getParcelableExtra("binder");
            if (tk == null) return;

            // 切换 Controller
            if (remoteCtrl != null) remoteCtrl.unregisterCallback(remoteCb);
            remoteCtrl = new MediaControllerCompat(MyMusicService.this, tk);
            remoteCtrl.registerCallback(remoteCb);

            // 初次同步状态 + 元数据
            mirror(remoteCtrl.getMetadata(), remoteCtrl.getPlaybackState());
        }
    };

    // =========================================================
    // 🚀 启动服务：初始化本地 MediaSession 并设置转发逻辑
    // =========================================================
    @Override
    public void onCreate() {
        super.onCreate();

        // ① 建立本地 MediaSession（只读，不处理媒体按钮）
        mSession = new MediaSessionCompat(this, "MirrorSession");
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS); // 不处理 TransportControls
        mSession.setActive(true);
        setSessionToken(mSession.getSessionToken());

        // ② 设置媒体控制行为：将本地播放控制请求转发给 QQ 音乐
        mSession.setCallback(new MediaSessionCompat.Callback() {

            @Override public void onPlay() {
                if (remoteCtrl != null)
                    remoteCtrl.getTransportControls().play();
            }

            @Override public void onPause() {
                if (remoteCtrl != null)
                    remoteCtrl.getTransportControls().pause();
            }

            @Override public void onSkipToNext() {
                if (remoteCtrl != null)
                    remoteCtrl.getTransportControls().skipToNext();
            }

            @Override public void onSkipToPrevious() {
                if (remoteCtrl != null)
                    remoteCtrl.getTransportControls().skipToPrevious();
            }

            @Override public void onSeekTo(long positionMs) {
                // ① 转发拖动请求
                if (remoteCtrl != null) {
                    remoteCtrl.getTransportControls().seekTo(positionMs);
                }

                // ② 本地 UI 立即刷新（防止进度条卡住）
                PlaybackStateCompat remoteState =
                        (remoteCtrl != null) ? remoteCtrl.getPlaybackState() : null;

                if (remoteState != null) {
                    // 用 QQ 音乐的 PlaybackState（含新 position）
                    mSession.setPlaybackState(remoteState);
                } else {
                    // 没拿到 QQ 状态时手动构造一个简单播放状态
                    mSession.setPlaybackState(
                            new PlaybackStateCompat.Builder()
                                    .setState(PlaybackStateCompat.STATE_PLAYING,
                                            positionMs,
                                            1.0f) // 播放速度 1x
                                    .build()
                    );
                }
            }
        });

        // ③ 注册广播监听，等待 QQ 音乐 Token 注入
        IntentFilter f = new IntentFilter("com.example.ACTION_QQ_CONTROLLER");
        LocalBroadcastManager.getInstance(this).registerReceiver(tokenRx, f);
    }

    // =========================================================
    // 🧹 资源释放
    // =========================================================
    @Override
    public void onDestroy() {
        mSession.release();
    }

    // =========================================================
    // 🚪 MediaBrowser 接口（供 Android Auto 探测）
    // =========================================================
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 Bundle rootHints) {
        return new BrowserRoot("root", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(Collections.emptyList());
    }
}
