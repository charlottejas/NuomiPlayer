package com.nuomi.shared;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.annotation.NonNull;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.MediaBrowserServiceCompat;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyMusicService extends MediaBrowserServiceCompat {

    private MediaSessionCompat mSession;                       // 本地 MediaSession
    private MediaControllerCompat remoteCtrl;                  // 指向外部播放器的控制器（QQ 或 NCM）
    private final MediaControllerCompat.Callback remoteCb = new RemoteCallback(); // 监听状态变化

    private static final String CUSTOM_ACTION_SHOW_LYRICS = "com.nuomi.SHOW_LYRICS";
    private static final String CUSTOM_ACTION_REPEAT_MODE = "com.nuomi.REPEAT_MODE";

    private static final String ACTION_CONTROLLER = "com.nuomi.ACTION_CONTROLLER";

    private static final String ACTION_TOGGLE_LYRICS_MODE = "com.nuomi.ACTION_TOGGLE_LYRICS_MODE";

    private List<Pair<Long, String>> parsedLyrics = new ArrayList<>();
    private boolean isLyricsMode = false; // 仅 QQ 模式可用；NCM 模式强制关闭
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int lastPlayMode = 0; // QQ 的播放模式缓存

    // ===== 仅在“QQ 歌词模式”下使用的缓存/本地时钟 =====
    private MediaMetadataCompat lastRemoteMeta = null;
    private PlaybackStateCompat lastRemoteState = null;

    private boolean suppressRemoteState = false; // 拖动后的保护期：忽略短期旧状态回写
    private final Runnable clearSuppression = () -> suppressRemoteState = false;

    private long basePosMs = 0L;
    private long baseUpdateElapsed = 0L;
    private float baseSpeed = 0f;
    private int baseState = PlaybackStateCompat.STATE_NONE;
    private long durationMs = 0L;

    private String lastLyricsRaw = null;

    // 三种模式：QQ音乐、网易云音乐、其他
    private static final String MODE_QQ = "QQ";
    private static final String MODE_NCM = "NCM";
    private static final String MODE_OTHER = "OTHER";
    private String musicMode = MODE_QQ;  // 当前模式，默认QQ音乐

    // 新增：防止重复激活
    private boolean sessionActivated = false;

    // 放在成员里
    private static final String TAG = "Mirror";

    private void updateSessionActive(String reason) {
        // QQ 或 网易云 + 歌词模式 才激活
        boolean should = ((MODE_QQ.equals(musicMode) || MODE_NCM.equals(musicMode)) && isLyricsMode);
        if (mSession.isActive() != should) {
            mSession.setActive(should);
            Log.i(TAG, "setActive=" + should + " reason=" + reason);
        }
    }

    private PlaybackStateCompat buildMinimalState(int state, long pos, float speed) {
        long ACTIONS = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_PLAY_PAUSE;

        return new PlaybackStateCompat.Builder()
                .setState(state, pos, speed, SystemClock.elapsedRealtime())
                .setActions(ACTIONS)
                .build();
    }



    // 以“基准位置+基准时间+速度”推算当前 position（只在 QQ 歌词模式用）
    private long clockPosition() {
        if (baseState == PlaybackStateCompat.STATE_PLAYING) {
            long elapsed = SystemClock.elapsedRealtime() - baseUpdateElapsed;
            long pos = basePosMs + (long) (elapsed * baseSpeed);
            return Math.max(0L, durationMs > 0 ? Math.min(pos, durationMs) : pos);
        } else {
            return basePosMs;
        }
    }

    // 每秒刷新（QQ 和网易云歌词模式）
    private final Runnable lyricsUpdater = new Runnable() {
        @Override
        public void run() {
            if ((MODE_QQ.equals(musicMode) || MODE_NCM.equals(musicMode)) && isLyricsMode && remoteCtrl != null) {
                applyLyricsOverlay(lastRemoteMeta);
                handler.postDelayed(this, 1000);
            }
        }
    };

    // "自动开启歌词模式"广播，QQ 和网易云模式生效
    private final BroadcastReceiver autoLyricsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("Mirror", "📨 收到自动开启歌词模式请求");
            if (MODE_OTHER.equals(musicMode)) { // 其他模式不支持歌词
                Log.i("Mirror", "ℹ️ 当前为【其他模式】，忽略开启歌词模式请求");
                return;
            }
            if (!isLyricsMode) {
                isLyricsMode = true;
                Log.i("Mirror", "🎵 已开启歌词模式（" + musicMode + "）");

                if (lastRemoteState != null) {
                    basePosMs = lastRemoteState.getPosition();
                    baseSpeed = lastRemoteState.getPlaybackSpeed();
                    baseState = lastRemoteState.getState();
                    baseUpdateElapsed = SystemClock.elapsedRealtime();
                }

                handler.post(lyricsUpdater);
                if (remoteCtrl != null) {
                    applyLyricsOverlay(lastRemoteMeta);
                    mirror(remoteCtrl.getMetadata(), remoteCtrl.getPlaybackState());
                }
            }
        }
    };


    // =========================================================
    // 🔁 外部播放器回调：将元数据 / 播放状态同步给本地 Session
    // =========================================================
    private class RemoteCallback extends MediaControllerCompat.Callback {
        @Override public void onMetadataChanged(MediaMetadataCompat m) {
            lastRemoteMeta = m; // 缓存给 QQ 歌词模式
            mirror(m, null);
        }

        @Override public void onPlaybackStateChanged(PlaybackStateCompat s) {
            lastRemoteState = s; // 缓存给 QQ 歌词模式
            mirror(null, s);
        }
    }

    // =========================================================
    // 🪞 同步信息到本地 Session（根据当前来源分支）
    // =========================================================
    private void mirror(MediaMetadataCompat meta, PlaybackStateCompat st) {

        // --- 1. 同步元数据 ---
        if (meta != null) {
            if ((MODE_QQ.equals(musicMode) || MODE_NCM.equals(musicMode)) && isLyricsMode) {
                // QQ/网易云 歌词模式：覆盖为"当前句/下一句"
                applyLyricsOverlay(meta);
            } else {
                // QQ/网易云 非歌词模式 或 其他模式：原样映射标准字段
                MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();

                String title = meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                String artist = meta.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);

                long duration = meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);

                // QQ 模式保留 playMode；网易云/其他 没有该私有键，忽略即可
                if (MODE_QQ.equals(musicMode)) {
                    long playMode = meta.getLong("ucar.media.metadata.PLAY_MODE");
                    lastPlayMode = (int) playMode;
                }

                builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
                builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);

                if (duration > 0)
                    builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

                // ✅ 仅在“非 QQ 模式”启用封面位图兜底；QQ 模式保持你原来的只取 ALBUM_ART 行为
                Bitmap art = null;
                if (!MODE_QQ.equals(musicMode)) {
                    // 非 QQ：位图优先顺序 ALBUM_ART → DISPLAY_ICON → ART
                    art = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                    if (art == null) art = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON);
                    if (art == null) art = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
                } else {
                    // QQ：保持原逻辑（只取 ALBUM_ART）
                    art = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                }

                if (art != null) {
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
                }

                mSession.setMetadata(builder.build());
            }
        }

        // --- 2. 同步播放状态 ---
        if (st != null) {

            // QQ/网易云 歌词模式专用分支（其他模式强制关闭歌词，不走此分支）
            if ((MODE_QQ.equals(musicMode) || MODE_NCM.equals(musicMode)) && isLyricsMode) {
                if (!suppressRemoteState) {
                    basePosMs = st.getPosition();
                    baseSpeed = st.getPlaybackSpeed();
                    baseState = st.getState();
                    baseUpdateElapsed = SystemClock.elapsedRealtime();
                }

                int code = st.getState();
                if (code == PlaybackStateCompat.STATE_NONE || code == PlaybackStateCompat.STATE_STOPPED) {
                    code = PlaybackStateCompat.STATE_PAUSED; // 避免 AA 跳回浏览页
                }

                PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                        .setState(code, clockPosition(), (baseSpeed == 0f ? 1.0f : baseSpeed))
                        .setActions(
                                PlaybackStateCompat.ACTION_PLAY |
                                        PlaybackStateCompat.ACTION_PAUSE |
                                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                        PlaybackStateCompat.ACTION_SEEK_TO |
                                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                        );

                // 自定义按钮（QQ 和网易云模式展示）
                int lyricsIconRes = isLyricsMode ? R.drawable.ic_lyrics_24dp : R.drawable.ic_lyrics_outline_24dp;
                builder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_ACTION_SHOW_LYRICS, "歌词", lyricsIconRes).build());

                // 循环按钮仅 QQ 模式显示
                if (MODE_QQ.equals(musicMode)) {
                    int repeatIconRes;
                    switch (lastPlayMode) {
                        case 1: repeatIconRes = R.drawable.ic_repeat_one_24dp; break;
                        case 0: repeatIconRes = R.drawable.ic_shuffle_24dp;    break;
                        case 2:
                        default: repeatIconRes = R.drawable.ic_repeat_24dp;    break;
                    }
                    builder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                            CUSTOM_ACTION_REPEAT_MODE, "循环", repeatIconRes).build());
                }

                mSession.setPlaybackState(builder.build());
                return;
            }

            // —— QQ 非歌词模式 或 NCM 模式通用分支 ——
            int code = st.getState();
            if (code == PlaybackStateCompat.STATE_NONE ||
                    code == PlaybackStateCompat.STATE_STOPPED) {
                return;
            }

            PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                    .setState(code, st.getPosition(), st.getPlaybackSpeed())
                    .setActions(
                            PlaybackStateCompat.ACTION_PLAY |
                                    PlaybackStateCompat.ACTION_PAUSE |
                                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                    PlaybackStateCompat.ACTION_SEEK_TO |
                                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                    );

            // 仅 QQ 模式下加入自定义按钮；NCM 模式完全关闭“歌词/循环”按钮
            if (MODE_QQ.equals(musicMode) || MODE_NCM.equals(musicMode)) {
                int lyricsIconRes = isLyricsMode ? R.drawable.ic_lyrics_24dp : R.drawable.ic_lyrics_outline_24dp;
                builder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_ACTION_SHOW_LYRICS, "歌词", lyricsIconRes).build());

                // 循环按钮仅 QQ 模式显示
                if (MODE_QQ.equals(musicMode)) {
                    int repeatIconRes = R.drawable.ic_repeat_24dp;
                    if (meta != null) {
                        long playMode = meta.getLong("ucar.media.metadata.PLAY_MODE");
                        switch ((int) playMode) {
                            case 1: repeatIconRes = R.drawable.ic_repeat_one_24dp; break;
                            case 0: repeatIconRes = R.drawable.ic_shuffle_24dp;    break;
                            case 2:
                            default: repeatIconRes = R.drawable.ic_repeat_24dp;    break;
                        }
                    }
                    builder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                            CUSTOM_ACTION_REPEAT_MODE, "循环", repeatIconRes).build());
                }
            }

            mSession.setPlaybackState(builder.build());
        }
    }

    // 在"QQ/网易云 歌词模式"调用：把当前/下一句覆盖到元数据
    private void applyLyricsOverlay(MediaMetadataCompat meta) {
        if (MODE_OTHER.equals(musicMode) || !isLyricsMode || meta == null) return;

        // QQ 模式有 playMode
        if (MODE_QQ.equals(musicMode)) {
            long playMode = meta.getLong("ucar.media.metadata.PLAY_MODE");
            lastPlayMode = (int) playMode;
        }
        long dur = meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        if (dur > 0) durationMs = dur;

        // 获取歌词：QQ 模式从元数据获取，网易云模式使用占位歌词
        String lyricsWhole = null;
        if (MODE_QQ.equals(musicMode)) {
            lyricsWhole = meta.getString("ucar.media.metadata.LYRICS_WHOLE");
        } else if (MODE_NCM.equals(musicMode)) {
            lyricsWhole = getNcmPlaceholderLyrics();
        }
        
        if (lyricsWhole != null && !lyricsWhole.equals(lastLyricsRaw)) {
            lastLyricsRaw = lyricsWhole;
            parseLyrics(lyricsWhole);
        }

        long t = clockPosition();
        String current = "", next = "";
        if (!parsedLyrics.isEmpty()) {
            int lo = 0, hi = parsedLyrics.size() - 1, ans = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (parsedLyrics.get(mid).first <= t) { ans = mid; lo = mid + 1; }
                else hi = mid - 1;
            }
            if (ans >= 0) current = parsedLyrics.get(ans).second;
            if (ans + 1 < parsedLyrics.size()) next = parsedLyrics.get(ans + 1).second;
        }

        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
        b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, current);
        b.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, next);

        Bitmap art = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        if (art != null) b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
        if (durationMs > 0) b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);

        mSession.setMetadata(b.build());

        int code = (baseState == PlaybackStateCompat.STATE_NONE || baseState == PlaybackStateCompat.STATE_STOPPED)
                ? PlaybackStateCompat.STATE_PAUSED : baseState;

        PlaybackStateCompat.Builder ps = new PlaybackStateCompat.Builder()
                .setState(code, clockPosition(), (baseSpeed == 0f ? 1.0f : baseSpeed))
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                );

        int lyricsIconRes = R.drawable.ic_lyrics_24dp;
        ps.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_SHOW_LYRICS, "歌词", lyricsIconRes).build());

        // 循环按钮仅 QQ 模式显示
        if (MODE_QQ.equals(musicMode)) {
            int repeatIconRes;
            switch (lastPlayMode) {
                case 1: repeatIconRes = R.drawable.ic_repeat_one_24dp; break;
                case 0: repeatIconRes = R.drawable.ic_shuffle_24dp;    break;
                case 2:
                default: repeatIconRes = R.drawable.ic_repeat_24dp;    break;
            }
            ps.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_REPEAT_MODE, "循环", repeatIconRes).build());
        }

        mSession.setPlaybackState(ps.build());
    }

    // =========================================================
    // 📡 接收 QQ / NCM 的 Token 并构建 Controller（切源）
    // =========================================================
    private final BroadcastReceiver tokenRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!ACTION_CONTROLLER.equals(i.getAction())) return;

            // 1) 广播来源的包名（Sniffer 填入）
            String sourcePkg = i.getStringExtra("pkg");
            if (sourcePkg == null) {
                Log.w("Mirror", "⚠️ 收到控制广播但缺少 pkg");
                return;
            }

            // 2) 只采纳“当前选中的包名”
            SharedPreferences sp = getSharedPreferences("session_pref", MODE_PRIVATE);
            String chosenPkg = sp.getString("last_pkg", null);
            if (chosenPkg == null || !chosenPkg.equals(sourcePkg)) {
                Log.i("Mirror", "ℹ️ 忽略不同来源广播，当前选择=" + chosenPkg + "，广播来自=" + sourcePkg);
                return;
            }

            // 3) 根据来源包名确定模式：QQ、网易云或其他
            String newMode;
            if ("com.tencent.qqmusic".equals(sourcePkg)) {
                newMode = MODE_QQ;
            } else if ("com.netease.cloudmusic".equals(sourcePkg)) {
                newMode = MODE_NCM;
            } else {
                newMode = MODE_OTHER;
            }
            
            if (!newMode.equals(musicMode)) {
                String oldMode = musicMode;
                musicMode = newMode;
                Log.i("Mirror", "🔄 切换为【" + musicMode + " 模式】，来源=" + sourcePkg);
                
                // 如果切换到"其他"模式，关闭歌词
                if (MODE_OTHER.equals(musicMode)) {
                    if (isLyricsMode) {
                        isLyricsMode = false;
                        handler.removeCallbacks(lyricsUpdater);
                        suppressRemoteState = false;
                        Log.i("Mirror", "🧹 已关闭歌词模式并清理定时任务（进入其他模式）");
                    }
                }
            }

            updateSessionActive("sourceChanged:" + sourcePkg);


            // 4) 取 Token → 绑定 Controller
            MediaSessionCompat.Token tk = i.getParcelableExtra("binder");
            if (tk == null) {
                Log.w("Mirror", "⚠️ 广播中没有 binder Token");
                return;
            }

            try {
                if (remoteCtrl != null) {
                    remoteCtrl.unregisterCallback(remoteCb);
                }
                remoteCtrl = new MediaControllerCompat(MyMusicService.this, tk);
                remoteCtrl.registerCallback(remoteCb);
                Log.i("Mirror", "✅ 已绑定远端控制器，pkg=" + sourcePkg);



                // 5) 同步一次
                mirror(remoteCtrl.getMetadata(), remoteCtrl.getPlaybackState());

                updateSessionActive("autoLyricsOnStartup");

            } catch (Exception e) {
                Log.e("Mirror", "❌ 绑定控制器失败", e);
            }
        }
    };


    private void parseLyrics(String rawLyrics) {
        parsedLyrics.clear();
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2}\\.\\d{2})\\](.*)");
        for (String line : rawLyrics.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                int min = Integer.parseInt(matcher.group(1));
                float sec = Float.parseFloat(matcher.group(2));
                long timeMs = (long) ((min * 60 + sec) * 1000);
                String text = matcher.group(3).trim();
                parsedLyrics.add(new Pair<>(timeMs, text));
            }
        }
    }

    // 网易云音乐歌词占位函数：返回固定的示例歌词
    private String getNcmPlaceholderLyrics() {
        return "[00:00.00]网易云音乐歌词显示功能\n" +
               "[00:03.00]这是一段示例歌词\n" +
               "[00:06.00]🎵 当前播放的是网易云音乐\n" +
               "[00:10.00]歌词功能开发中\n" +
               "[00:14.00]敬请期待\n" +
               "[00:18.00]感谢使用糯米播放器";
    }

    // =========================================================
    // 🚀 启动服务：初始化本地 MediaSession 并设置转发逻辑
    // =========================================================
    @Override
    public void onCreate() {
        super.onCreate();

        mSession = new MediaSessionCompat(this, "MirrorSession");
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
        setSessionToken(mSession.getSessionToken());

        mSession.setPlaybackState(buildMinimalState(
                PlaybackStateCompat.STATE_NONE, 0, 0f));
        updateSessionActive("onCreate");

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
                if (remoteCtrl != null) {
                    remoteCtrl.getTransportControls().seekTo(positionMs);
                }

                if ((MODE_QQ.equals(musicMode) || MODE_NCM.equals(musicMode)) && isLyricsMode) {
                    suppressRemoteState = true;
                    handler.removeCallbacks(clearSuppression);
                    handler.postDelayed(clearSuppression, 1200);

                    long now = SystemClock.elapsedRealtime();
                    PlaybackStateCompat rs = lastRemoteState;
                    baseState = (rs != null) ? rs.getState() : PlaybackStateCompat.STATE_PLAYING;
                    baseSpeed = (rs != null) ? rs.getPlaybackSpeed() : 1.0f;
                    basePosMs = positionMs;
                    baseUpdateElapsed = now;

                    applyLyricsOverlay(lastRemoteMeta);
                } else {
                    PlaybackStateCompat remoteState =
                            (remoteCtrl != null) ? remoteCtrl.getPlaybackState() : null;

                    if (remoteState != null) {
                        mSession.setPlaybackState(remoteState);
                    }
                    updateSessionActive("seekTo");
                }
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                // 其他模式下直接忽略自定义按钮
                if (MODE_OTHER.equals(musicMode)) return;

                if (CUSTOM_ACTION_SHOW_LYRICS.equals(action)) {
                    isLyricsMode = !isLyricsMode;

                    if (isLyricsMode) {
                        if (lastRemoteState != null) {
                            basePosMs = lastRemoteState.getPosition();
                            baseSpeed = lastRemoteState.getPlaybackSpeed();
                            baseState = lastRemoteState.getState();
                            baseUpdateElapsed = SystemClock.elapsedRealtime();
                        }
                        handler.post(lyricsUpdater);
                        applyLyricsOverlay(lastRemoteMeta);
                    } else {
                        handler.removeCallbacks(lyricsUpdater);
                        suppressRemoteState = false;
                    }

                    if (remoteCtrl != null) {
                        mirror(remoteCtrl.getMetadata(), remoteCtrl.getPlaybackState());
                    }
                    updateSessionActive("toggleLyrics=" + isLyricsMode);

                } else if (CUSTOM_ACTION_REPEAT_MODE.equals(action)) {
                    // 仅 QQ 模式发送 QQ 的切换广播
                    Intent intent = new Intent("com.tencent.qqmusic.ACTION_SERVICE_PLAY_MODE_WIDGET.QQMusicPhone");
                    intent.setPackage("com.tencent.qqmusic");
                    sendBroadcast(intent);

                    handler.postDelayed(() -> {
                        if (remoteCtrl != null) {
                            mirror(remoteCtrl.getMetadata(), remoteCtrl.getPlaybackState());
                        }
                    }, 500);
                }
            }
        });

        // 同时注册 QQ / NCM 的 Token 广播
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(tokenRx, new IntentFilter(ACTION_CONTROLLER));



        // 注册“自动歌词模式”广播
        lbm.registerReceiver(autoLyricsReceiver, new IntentFilter(ACTION_TOGGLE_LYRICS_MODE));


        // 自动歌词模式：QQ 和网易云模式可自动开启
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean autoLyrics = prefs.getBoolean("autoLyrics", false);
        Log.i("Mirror", "🎚 autoLyrics 开关状态 = " + autoLyrics);
        if (autoLyrics && (MODE_QQ.equals(musicMode) || MODE_NCM.equals(musicMode)) && !isLyricsMode) {
            isLyricsMode = true;

            if (lastRemoteState != null) {
                basePosMs = lastRemoteState.getPosition();
                baseSpeed = lastRemoteState.getPlaybackSpeed();
                baseState = lastRemoteState.getState();
                baseUpdateElapsed = SystemClock.elapsedRealtime();
            }

            handler.post(lyricsUpdater);
            if (remoteCtrl != null) {
                applyLyricsOverlay(lastRemoteMeta);
                mirror(remoteCtrl.getMetadata(), remoteCtrl.getPlaybackState());
            }
        }
    }

    // =========================================================
    // 🧹 资源释放
    // =========================================================
    @Override
    public void onDestroy() {
        handler.removeCallbacks(lyricsUpdater);
        if (remoteCtrl != null) {
            remoteCtrl.unregisterCallback(remoteCb);
        }
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(tokenRx);
        lbm.unregisterReceiver(autoLyricsReceiver);

        mSession.release();
        super.onDestroy();
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
