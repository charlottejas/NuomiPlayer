package com.nuomi;

import android.os.Bundle;

import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.os.SystemClock;



import android.content.BroadcastReceiver;

import android.os.Handler;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat;
import android.provider.Settings;
import android.content.ComponentName;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

import androidx.annotation.Nullable;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.content.ActivityNotFoundException;


public class MainActivity extends AppCompatActivity {

    // ========================= 成员变量声明 =========================
    private TextView titleTv;                    // 歌名显示
    private MediaControllerCompat qqCtrl;        // QQ 音乐控制器
    private BroadcastReceiver tokenReceiver;     // 广播接收器：接收 QqSessionSniffer 发送的 Token

    private final Handler progressHandler = new Handler();  // 用于进度更新
    private Runnable progressRunnable;                      // 进度任务

    private Handler tickerHandler = new Handler();          // 播放进度模拟器
    private Runnable tickerRunnable;
    private long currentPositionMs = 0;                     // 当前播放位置（ms）

    // 支持两个来源的广播 action
    private static final String ACTION_QQ  = "com.example.ACTION_QQ_CONTROLLER";
    private static final String ACTION_NCM = "com.example.ACTION_NCM_CONTROLLER";

    // 来源标识
    private static final String SRC_QQ  = "QQ";
    private static final String SRC_NCM = "NCM";

    private String activeSource = SRC_QQ; // 当前捕获来源（默认 QQ）

    private boolean suppressLyricsToggle = false;




    private @Nullable Intent buildLaunchIntent(String pkg) {
        PackageManager pm = getPackageManager();

        // 1) 官方推荐：找该包的 LAUNCHER Activity（最稳）
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        main.setPackage(pkg);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        if (list != null && !list.isEmpty()) {
            ResolveInfo ri = list.get(0);
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return launch;
        }

        // 2) 兜底
        Intent i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        }
        return null;
    }

    private void openMarketOrToast(String pkg, String appName) {
        try {
            Intent market = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + pkg));
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(market);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "未检测到 " + appName + "，请先安装。", Toast.LENGTH_SHORT).show();
        }
    }



    private void refreshOpenButtonLabel(Button btnOpen) {
        btnOpen.setText(SRC_QQ.equals(activeSource) ? R.string.open_qq : R.string.open_ncm);
    }

    // ========================= 生命周期入口 =========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查是否开启了通知使用权，未开启则弹窗引导
        if (!isNlEnabled()) {
            promptForNlPermission();
        }

        // 设置布局
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Button btnOpen = findViewById(R.id.btn_open_qqmusic);

        // 沉浸式状态栏处理
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        // 3) 读取偏好
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean autoLyrics = prefs.getBoolean("autoLyrics", false);
        String savedSrc = prefs.getString("activeSource", SRC_QQ);
        activeSource = SRC_NCM.equals(savedSrc) ? SRC_NCM : SRC_QQ;

        // 4) 初始化两个开关
        // 4.1 歌词模式开关
        // 4.1 歌词模式开关（加了网易云模式的两道安全措施）
        SwitchCompat switchLyrics = findViewById(R.id.switch_lyrics_mode);

// 如果当前是网易云模式，则强制把歌词开关置为关闭
        boolean initialLyrics = autoLyrics && !SRC_NCM.equals(activeSource);
        switchLyrics.setChecked(initialLyrics);

        switchLyrics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressLyricsToggle) return;

            // 安全措施 2：网易云模式下，拦截用户尝试开启，并提示
            if (SRC_NCM.equals(activeSource) && isChecked) {
                suppressLyricsToggle = true;
                switchLyrics.setChecked(false);                 // 立刻回拨
                suppressLyricsToggle = false;
                Toast.makeText(MainActivity.this, "网易云音乐暂不支持歌词模式", Toast.LENGTH_SHORT).show();
                prefs.edit().putBoolean("autoLyrics", false).apply();
                return;
            }

            // 正常路径（仅 QQ 模式允许修改）
            prefs.edit().putBoolean("autoLyrics", isChecked).apply();
            if (isChecked) {
                // 用户开启后立即激活歌词模式（由 MyMusicService 监听本地广播）
                Intent intent = new Intent("com.example.ACTION_TOGGLE_LYRICS_MODE");
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
            }
        });

// 启动时如果偏好是 true 且当前是 QQ 模式，按原逻辑主动开启
        if (autoLyrics && SRC_QQ.equals(activeSource)) {
            Intent intent = new Intent("com.example.ACTION_TOGGLE_LYRICS_MODE");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } else if (autoLyrics && SRC_NCM.equals(activeSource)) {
            // 安全措施 1（启动时也生效）：如果偏好里本来是开，但当前是网易云，强制关并落盘
            prefs.edit().putBoolean("autoLyrics", false).apply();
        }


        // 4.2 新增：网易云模式开关
        SwitchCompat switchNcm = findViewById(R.id.switch_ncm_mode);
        if (switchNcm != null) {
            switchNcm.setChecked(SRC_NCM.equals(activeSource));
            switchNcm.setOnCheckedChangeListener((btn, isChecked) -> {
                activeSource = isChecked ? SRC_NCM : SRC_QQ;
                prefs.edit().putString("activeSource", activeSource).apply();

                refreshOpenButtonLabel(btnOpen);

                if (qqCtrl != null) {
                    try { qqCtrl.unregisterCallback(cb); } catch (Exception ignore) {}
                    qqCtrl = null;
                }

                Toast.makeText(this,
                        isChecked ? "已切换到网易云模式" : "已切换到QQ音乐模式",
                        Toast.LENGTH_SHORT).show();

                // ✅ 切到网易云：强制把歌词开关关掉，并把偏好改为 false
                if (isChecked) {
                    if (switchLyrics.isChecked()) {
                        suppressLyricsToggle = true;
                        switchLyrics.setChecked(false);
                        suppressLyricsToggle = false;
                    }
                    prefs.edit().putBoolean("autoLyrics", false).apply();
                }

                // 请求对应 Sniffer 立刻重发 Token（这样无需等播放/切歌）
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent(isChecked ? "com.example.REQUEST_NCM_TOKEN"
                                : "com.example.REQUEST_QQ_TOKEN"));


            });
        }


        // 5) 注册广播接收器：同时监听 QQ / 网易云 Token，仅采纳当前来源
        tokenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String action = i.getAction();
                if (SRC_QQ.equals(activeSource) && !ACTION_QQ.equals(action)) return;
                if (SRC_NCM.equals(activeSource) && !ACTION_NCM.equals(action)) return;

                MediaSessionCompat.Token tk = i.getParcelableExtra("binder");
                if (tk == null) return;

                if (qqCtrl != null) qqCtrl.unregisterCallback(cb);
                try {
                    qqCtrl = new MediaControllerCompat(MainActivity.this, tk);
                    qqCtrl.registerCallback(cb, null);
                    MediaControllerCompat.setMediaController(MainActivity.this, qqCtrl);

                    MediaMetadataCompat meta = qqCtrl.getMetadata();
                    if (meta != null) cb.onMetadataChanged(meta);
                } catch (Exception e) {
                    Log.e("QqSniffer", "set controller failed", e);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_QQ);   // "com.example.ACTION_QQ_CONTROLLER"
        filter.addAction(ACTION_NCM);  // "com.example.ACTION_NCM_CONTROLLER"
        LocalBroadcastManager.getInstance(this).registerReceiver(tokenReceiver, filter);

        // 6) 打开 App 按钮：根据当前来源启动 QQ 或 网易云

        refreshOpenButtonLabel(btnOpen);
        btnOpen.setOnClickListener(v -> {
            if (SRC_QQ.equals(activeSource)) {
                // 先试深链（你原逻辑），失败再兜底
                try {
                    Intent deep = new Intent(Intent.ACTION_VIEW, Uri.parse("qqmusic://"));
                    deep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(deep);
                } catch (ActivityNotFoundException e) {
                    Intent launch = buildLaunchIntent("com.tencent.qqmusic");
                    if (launch != null) startActivity(launch);
                    else openMarketOrToast("com.tencent.qqmusic", "QQ 音乐");
                }
            } else {
                Intent launch = buildLaunchIntent("com.netease.cloudmusic");
                if (launch != null) startActivity(launch);
                else openMarketOrToast("com.netease.cloudmusic", "网易云音乐");
            }
        });






    }

    @Override
    protected void onDestroy() {
        if (qqCtrl != null) qqCtrl.unregisterCallback(cb);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tokenReceiver);
        progressHandler.removeCallbacksAndMessages(null);  // 停止进度更新
        super.onDestroy();
    }

    // ========================= 权限检测相关 =========================

    /** 判断通知使用权是否开启 */
    private boolean isNlEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        String flat = new ComponentName(getPackageName(),
                MusicSessionSniffer.class.getName()).flattenToString();
        return enabled.contains(flat);
    }



    /** 弹窗提示用户开启通知监听权限 */
    private void promptForNlPermission() {
        new AlertDialog.Builder(this)
                .setTitle("启用通知读取权限")
                .setMessage("请在接下来的页面勾选本应用，否则无法读取音乐曲目信息。")
                .setPositiveButton("去授权", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(i);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ========================= 控制器回调 =========================

    /** QQ 控制器的元数据与播放状态监听回调 */
    private final MediaControllerCompat.Callback cb = new MediaControllerCompat.Callback() {

        @Override
        public void onMetadataChanged(MediaMetadataCompat meta) {
            if (meta != null) {
                String title = meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                Log.i("QqSniffer", "歌曲标题更新为：" + title);

                // 更新控制面板（歌名、歌手、封面、总时长）
                Fragment fragment = getSupportFragmentManager()
                        .findFragmentById(R.id.playbackControlsFragment);
                if (fragment instanceof PlaybackControlsFragment) {
                    ((PlaybackControlsFragment) fragment).updateTitle(title);
                }

                Bitmap albumArt = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (albumArt != null) {
                    AlbumCoverFragment frag2 = (AlbumCoverFragment)
                            getSupportFragmentManager().findFragmentById(R.id.playerAlbumCoverFragment);
                    if (frag2 != null) {
                        frag2.updateCover(albumArt);
                    } else {
                        Log.w("QqSniffer", "封面Fragment未初始化");
                    }
                } else {
                    Log.w("QqSniffer", "未获取到封面图");
                }

                String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                PlaybackControlsFragment frag1 = (PlaybackControlsFragment)
                        getSupportFragmentManager().findFragmentById(R.id.playbackControlsFragment);
                if (frag1 != null) {
                    frag1.updateTitle(title);
                    frag1.updateArtist(artist);
                }

                long durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
                PlaybackControlsFragment frag = (PlaybackControlsFragment)
                        getSupportFragmentManager().findFragmentById(R.id.playbackControlsFragment);
                if (frag != null) {
                    frag.updateTotalTime(durationMs);
                }


            }
            PlaybackStateCompat state = qqCtrl.getPlaybackState();
            if (state != null) {
                onPlaybackStateChanged(state);
            }

        }

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            long position = state.getPosition();
            Log.i("QqSniffer", "State → " + state.getState() + " | position = " + position);

            PlaybackControlsFragment frag = (PlaybackControlsFragment)
                    getSupportFragmentManager().findFragmentById(R.id.playbackControlsFragment);
            if (frag != null) {
                frag.updateProgressTime(position);
                frag.updatePlayPauseButton(state.getState());
            }

            // 开始/停止进度模拟器
            if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                startProgressTicker(position);
            } else {
                stopProgressTicker();
            }
        }
    };


    // ========================= 播放进度模拟器 =========================

    /** 启动进度模拟器：每秒将 position +1s */
    private void startProgressTicker(long startPos) {
        currentPositionMs = startPos;
        stopProgressTicker();  // 防止重复任务

        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                currentPositionMs += 1000;

                PlaybackControlsFragment frag = (PlaybackControlsFragment)
                        getSupportFragmentManager().findFragmentById(R.id.playbackControlsFragment);

                if (frag != null) {
                    frag.updateProgressTime(currentPositionMs);
                }

                tickerHandler.postDelayed(this, 1000);
            }
        };
        tickerHandler.postDelayed(tickerRunnable, 1000);
    }

    /** 停止进度模拟器 */
    private void stopProgressTicker() {
        tickerHandler.removeCallbacksAndMessages(null);
    }
}
