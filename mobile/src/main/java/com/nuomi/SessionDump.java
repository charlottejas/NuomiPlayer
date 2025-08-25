package com.nuomi;



import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

/**
 * 点选 Session 后，打印该 App 的 MediaSession 元数据（歌名/歌手/专辑/封面/URI 等）。
 * 用法：SessionDump.dumpFromPicker(context, packageName, appLabel);
 */
public final class SessionDump {

    private static final String TAG = "NuomiMetaDump";

    private SessionDump() {}

    /**
     * 从 SessionPicker 调用：根据包名找到对应的 MediaController 并打印元数据。
     */
    public static void dumpFromPicker(@NonNull Context ctx,
                                      @NonNull String pkg,
                                      @NonNull String label) {
        try {
            MediaSessionManager msm =
                    (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);

            // 使用你项目里的通知监听器（extends NotificationListenerService）
            ComponentName cn = new ComponentName(ctx, SessionSnifferService.class);

            List<MediaController> list = msm.getActiveSessions(cn);
            MediaControllerCompat compat = null;
            for (MediaController c : list) {
                if (pkg.equals(c.getPackageName())) {
                    MediaSessionCompat.Token token =
                            MediaSessionCompat.Token.fromToken(c.getSessionToken());
                    compat = new MediaControllerCompat(ctx, token);
                    break;
                }
            }

            if (compat == null) {
                Log.i(TAG, "dump[" + label + "] no controller for pkg=" + pkg
                        + ", active=" + (list == null ? 0 : list.size()));
                return;
            }

            dumpMetadataFields(compat, label + ":" + pkg);
            dumpQueuePeek(compat, 5);

        } catch (SecurityException se) {
            Log.w(TAG, "Notification access not granted for SessionSnifferService. " + se);
        } catch (Throwable t) {
            Log.e(TAG, "dumpFromPicker error", t);
        }
    }

    // ========================= 实际打印实现 =========================

    private static CharSequence ellipsis(@Nullable CharSequence cs, int max) {
        if (cs == null) return "null";
        String s = cs.toString();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String bool(boolean b) { return b ? "Y" : "N"; }

    private static void dumpMetadataFields(@Nullable MediaControllerCompat ctrl,
                                           @NonNull String label) {
        Log.i(TAG, "----- dumpMetadataFields [" + label + "] -----");
        if (ctrl == null) { Log.i(TAG, "controller=null"); return; }

        MediaMetadataCompat md = ctrl.getMetadata();
        Log.i(TAG, "package=" + ctrl.getPackageName() + " | hasMetadata=" + bool(md != null));
        if (md == null) return;

        // 1) 标准文本
        Log.i(TAG, "TITLE       : " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_TITLE), 60));
        Log.i(TAG, "ARTIST      : " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_ARTIST), 60));
        Log.i(TAG, "ALBUM       : " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_ALBUM), 60));

        // 2) 其他常见文本
        Log.i(TAG, "MEDIA_ID    : " + md.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID));
        Log.i(TAG, "ALBUM_ARTIST: " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST), 60));
        Log.i(TAG, "COMPOSER    : " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_COMPOSER), 60));
        Log.i(TAG, "GENRE       : " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_GENRE), 60));

        // 3) Display 系列（很多 App 用这组）
        Log.i(TAG, "DISPLAY_TIT : " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE), 60));
        Log.i(TAG, "DISPLAY_SUB : " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE), 60));
        Log.i(TAG, "DISPLAY_DESC: " + ellipsis(md.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION), 60));

        // 4) 数值：时长/曲号等
        long duration  = md.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        long trackNo   = md.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER);
        long numTracks = md.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS);
        long discNo    = md.getLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER);
        Log.i(TAG, "DURATION_MS : " + duration);
        Log.i(TAG, "TRACK_NO    : " + trackNo + " / NUM_TRACKS=" + numTracks + " | DISC_NO=" + discNo);

        // 5) 封面位图 & URI（两套：ART/ALBUM_ART 与 DISPLAY_ICON）
        Bitmap artBmp      = md.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
        Bitmap albumArtBmp = md.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        Bitmap iconBmp     = md.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON);
        String artUri      = md.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
        String albumArtUri = md.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
        String iconUri     = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI);

        Log.i(TAG, "ART_BITMAP  : " + (artBmp      != null ? artBmp.getWidth()      + "x" + artBmp.getHeight()      : "null"));
        Log.i(TAG, "ALBUM_ARTBM : " + (albumArtBmp != null ? albumArtBmp.getWidth() + "x" + albumArtBmp.getHeight() : "null"));
        Log.i(TAG, "ICON_BITMAP : " + (iconBmp     != null ? iconBmp.getWidth()     + "x" + iconBmp.getHeight()     : "null"));
        Log.i(TAG, "ART_URI     : " + artUri);
        Log.i(TAG, "ALBUM_ARTURI: " + albumArtUri);
        Log.i(TAG, "ICON_URI    : " + iconUri);

        // 6) 评分（有的 App 会填）
        RatingCompat sysRating  = md.getRating(MediaMetadataCompat.METADATA_KEY_RATING);
        RatingCompat userRating = md.getRating(MediaMetadataCompat.METADATA_KEY_USER_RATING);
        Log.i(TAG, "RATING      : " + (sysRating  != null ? sysRating.toString()  : "null"));
        Log.i(TAG, "USER_RATING : " + (userRating != null ? userRating.toString() : "null"));

        // 7) 简表：到底包含哪些 Key（一眼看全）
        String[] keys = new String[] {
                MediaMetadataCompat.METADATA_KEY_TITLE,
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                MediaMetadataCompat.METADATA_KEY_ALBUM,
                MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadataCompat.METADATA_KEY_DURATION,
                MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                MediaMetadataCompat.METADATA_KEY_ART,
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                MediaMetadataCompat.METADATA_KEY_ART_URI,
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI
        };
        StringBuilder present = new StringBuilder();
        for (String k : keys) {
            if (md.containsKey(k)) {
                // 只打印 KEY 的简名，易读
                present.append(k.substring(k.lastIndexOf('_') + 1)).append(", ");
            }
        }
        if (present.length() > 2) present.setLength(present.length() - 2);
        Log.i(TAG, "PRESENT_KEYS: [" + present + "]");
    }

    private static void dumpQueuePeek(@Nullable MediaControllerCompat ctrl, int maxItems) {
        if (ctrl == null) return;
        List<MediaSessionCompat.QueueItem> q = ctrl.getQueue();
        if (q == null || q.isEmpty()) {
            Log.i(TAG, "QUEUE: empty");
            return;
        }
        Log.i(TAG, "QUEUE: size=" + q.size());
        for (int i = 0; i < Math.min(maxItems, q.size()); i++) {
            MediaSessionCompat.QueueItem it = q.get(i);
            MediaDescriptionCompat d = it.getDescription();
            Log.i(TAG, "  #" + i
                    + " title=" + (d.getTitle() == null ? "null" : ellipsis(d.getTitle(), 50))
                    + " | sub=" + (d.getSubtitle() == null ? "null" : ellipsis(d.getSubtitle(), 50))
                    + " | mediaId=" + d.getMediaId()
                    + " | iconUri=" + d.getIconUri()
                    + " | mediaUri=" + d.getMediaUri());
        }
    }
}
