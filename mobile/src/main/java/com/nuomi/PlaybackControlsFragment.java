package com.nuomi;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textview.MaterialTextView;

public class PlaybackControlsFragment extends Fragment {

    private MaterialTextView titleTv;
    private MaterialTextView textView;

    private MaterialTextView songCurrentProgress;
    private MaterialTextView songTotalTime;

    private MusicSlider progressSlider;

    private MaterialButton previousButton;
    private FloatingActionButton playPauseButton;
    private MaterialButton nextButton;



    public PlaybackControlsFragment() {
        super(R.layout.fragment_m3_player_playback_controls);
    }


    // ===== 在类里新增：安全调用封装（任意位置，建议放成员方法区） =====
    private void safeWithController(SafeControllerAction action) {
        try {
            MediaControllerCompat c = MediaControllerCompat.getMediaController(requireActivity());
            if (c == null) {
                Toast.makeText(requireContext(), "未发现可控制的播放器", Toast.LENGTH_SHORT).show();
                return;
            }
            // 探测存活
            c.getTransportControls();
            action.run(c);
        } catch (RuntimeException e) {
            String s = String.valueOf(e);
            if (s.contains("DeadObjectException")) {
                Log.e("PlaybackControlsFragment", "Remote session dead → request rebind", e);
                requireContext().sendBroadcast(new Intent("com.nuomi.ACTION_REBIND_ACTIVE_SESSION"));
                Toast.makeText(requireContext(), "播放器断开，正在重新连接…", Toast.LENGTH_SHORT).show();
            } else {
                Log.e("PlaybackControlsFragment", "Controller call failed", e);
            }
        }
    }

    // 自定义一个简单接口
    private interface SafeControllerAction {
        void run(MediaControllerCompat controller);
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        titleTv = view.findViewById(R.id.title);
        titleTv.setText("请先打开音乐App播放任意歌曲");
        textView = view.findViewById(R.id.text);
        songCurrentProgress = view.findViewById(R.id.songCurrentProgress);
        songTotalTime = view.findViewById(R.id.songTotalTime);

        progressSlider = view.findViewById(R.id.progressSlider);

        // 设置进度条初始范围（可改为0到你设定的默认最大值）
        progressSlider.setValueFrom(0);
        progressSlider.setValueTo(1000);
        progressSlider.setValue(0);

        previousButton = view.findViewById(R.id.previousButton);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        nextButton = view.findViewById(R.id.nextButton);

        previousButton.setOnClickListener(v ->
                safeWithController(new SafeControllerAction() {
                    @Override
                    public void run(MediaControllerCompat c) {
                        c.getTransportControls().skipToPrevious();
                    }
                })
        );

        playPauseButton.setOnClickListener(v ->
                safeWithController(new SafeControllerAction() {
                    @Override
                    public void run(MediaControllerCompat c) {
                        PlaybackStateCompat state = c.getPlaybackState();
                        if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                            c.getTransportControls().pause();
                        } else {
                            c.getTransportControls().play();
                        }
                    }
                })
        );

        nextButton.setOnClickListener(v ->
                safeWithController(new SafeControllerAction() {
                    @Override
                    public void run(MediaControllerCompat c) {
                        c.getTransportControls().skipToNext();
                    }
                })
        );

        progressSlider.setListener(new MusicSlider.Listener() {
            @Override public void onProgressChanged(MusicSlider slider, int progress, boolean fromUser) { }

            @Override public void onStartTrackingTouch(MusicSlider slider) { }

            @Override public void onStopTrackingTouch(MusicSlider slider) {
                safeWithController(new SafeControllerAction() {
                    @Override
                    public void run(MediaControllerCompat c) {
                        c.getTransportControls().seekTo(slider.getValue());
                    }
                });
            }
        });


        // 设置监听器：当用户拖动进度条后跳转播放位置
        progressSlider.setListener(new MusicSlider.Listener() {
            @Override
            public void onProgressChanged(MusicSlider slider, int progress, boolean fromUser) {
                // 可选：实时更新文字显示
            }

            @Override
            public void onStartTrackingTouch(MusicSlider slider) {
                // 可选：通知主程序暂停自动刷新
            }

            @Override
            public void onStopTrackingTouch(MusicSlider slider) {
                MediaControllerCompat controller = MediaControllerCompat.getMediaController(requireActivity());
                if (controller != null) {
                    controller.getTransportControls().seekTo(slider.getValue());
                }
            }
        });


    }

    public void updateTitle(String title) {
        if (titleTv != null) {
            titleTv.setText(title);
        }
    }


    public void updateArtist(String artist) {
        if (textView != null && artist != null) {
            textView.setText(artist);
        }
    }

    public void updateProgressTime(long milliseconds) {
        if (songCurrentProgress != null) {
            songCurrentProgress.setText(formatTime(milliseconds));
        }

        if (progressSlider != null && !progressSlider.isTrackingTouch()) {
            progressSlider.setValue((int) milliseconds);
        }
    }


    public void updateTotalTime(long milliseconds) {
        if (songTotalTime != null) {
            songTotalTime.setText(formatTime(milliseconds));
        }

        if (progressSlider != null) {
            progressSlider.setValueTo((int) Math.max(milliseconds, 1));
        }
    }


    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void updatePlayPauseButton(int playbackState) {
        if (playPauseButton == null) return;

        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            playPauseButton.setImageResource(R.drawable.ic_pause_m3_24dp);
        } else {
            playPauseButton.setImageResource(R.drawable.ic_play_m3_24dp);
        }
    }




}
