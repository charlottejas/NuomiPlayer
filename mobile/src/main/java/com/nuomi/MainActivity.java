package com.nuomi;

import android.Manifest;
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
import android.support.v4.media.MediaMetadataCompat;



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

    private static final String ACTION_CONTROLLER = "com.nuomi.ACTION_CONTROLLER";

    // 来源标识
    private static final String SRC_QQ  = "QQ";
    private static final String SRC_NCM = "NCM";

    private String activeSource = SRC_QQ; // 当前捕获来源（默认 QQ）

    private boolean suppressLyricsToggle = false;

    private static final String ACTION_SELECTION_CHANGED = "com.nuomi.ACTION_SELECTION_CHANGED";


    private BroadcastReceiver selectionChangedRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Button btnOpen = findViewById(R.id.btn_open_app);
            refreshOpenButtonLabel(btnOpen);

            // ↓↓↓ 新增：会话变更时同步歌词开关
            SwitchCompat sw = findViewById(R.id.switch_lyrics_mode);
            boolean autoLyrics = getSharedPreferences("settings", MODE_PRIVATE)
                    .getBoolean("autoLyrics", false);
            String pkg = getSharedPreferences("session_pref", MODE_PRIVATE)
                    .getString("last_pkg", null);
            boolean isQQ = "com.tencent.qqmusic".equals(pkg);
            boolean isNCM = "com.netease.cloudmusic".equals(pkg);

            suppressLyricsToggle = true;
            sw.setChecked(autoLyrics && (isQQ || isNCM));  // QQ或网易云时根据设置显示；其他时自动回拨为关
            suppressLyricsToggle = false;
        }
    };

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
        SharedPreferences sp = getSharedPreferences("session_pref", MODE_PRIVATE);
        String label = sp.getString("last_label", null);
        String pkg   = sp.getString("last_pkg", null);

        if (label != null && pkg != null) {
            btnOpen.setText("打开 " + label);
        } else {
            btnOpen.setText("打开 App");
        }
    }


    // ========================= 生命周期入口 =========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查是否开启了通知使用权，未开启则弹窗引导
        if (!isNlEnabled()) {
            promptForNlPermission();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                promptForPostNotificationsPermission();
            }
        }

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(selectionChangedRx, new IntentFilter(ACTION_SELECTION_CHANGED));


        // 设置布局
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        // ✅ 在这里插入首次使用说明弹窗
        SharedPreferences sp1 = getSharedPreferences("settings", MODE_PRIVATE);
        boolean shown = sp1.getBoolean("guideShown", false);
        if (!shown) {
            new AlertDialog.Builder(this)
                    .setTitle("使用说明")
                    .setMessage("📱 初次使用：\n\n"
                            + "1. 在接下来的页面授权通知权限，需要从通知获得歌曲信息\n\n"
                            + "2. 打开你要使用的音乐 App，让它在后台播放，然后点击“选择播放器”按钮\n\n"
                            + "3. 点击“刷新”按钮，正在播放的 App 会显示名称和图标，点击选中\n"
                            + "   （只有第一次录入新 App 时需要，以后切换时可直接点击切换）\n\n"
                            + "4. 这时你能看到手机端播放器展示当前歌曲信息，表示已成功 🎶\n\n"
                            + "🚗 Android Auto：\n\n"
                            + "1. 在手机 Android Auto 中开启开发者模式，并允许未知来源应用\n\n"
                            + "2. 例如使用 QQ 音乐：手机连接车机 Android Auto → 确保糯米播放器在后台运行 → 打开 QQ 音乐播放\n\n"
                            + "3. 车机端糯米播放器会自动显示歌曲。如果显示“没有任何内容”，请在手机端点击暂停再播放等待1~2 秒")
                    .setPositiveButton("我知道了", (d, w) -> {
                        sp1.edit().putBoolean("guideShown", true).apply();
                        d.dismiss();
                    })
                    .setCancelable(false)

                    .setPositiveButton("我知道了", (d, w) -> {
                        sp1.edit().putBoolean("guideShown", true).apply(); // 标记已展示
                        d.dismiss();
                    })
                    .setCancelable(false)
                    .show();
        }

        Button pickBtn = findViewById(R.id.btn_pick_session);
        pickBtn.setOnClickListener(v -> {
            // 先检查是否已授予通知监听权限
            if (!com.nuomi.NotifAccessHelper.isEnabled(this)) {
                Toast.makeText(this, "请先开启“通知使用权”，再返回此页", Toast.LENGTH_LONG).show();
                com.nuomi.NotifAccessHelper.openSettings(this);
                return;
            }
            // 打开底部弹窗
            new com.nuomi.SessionPickerSheet()
                    .show(getSupportFragmentManager(), "session_picker");
        });


        Button btnOpen = findViewById(R.id.btn_open_app);

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
        // 4.1 歌词模式开关（仅 QQ 音乐允许，其他 App 一律禁用）
        SwitchCompat switchLyrics = findViewById(R.id.switch_lyrics_mode);

        // 读取用户在 SessionPicker 里选择的 App
        SharedPreferences selSp = getSharedPreferences("session_pref", MODE_PRIVATE);
        String chosenPkg = selSp.getString("last_pkg", null);
        boolean isQQSelected = "com.tencent.qqmusic".equals(chosenPkg);
        boolean isNCMSelected = "com.netease.cloudmusic".equals(chosenPkg);

        // 只有当选择的是 QQ 或网易云且偏好为 true 才默认勾选
        switchLyrics.setChecked(autoLyrics && (isQQSelected || isNCMSelected));

        switchLyrics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressLyricsToggle) return;

            // 实时确认当前所选 App（避免用户刚切换了选择）
            SharedPreferences curSel = getSharedPreferences("session_pref", MODE_PRIVATE);
            String currentPkg = curSel.getString("last_pkg", null);
            boolean isQQ = "com.tencent.qqmusic".equals(currentPkg);
            boolean isNCM = "com.netease.cloudmusic".equals(currentPkg);

            // 非 QQ/网易云音乐：禁止开启歌词模式并回拨
            if (!isQQ && !isNCM && isChecked) {
                suppressLyricsToggle = true;
                switchLyrics.setChecked(false);   // 立刻回拨
                suppressLyricsToggle = false;
                Toast.makeText(MainActivity.this, "当前选择的 App 不支持歌词模式（仅 QQ 音乐和网易云音乐）", Toast.LENGTH_SHORT).show();
                prefs.edit().putBoolean("autoLyrics", false).apply();
                return;
            }

            // QQ 音乐或网易云音乐：正常落盘与通知
            prefs.edit().putBoolean("autoLyrics", isChecked).apply();
            if (isChecked) {
                // 用户开启后立即激活歌词模式（由 MyMusicService 监听本地广播）
                Intent intent = new Intent("com.nuomi.ACTION_TOGGLE_LYRICS_MODE");
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
            }
        });




        // 5) 注册广播接收器：仅采纳“当前选中的 App”
        tokenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                // 只处理通用 Action
                if (!ACTION_CONTROLLER.equals(i.getAction())) return;

                // 广播里携带的来源包名（由 Sniffer 填入）
                String sourcePkg = i.getStringExtra("pkg");
                if (sourcePkg == null) return;

                // 读取当前用户选中的包名
                SharedPreferences sp = getSharedPreferences("session_pref", MODE_PRIVATE);
                String chosenPkg = sp.getString("last_pkg", null);
                if (chosenPkg == null || !chosenPkg.equals(sourcePkg)) {
                    // 不是当前选中的来源，忽略
                    return;
                }

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
                    Log.e("QqSniffer", "设置控制器失败", e);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_CONTROLLER);
        LocalBroadcastManager.getInstance(this).registerReceiver(tokenReceiver, filter);


        // 6) 打开 App 按钮

        refreshOpenButtonLabel(btnOpen);
        btnOpen.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("session_pref", MODE_PRIVATE);
            String pkg = sp.getString("last_pkg", null);
            String label = sp.getString("last_label", "所选应用");

            if (pkg == null) {
                Toast.makeText(this, "请先选择一个 App", Toast.LENGTH_SHORT).show();
                return;
            }

            // 尝试启动
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            } else {
                Toast.makeText(this, "未找到 " + label, Toast.LENGTH_SHORT).show();
            }
        });







    }

    @Override
    protected void onDestroy() {
        if (qqCtrl != null) qqCtrl.unregisterCallback(cb);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tokenReceiver);
        progressHandler.removeCallbacksAndMessages(null);  // 停止进度更新


        LocalBroadcastManager.getInstance(this).unregisterReceiver(selectionChangedRx);

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
                .setMessage("请在接下来的页面中勾选本应用，否则将无法获取正在播放的歌曲信息。")
                .setPositiveButton("去授权", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(i);
                })
                .setNegativeButton("取消", null)
                .show();
    }


    /** 弹窗提示用户开启 Android 13+ 通知权限 */
    private void promptForPostNotificationsPermission() {
        new AlertDialog.Builder(this)
                .setTitle("允许通知权限")
                .setMessage("为了保证糯米播放器在后台正常运行，本应用需要在状态栏权限"
                        + "🎵。\n\n"
                        + "在 Android 13 及以上系统，如果不允许通知权限，应用可能会被系统限制后台运行。")
                .setPositiveButton("去允许", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(
                                new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                                1001  // 自定义请求码
                        );
                    }
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

                // 封面位图优先：ALBUM_ART → DISPLAY_ICON → ART
                Bitmap cover = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                if (cover == null) cover = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON);
                if (cover == null) cover = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);

                if (cover != null) {
                    AlbumCoverFragment frag2 = (AlbumCoverFragment)
                            getSupportFragmentManager().findFragmentById(R.id.playerAlbumCoverFragment);
                    if (frag2 != null) {
                        frag2.updateCover(cover);
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshOpenButtonLabel(findViewById(R.id.btn_open_app));
    }



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
