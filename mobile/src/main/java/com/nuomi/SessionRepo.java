package com.nuomi;



import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;

import android.media.session.MediaSessionManager;
import android.text.TextUtils;
import java.util.*;

public class SessionRepo {

    public static java.util.List<SessionInfo> loadActiveSessions(Context ctx) {
        MediaSessionManager msm =
                (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);

        ComponentName listener = new ComponentName(ctx, SessionSnifferService.class);
        java.util.List<MediaController> controllers = msm.getActiveSessions(listener);

        // 去重并过滤自身包名
        String self = ctx.getPackageName();
        Map<String, MediaController> byPkg = new LinkedHashMap<>();
        for (MediaController c : controllers) {
            String pkg = c.getPackageName();
            if (pkg == null || pkg.equals(self)) continue;
            if (!byPkg.containsKey(pkg)) byPkg.put(pkg, c);
        }

        java.util.List<SessionInfo> result = new ArrayList<>();
        for (MediaController c : byPkg.values()) {
            String pkg = c.getPackageName();
            String label = pkg;
            Drawable icon = null;
            try {
                label = ctx.getPackageManager()
                        .getApplicationLabel(
                                ctx.getPackageManager().getApplicationInfo(pkg, 0)
                        ).toString();
                icon = ctx.getPackageManager().getApplicationIcon(pkg);
            } catch (Exception ignore) {}

            String title = null;
            MediaMetadata md = c.getMetadata();
            if (md != null) {
                CharSequence t = md.getText(MediaMetadata.METADATA_KEY_TITLE);
                if (!TextUtils.isEmpty(t)) title = t.toString();
            }
            result.add(new SessionInfo(pkg, label, icon, title));
        }
        return result;
    }
}
