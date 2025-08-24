package com.nuomi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.SharedPreferences;


import androidx.annotation.Nullable;


import android.content.Context;



public class MusicSessionSniffer extends NotificationListenerService {

    private static final String ACTION_CONTROLLER = "com.example.ACTION_CONTROLLER";
    private static final String ACTION_REQ_TOKEN  = "com.example.REQUEST_TOKEN";

    private MediaController selectedCtrl;   // 当前选中包名对应的 controller
    private String selectedPkg;             // 当前选中的包名（从 SP 读取）

    @Override public void onCreate() {
        super.onCreate();
        Log.i("Sniffer", "🚀 MusicSessionSniffer 启动");
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(reqTokenRx, new IntentFilter(ACTION_REQ_TOKEN));
    }

    @Override public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reqTokenRx);
        Log.i("Sniffer", "🛑 MusicSessionSniffer 已销毁");
        super.onDestroy();
    }

    @Override public void onListenerConnected() {
        Log.i("Sniffer", "🔌 已连接到通知监听服务");
        refreshSelectedController();
        sendTokenIfAny();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        String want = readChosenPkg();
        if (want == null) return;
        if (want.equals(pkg)) {
            Log.i("Sniffer", "🔔 收到来自已选中应用的通知: " + pkg);
            refreshSelectedController();
            sendTokenIfAny();
        }
    }

    private final BroadcastReceiver reqTokenRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Log.i("Sniffer", "📨 收到立即重发 Token 请求");
            refreshSelectedController();
            sendTokenIfAny();
        }
    };

    @Nullable
    private String readChosenPkg() {
        SharedPreferences sp = getSharedPreferences("session_pref", MODE_PRIVATE);
        return sp.getString("last_pkg", null);
    }

    private void refreshSelectedController() {
        selectedPkg = readChosenPkg();
        if (selectedPkg == null) {
            selectedCtrl = null;
            Log.w("Sniffer", "⚠️ 当前没有用户选中的应用");
            return;
        }

        MediaSessionManager sm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (sm == null) { Log.w("Sniffer", "❌ 无法获取 MediaSessionManager"); return; }

        ComponentName me = new ComponentName(this, MusicSessionSniffer.class);
        java.util.List<MediaController> list = null;
        try { list = sm.getActiveSessions(me); } catch (SecurityException ignore) {}
        if (list == null || list.isEmpty()) {
            try { list = sm.getActiveSessions(null); } catch (Throwable ignore) {}
        }
        Log.i("Sniffer", "📊 活跃会话数量 = " + (list == null ? 0 : list.size()));
        if (list == null) { selectedCtrl = null; return; }

        MediaController found = null;
        for (MediaController c : list) {
            if (selectedPkg.equals(c.getPackageName())) {
                found = c;
                break;
            }
        }

        if (found == null) {
            Log.w("Sniffer", "⚠️ 当前选中的应用没有活跃会话: " + selectedPkg);
            selectedCtrl = null;
            return;
        }

        if (selectedCtrl == null ||
                !selectedCtrl.getSessionToken().equals(found.getSessionToken())) {
            selectedCtrl = found;
            Log.i("Sniffer", "🎶 绑定到新会话: " + selectedPkg);
            dumpCapabilities(selectedCtrl);

            selectedCtrl.registerCallback(new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata meta)  { dumpMeta(selectedPkg, meta); }
                @Override public void onPlaybackStateChanged(PlaybackState s){ dumpState(selectedPkg, s);   }
            });

            dumpMeta(selectedPkg, selectedCtrl.getMetadata());
            dumpState(selectedPkg, selectedCtrl.getPlaybackState());
        }
    }

    private void sendTokenIfAny() {
        if (selectedCtrl == null || selectedPkg == null) return;
        MediaSessionCompat.Token compat = MediaSessionCompat.Token.fromToken(selectedCtrl.getSessionToken());
        Intent i = new Intent(ACTION_CONTROLLER);
        i.putExtra("pkg", selectedPkg);
        i.putExtra("binder", compat);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        Log.i("Sniffer", "📡 已发送 Token 给应用: " + selectedPkg);
    }

    /* --- 调试打印（中文版本） --- */
    private void dumpMeta(String pkg, MediaMetadata meta) {
        if (meta == null) return;
        Log.i("Sniffer", pkg + " 元数据 → 歌曲: " + meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                + " | 歌手: " + meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                + " | 时长: " + meta.getLong(MediaMetadata.METADATA_KEY_DURATION) + "ms");
    }
    private void dumpState(String pkg, PlaybackState st) {
        if (st == null) return;
        String s = st.getState() == PlaybackState.STATE_PLAYING ? "播放中"
                : st.getState() == PlaybackState.STATE_PAUSED ? "已暂停" : String.valueOf(st.getState());
        Log.i("Sniffer", pkg + " 播放状态 → " + s + " | 位置=" + st.getPosition() + "ms");
    }
    private void dumpCapabilities(MediaController ctrl) {
        PlaybackState st = ctrl.getPlaybackState();
        long a = st != null ? st.getActions() : 0;
        Log.i("Sniffer", "可用操作=" + a + " | 包名=" + ctrl.getPackageName());
    }
}
