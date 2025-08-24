package com.nuomi;

import android.service.notification.NotificationListenerService;

public class SessionSnifferService extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        // 无需实现逻辑：仅用于允许 getActiveSessions()
    }
}
