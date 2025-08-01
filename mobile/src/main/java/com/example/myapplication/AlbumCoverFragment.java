package com.example.myapplication;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AlbumCoverFragment extends Fragment {

    private ImageView playerImage;

    public AlbumCoverFragment() {
        super(R.layout.fragment_album_cover_m3);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playerImage = view.findViewById(R.id.player_image);
    }

    // 给外部调用，设置封面图
    public void updateCover(Bitmap albumArt) {
        if (playerImage != null && albumArt != null) {
            playerImage.setImageBitmap(albumArt);
        }
    }
}
