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

import java.util.List;

@SuppressLint("OverrideAbstract")
public class MusicSessionSniffer extends NotificationListenerService {

    private static final String QQ_PKG  = "com.tencent.qqmusic";
    private static final String NCM_PKG = "com.netease.cloudmusic";

    // 广播：给 App
    private static final String ACTION_QQ_TOKEN  = "com.example.ACTION_QQ_CONTROLLER";
    private static final String ACTION_NCM_TOKEN = "com.example.ACTION_NCM_CONTROLLER";
    // 广播：App 请求我立刻重发 Token
    private static final String REQ_QQ_TOKEN  = "com.example.REQUEST_QQ_TOKEN";
    private static final String REQ_NCM_TOKEN = "com.example.REQUEST_NCM_TOKEN";

    private MediaController qqCtrl, ncmCtrl;

    @Override public void onCreate() {
        super.onCreate();
        Log.i("Sniffer", "🚀 MusicSessionSniffer STARTED (onCreate)");
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(reqTokenRx, new IntentFilter(REQ_QQ_TOKEN));
        lbm.registerReceiver(reqTokenRx, new IntentFilter(REQ_NCM_TOKEN));
    }

    @Override public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reqTokenRx);
        super.onDestroy();
    }

    @Override public void onListenerConnected() {
        Log.i("Sniffer", "🔌 onListenerConnected");
        refreshControllers();
        sendTokenIfAny(QQ_PKG);
        sendTokenIfAny(NCM_PKG);
    }

    @Override public void onListenerDisconnected() {
        Log.w("Sniffer", "🔌 onListenerDisconnected");
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (QQ_PKG.equals(pkg) || NCM_PKG.equals(pkg)) {
            Log.i("Sniffer", "🔔 notif from " + pkg);
            refreshControllers();
            sendTokenIfAny(pkg);
        }
    }

    // App 请求立刻重发 Token
    private final BroadcastReceiver reqTokenRx = new BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            String a = i.getAction();
            Log.i("Sniffer", "📨 request token: " + a);
            refreshControllers();
            if (REQ_QQ_TOKEN.equals(a))  sendTokenIfAny(QQ_PKG);
            if (REQ_NCM_TOKEN.equals(a)) sendTokenIfAny(NCM_PKG);
        }
    };

    private void refreshControllers() {
        MediaSessionManager sm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (sm == null) { Log.w("Sniffer", "MediaSessionManager null"); return; }

        ComponentName me = new ComponentName(this, MusicSessionSniffer.class);
        List<MediaController> list = null;
        try { list = sm.getActiveSessions(me); } catch (SecurityException ignore) {}
        if (list == null || list.isEmpty()) {
            try { list = sm.getActiveSessions(null); } catch (Throwable ignore) {}
        }
        Log.i("Sniffer", "active sessions = " + (list == null ? 0 : list.size()));
        if (list == null) return;

        for (MediaController c : list) {
            String pkg = c.getPackageName();
            if (QQ_PKG.equals(pkg)) {
                if (qqCtrl == null || !qqCtrl.getSessionToken().equals(c.getSessionToken())) {
                    attachFor(pkg, c);
                }
            } else if (NCM_PKG.equals(pkg)) {
                if (ncmCtrl == null || !ncmCtrl.getSessionToken().equals(c.getSessionToken())) {
                    attachFor(pkg, c);
                }
            }
        }
    }

    private void attachFor(String pkg, MediaController c) {
        if (QQ_PKG.equals(pkg)) qqCtrl = c; else ncmCtrl = c;
        Log.i("Sniffer", "🎶 attach to " + pkg + " session");
        dumpCapabilities(c);
        sendTokenIfAny(pkg);

        c.registerCallback(new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata meta)  { dumpMeta(pkg, meta); }
            @Override public void onPlaybackStateChanged(PlaybackState s){ dumpState(pkg, s);   }
        });
        dumpMeta(pkg, c.getMetadata());
        dumpState(pkg, c.getPlaybackState());
    }

    private void sendTokenIfAny(String pkg) {
        MediaController ctrl = QQ_PKG.equals(pkg) ? qqCtrl : ncmCtrl;
        if (ctrl == null) return;
        MediaSessionCompat.Token compat = MediaSessionCompat.Token.fromToken(ctrl.getSessionToken());
        Intent i = new Intent(QQ_PKG.equals(pkg) ? ACTION_QQ_TOKEN : ACTION_NCM_TOKEN);
        i.putExtra("binder", compat);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        Log.i("Sniffer", "📡 sent token: " + pkg);
    }

    /* --- 调试打印，可保留 --- */
    private void dumpMeta(String pkg, MediaMetadata meta) {
        if (meta == null) return;
        Log.i("Sniffer", pkg + " Meta → title=" + meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                + " | artist=" + meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                + " | dur=" + meta.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }
    private void dumpState(String pkg, PlaybackState st) {
        if (st == null) return;
        String s = st.getState() == PlaybackState.STATE_PLAYING ? "PLAYING"
                : st.getState() == PlaybackState.STATE_PAUSED ? "PAUSED" : String.valueOf(st.getState());
        Log.i("Sniffer", pkg + " State → " + s + " | pos=" + st.getPosition());
    }
    private void dumpCapabilities(MediaController ctrl) {
        PlaybackState st = ctrl.getPlaybackState();
        long a = st != null ? st.getActions() : 0;
        Log.i("Sniffer", "actions=" + a + " pkg=" + ctrl.getPackageName());
    }
}
