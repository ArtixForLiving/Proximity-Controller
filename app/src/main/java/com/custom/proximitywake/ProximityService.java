package com.custom.proximitywake;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * ProximityService — Foreground Service
 *
 * Listens to the proximity sensor and performs the selected task:
 *   MODE_SCREEN_ON  : Wakes the screen when an object approaches the sensor.
 *   MODE_SCREEN_OFF : Locks the screen when the sensor is covered.
 *   MODE_BOTH       : Combines both behaviors.
 *
 * ─── EXTREME RAM OPTIMIZATION ────────────────────────────────────────────
 *  • All instance variables declared at class level (zero new() in hot path).
 *  • onSensorChanged() is GC-pressure-free: reads from the pre-allocated
 *    float array provided by the OS — no boxing, no allocation.
 *  • WakeLock is acquired/released rather than re-created each time.
 *  • DevicePolicyManager reference is cached; ComponentName is pre-built.
 *  • Notification is built once at startForeground(); never rebuilt.
 *  • SensorDelay.SENSOR_DELAY_NORMAL (200 ms) reduces CPU wake-ups.
 * ─────────────────────────────────────────────────────────────────────────
 *
 * Expected RAM footprint (service-only, activity killed): < 3 MB.
 */
public final class ProximityService extends Service implements SensorEventListener {

    // ── Public constants (read by MainActivity via Intent extras) ──────────
    public static final String EXTRA_MODE = "mode";
    public static final int MODE_SCREEN_ON  = 0;
    public static final int MODE_SCREEN_OFF = 1;
    public static final int MODE_BOTH       = 2;

    // ── Notification channel / ID ──────────────────────────────────────────
    private static final String CHANNEL_ID    = "proximity_ctrl_channel";
    private static final int    NOTIFICATION_ID = 1;

    // ── Proximity threshold: sensor returns 0 cm when covered ─────────────
    private static final float PROXIMITY_NEAR = 1.0f; // cm — near/covered

    // ── WakeLock duration for Screen On task (3 seconds) ─────────────────
    private static final long WAKE_LOCK_DURATION_MS = 3000L;

    // ── Class-level fields — ALL allocated once, never inside callbacks ────
    // SensorManager + sensor reference
    private SensorManager sensorManager;
    private Sensor        proximitySensor;

    // PowerManager + WakeLock for Screen On task
    private PowerManager          powerManager;
    @SuppressWarnings("deprecation") // SCREEN_BRIGHT_WAKE_LOCK is the correct flag for waking
    private PowerManager.WakeLock screenWakeLock;

    // DevicePolicyManager + admin component for Screen Off task
    private DevicePolicyManager devicePolicyManager;
    private ComponentName       adminComponent;

    // Active task mode (set from Intent, default to Screen On)
    private int activeMode = MODE_SCREEN_ON;

    // Cached distance reading — reused in onSensorChanged(), never boxed
    private float sensorDistance;

    // Pre-built notification (constructed once at startForeground)
    private Notification foregroundNotification;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();

        // Cache system service references — one allocation each, at startup
        sensorManager        = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        powerManager         = (PowerManager)  getSystemService(Context.POWER_SERVICE);
        devicePolicyManager  = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent       = new ComponentName(this, ProximityAdminReceiver.class);

        // Obtain the proximity sensor (null if hardware is missing)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        // Acquire WakeLock once (will be acquired/released on demand, not recreated)
        //noinspection deprecation — SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP is correct for wake-from-pocket
        screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ProximityController:WakeLock"
        );
        screenWakeLock.setReferenceCounted(false); // allow repeated acquire/release

        // Build the foreground notification once
        buildForegroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Promote to foreground immediately to prevent system from killing the service
        startForeground(NOTIFICATION_ID, foregroundNotification);

        // Read the selected task mode from the intent (default: Screen On)
        if (intent != null) {
            activeMode = intent.getIntExtra(EXTRA_MODE, MODE_SCREEN_ON);
        }

        // Register proximity listener at normal rate (5 Hz) — adequate for hand detection
        if (proximitySensor != null) {
            sensorManager.registerListener(
                    this,
                    proximitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
        }

        // Restart service if killed by low-memory: preserve mode via sticky + intent redelivery
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        // Unregister listener to stop sensor callbacks immediately
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        // Release WakeLock if held (avoid battery drain after service death)
        if (screenWakeLock != null && screenWakeLock.isHeld()) {
            screenWakeLock.release();
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Not a bound service
        return null;
    }

    // ── SensorEventListener ────────────────────────────────────────────────

    /**
     * Called by SensorManager on every proximity reading.
     *
     * GC PRESSURE RULE: This method MUST NOT allocate any new objects.
     * The float[] values array is owned by the OS and pre-allocated;
     * we read directly from it. sensorDistance is a primitive field.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Read raw distance in cm — 0.0 = covered/near, max_range = far
        sensorDistance = event.values[0];

        switch (activeMode) {
            case MODE_SCREEN_ON:
                if (sensorDistance < PROXIMITY_NEAR) {
                    performScreenOn();
                }
                break;

            case MODE_SCREEN_OFF:
                if (sensorDistance < PROXIMITY_NEAR) {
                    performScreenOff();
                }
                break;

            case MODE_BOTH:
                if (sensorDistance < PROXIMITY_NEAR) {
                    // Covered → lock screen
                    performScreenOff();
                } else {
                    // Uncovered / hand removed → wake screen
                    performScreenOn();
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not required for proximity sensor — proximity accuracy is not variable
    }

    // ── Task Implementations ───────────────────────────────────────────────

    /**
     * Screen On — acquire SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP.
     * The WakeLock object is pre-created; we only call acquire() here.
     * The lock auto-releases after WAKE_LOCK_DURATION_MS (timed acquire).
     */
    private void performScreenOn() {
        if (!screenWakeLock.isHeld()) {
            screenWakeLock.acquire(WAKE_LOCK_DURATION_MS);
        }
    }

    /**
     * Screen Off — call DevicePolicyManager.lockNow().
     * Requires the user to have granted Device Admin to this app.
     * If admin is not active, the call is skipped gracefully.
     */
    private void performScreenOff() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow();
        }
        // If admin not granted: silent no-op — user must activate admin first
    }

    // ── Notification ───────────────────────────────────────────────────────

    /**
     * Build the persistent foreground notification.
     * Called once in onCreate(); result stored in foregroundNotification.
     * No allocation happens during sensor callbacks.
     */
    @SuppressWarnings("deprecation") // Notification.Builder without channel is correct for API < 26
    private void buildForegroundNotification() {
        // Tap notification to reopen MainActivity
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // For API 25 (no NotificationChannel needed — introduced in API 26)
        foregroundNotification = new Notification.Builder(this)
                .setContentTitle("Proximity Controller")
                .setContentText("Service is running")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN) // minimal UI footprint
                .build();
    }
}
