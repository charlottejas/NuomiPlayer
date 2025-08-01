package com.example.myapplication;

import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        titleTv = view.findViewById(R.id.title);
        titleTv.setText("请先打开 QQ 音乐播放任意歌曲");
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

        previousButton.setOnClickListener(v -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(requireActivity());
            if (controller != null) {
                controller.getTransportControls().skipToPrevious();
            }
        });

        playPauseButton.setOnClickListener(v -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(requireActivity());
            if (controller != null) {
                PlaybackStateCompat state = controller.getPlaybackState();
                if (state != null) {
                    int playbackState = state.getState();
                    if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
                        controller.getTransportControls().pause();
                    } else {
                        controller.getTransportControls().play();
                    }
                }
            }
        });

        nextButton.setOnClickListener(v -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(requireActivity());
            if (controller != null) {
                controller.getTransportControls().skipToNext();
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
