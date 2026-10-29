package com.custom.proximitywake;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * ProximityAdminReceiver
 *
 * Device Admin Receiver required to enable DevicePolicyManager.lockNow().
 * The system calls this receiver when the user grants or revokes Device Admin
 * privileges from Settings > Security > Device Administrators.
 *
 * RAM profile: zero heap — no fields, no objects allocated at class level.
 * This class is instantiated by the system only on admin enable/disable events.
 */
public final class ProximityAdminReceiver extends DeviceAdminReceiver {

    /** Called when Device Admin is enabled by the user. */
    @Override
    public void onEnabled(Context context, Intent intent) {
        // No-op: state is persisted by the system via DevicePolicyManager
    }

    /** Called when Device Admin is disabled by the user. */
    @Override
    public void onDisabled(Context context, Intent intent) {
        // No-op: service will detect missing admin and fall back gracefully
    }
}
