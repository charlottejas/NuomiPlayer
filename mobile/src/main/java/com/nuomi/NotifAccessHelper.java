package com.nuomi;



import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

public class NotifAccessHelper {
    public static boolean isEnabled(Context ctx) {
        String flat = Settings.Secure.getString(
                ctx.getContentResolver(),
                "enabled_notification_listeners"
        );
        return !TextUtils.isEmpty(flat) && flat.contains(ctx.getPackageName());
    }

    public static void openSettings(Context ctx) {
        Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
