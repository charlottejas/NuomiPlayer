package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.media.session.MediaSession;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;

import android.support.v4.media.session.MediaSessionCompat;

import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import java.util.List;


@SuppressLint("OverrideAbstract")
public class QqSessionSniffer extends NotificationListenerService {

    private static final String QQ_PKG = "com.tencent.qqmusic";
    private MediaController qqCtrl;

    /* 当监听服务首次连接 & 每次 QQ 音乐推送新通知时，尝试刷新 Controller */
    @Override public void onListenerConnected() { refreshCtrl(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (QQ_PKG.equals(sbn.getPackageName())) refreshCtrl();
    }

    private void refreshCtrl() {
        MediaSessionManager sm =
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);

        // 用自己的监听器 ComponentName 作为参数
        ComponentName me = new ComponentName(this, QqSessionSniffer.class);
        List<MediaController> list = sm.getActiveSessions(me);   // ⬅ 改在这里

        if (list == null) return;
        for (MediaController c : list) {
            if (QQ_PKG.equals(c.getPackageName())) {
                if (qqCtrl == null ||
                        !qqCtrl.getSessionToken().equals(c.getSessionToken())) {
                    attach(c);
                }
                break;
            }
        }
    }


    private void attach(MediaController c) {
        qqCtrl = c;
        Log.i("QqSniffer", "🎶 绑定到 QQ 音乐 Session");
        dumpCapabilities(c);          // ← 调试输出（只打印一次就够）
        sendToken();                        // ★ 把 Token 提供给主界面

        dumpMeta(c.getMetadata());
        dumpState(c.getPlaybackState());

        qqCtrl.registerCallback(new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata meta) {
                dumpMeta(meta);
                sendToken();                // 如果想每次切歌都刷新令牌（可选）
            }
            @Override public void onPlaybackStateChanged(PlaybackState st) {
                dumpState(st);
            }
        });
    }


    /* 把拿到的信息写到 Logcat 观察 */
    private void dumpMeta(MediaMetadata meta) {
        if (meta == null) return;
        Log.i("QqSniffer",
                "Meta → Title="   + meta.getString(MediaMetadata.METADATA_KEY_TITLE)  +
                        " | Artist="      + meta.getString(MediaMetadata.METADATA_KEY_ARTIST) +
                        " | Duration(ms)=" + meta.getLong(MediaMetadata.METADATA_KEY_DURATION));

        /* 仅打印 PLAY_MODE 的 long 值 */
        @SuppressLint("WrongConstant")
        long rawMode = meta.getLong("ucar.media.metadata.PLAY_MODE");
        Log.i("QqSniffer", "ucar.media.metadata.PLAY_MODE = " + rawMode);
        // 打印 MEDIA_ID
        Log.i("QqSniffer", "android.media.metadata.MEDIA_ID = "
                + meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));

    }

    private void dumpState(PlaybackState st) {
        if (st == null) return;
        String s = st.getState() == PlaybackState.STATE_PLAYING ? "PLAYING"
                : st.getState() == PlaybackState.STATE_PAUSED  ? "PAUSED"
                : String.valueOf(st.getState());
        Log.i("QqSniffer",
                "State → " + s +
                        " | pos=" + st.getPosition() +
                        " | actions=" + st.getActions());
    }

    private void sendToken() {
        if (qqCtrl == null) return;

        // ① 先拿到平台 Token（android.media.session.MediaSession.Token）
        Object platformToken = qqCtrl.getSessionToken();

        // ② 转成 support-compat Token
        MediaSessionCompat.Token compatToken =
                MediaSessionCompat.Token.fromToken(platformToken);

        // ③ 通过本地广播传给 MainActivity
        Intent i = new Intent("com.example.ACTION_QQ_CONTROLLER");
        i.putExtra("binder", compatToken);        // Parcelable
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }


    /**
     * 调试-打印 QQ 音乐（或任何播放器）当前 MediaSession 能力
     * 传入 **平台类** android.media.session.MediaController
     */
    private void dumpCapabilities(MediaController ctrl) {

        PlaybackState st = ctrl.getPlaybackState();
        long acts = (st != null) ? st.getActions() : 0L;

        Log.i("QqSniffer", "=== Playback Actions ===");
        if ((acts & PlaybackState.ACTION_PLAY)            != 0) Log.i("QqSniffer","  • PLAY");
        if ((acts & PlaybackState.ACTION_PAUSE)           != 0) Log.i("QqSniffer","  • PAUSE");
        if ((acts & PlaybackState.ACTION_PLAY_PAUSE)      != 0) Log.i("QqSniffer","  • PLAY_PAUSE");
        if ((acts & PlaybackState.ACTION_SKIP_TO_NEXT)    != 0) Log.i("QqSniffer","  • NEXT");
        if ((acts & PlaybackState.ACTION_SKIP_TO_PREVIOUS)!= 0) Log.i("QqSniffer","  • PREVIOUS");
        if ((acts & PlaybackState.ACTION_SEEK_TO)         != 0) Log.i("QqSniffer","  • SEEK_TO");
        if ((acts & PlaybackState.ACTION_FAST_FORWARD)    != 0) Log.i("QqSniffer","  • FAST_FORWARD");
        if ((acts & PlaybackState.ACTION_REWIND)          != 0) Log.i("QqSniffer","  • REWIND");
        if ((acts & PlaybackState.ACTION_STOP)            != 0) Log.i("QqSniffer","  • STOP");
        if ((acts & PlaybackState.ACTION_SET_RATING)      != 0) Log.i("QqSniffer","  • SET_RATING");
        if ((acts & PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)!=0)Log.i("QqSniffer","  • PLAY_FROM_ID");

        Log.i("QqSniffer", "=== Metadata Keys ===");
        MediaMetadata mm = ctrl.getMetadata();
        if (mm != null) {
            for (String k : mm.keySet()) {
                String type = "unknown";
                if (mm.getString(k) != null)      type = "String";
                else if (mm.getLong(k) != 0)      type = "Long";
                else if (mm.getBitmap(k) != null) type = "Bitmap";
                else if (mm.getText(k) != null)   type = "CharSeq";
                Log.i("QqSniffer", "  • " + k + " (" + type + ")");
            }
        }

        List<MediaSession.QueueItem> q = ctrl.getQueue();
        Log.i("QqSniffer", "Queue size = " + (q == null ? 0 : q.size()));
    }




}
