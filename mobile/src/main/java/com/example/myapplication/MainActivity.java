package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

        // 沉浸式状态栏处理
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        // 注册广播接收器，接收 QQ 控制器 Token
        tokenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                MediaSessionCompat.Token tk = i.getParcelableExtra("binder");
                if (tk == null) return;

                // 注销旧的回调
                if (qqCtrl != null) qqCtrl.unregisterCallback(cb);

                // 创建新的控制器
                qqCtrl = new MediaControllerCompat(MainActivity.this, tk);
                qqCtrl.registerCallback(cb, null);

                // 注册控制器到当前 Activity，便于系统识别当前媒体控制器
                MediaControllerCompat.setMediaController(MainActivity.this, qqCtrl);

                // 主动触发一次元数据更新
                MediaMetadataCompat meta = qqCtrl.getMetadata();
                if (meta != null) cb.onMetadataChanged(meta);
            }
        };
        IntentFilter filter = new IntentFilter("com.example.ACTION_QQ_CONTROLLER");
        LocalBroadcastManager.getInstance(this).registerReceiver(tokenReceiver, filter);

        // 启动 QQ 音乐按钮
        Button btnOpen = findViewById(R.id.btn_open_qqmusic);
        btnOpen.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("qqmusic://"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(MainActivity.this,
                        "未检测到 QQ 音乐，请先安装。",
                        Toast.LENGTH_SHORT).show();
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
        String pkg = getPackageName();
        String flat = new ComponentName(pkg, QqSessionSniffer.class.getName()).flattenToString();
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(flat);
    }

    /** 弹窗提示用户开启通知监听权限 */
    private void promptForNlPermission() {
        new AlertDialog.Builder(this)
                .setTitle("启用通知读取权限")
                .setMessage("请在接下来的页面勾选本应用，否则无法读取 QQ 音乐曲目信息。")
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
