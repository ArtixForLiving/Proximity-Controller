package com.custom.proximitywake;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MainActivity — ProximityController
 *
 * Single-screen UI that lets the user:
 *   1. Toggle the foreground service on/off via a Switch.
 *   2. Select the active task mode via a RadioGroup.
 *
 * No support library, no ViewModel, no data binding.
 * UI state is persisted to SharedPreferences (tiny key-value store).
 *
 * RAM target: Activity heap < 2 MB; destroyed when user leaves,
 * service continues independently under 3 MB total.
 */
public final class MainActivity extends Activity {

    // ── Preference keys ────────────────────────────────────────────────────
    private static final String PREFS_NAME    = "proximity_prefs";
    private static final String KEY_ENABLED   = "service_enabled";
    private static final String KEY_MODE      = "task_mode";

    // ── Device Admin request code ──────────────────────────────────────────
    private static final int REQUEST_ENABLE_ADMIN = 101;

    // ── UI references (set in onCreate, nulled in onDestroy) ──────────────
    private Switch      serviceSwitch;
    private RadioGroup  taskRadioGroup;
    private RadioButton radioScreenOn;
    private RadioButton radioScreenOff;
    private RadioButton radioBoth;
    private TextView    statusText;

    // ── State ──────────────────────────────────────────────────────────────
    private SharedPreferences prefs;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName       adminComponent;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialise system services
        prefs               = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent      = new ComponentName(this, ProximityAdminReceiver.class);

        // Bind views
        serviceSwitch  = (Switch)      findViewById(R.id.switch_service);
        taskRadioGroup = (RadioGroup)  findViewById(R.id.radio_group_task);
        radioScreenOn  = (RadioButton) findViewById(R.id.radio_screen_on);
        radioScreenOff = (RadioButton) findViewById(R.id.radio_screen_off);
        radioBoth      = (RadioButton) findViewById(R.id.radio_screen_both);
        statusText     = (TextView)    findViewById(R.id.text_status);

        // Restore saved state
        restoreState();

        // Attach listeners
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onServiceToggled(isChecked);
            }
        });

        taskRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                onTaskModeChanged(checkedId);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh status text in case admin was granted/revoked in Settings
        updateStatusText();
    }

    @Override
    protected void onDestroy() {
        // Null out view references to prevent Activity memory leak
        serviceSwitch  = null;
        taskRadioGroup = null;
        radioScreenOn  = null;
        radioScreenOff = null;
        radioBoth      = null;
        statusText     = null;
        super.onDestroy();
    }

    // ── State helpers ──────────────────────────────────────────────────────

    /** Restore toggle + radio state from SharedPreferences. */
    private void restoreState() {
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        int     mode    = prefs.getInt(KEY_MODE, ProximityService.MODE_SCREEN_ON);

        serviceSwitch.setChecked(enabled);

        switch (mode) {
            case ProximityService.MODE_SCREEN_OFF:
                radioScreenOff.setChecked(true);
                break;
            case ProximityService.MODE_BOTH:
                radioBoth.setChecked(true);
                break;
            default:
                radioScreenOn.setChecked(true);
                break;
        }

        updateStatusText();
    }

    /** Persist current toggle and mode to SharedPreferences. */
    private void persistState(boolean enabled, int mode) {
        prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_MODE, mode)
                .apply(); // async write — no ANR risk
    }

    // ── Event handlers ─────────────────────────────────────────────────────

    /** Called when the main service switch is toggled. */
    private void onServiceToggled(boolean enabled) {
        int mode = resolveSelectedMode();

        // If Screen Off or Both is selected, ensure Device Admin is active
        if (enabled && (mode == ProximityService.MODE_SCREEN_OFF || mode == ProximityService.MODE_BOTH)) {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                // Prompt user to grant admin — switch back until granted
                serviceSwitch.setChecked(false);
                requestDeviceAdmin();
                return;
            }
        }

        persistState(enabled, mode);

        if (enabled) {
            startProximityService(mode);
        } else {
            stopProximityService();
        }

        updateStatusText();
    }

    /** Called when the task mode RadioGroup selection changes. */
    private void onTaskModeChanged(int checkedId) {
        int mode    = resolveSelectedMode();
        boolean svc = prefs.getBoolean(KEY_ENABLED, false);

        // If service is running, restart with new mode
        if (svc && isServiceRunning()) {
            startProximityService(mode);
        }

        persistState(svc, mode);
        updateStatusText();
    }

    // ── Service control ────────────────────────────────────────────────────

    /** Start ProximityService as a foreground service with the given mode. */
    private void startProximityService(int mode) {
        Intent intent = new Intent(this, ProximityService.class);
        intent.putExtra(ProximityService.EXTRA_MODE, mode);
        startService(intent);
    }

    /** Stop ProximityService. */
    private void stopProximityService() {
        Intent intent = new Intent(this, ProximityService.class);
        stopService(intent);
    }

    /** Returns true if ProximityService is currently running in the process list. */
    @SuppressWarnings("deprecation") // getRunningServices is the only API available without root on API 25
    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo svc : am.getRunningServices(Integer.MAX_VALUE)) {
            if (ProximityService.class.getName().equals(svc.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // ── Admin helper ───────────────────────────────────────────────────────

    /** Launch the system Device Admin activation screen. */
    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required to lock the screen when the proximity sensor is covered."
        );
        startActivityForResult(intent, REQUEST_ENABLE_ADMIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                // Admin granted — re-enable service automatically
                serviceSwitch.setChecked(true);
                onServiceToggled(true);
            } else {
                Toast.makeText(this,
                        "Device Admin is required for the Screen Off task.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    /**
     * Translate the currently checked RadioButton to a ProximityService mode constant.
     */
    private int resolveSelectedMode() {
        int checkedId = taskRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_screen_off)  return ProximityService.MODE_SCREEN_OFF;
        if (checkedId == R.id.radio_screen_both) return ProximityService.MODE_BOTH;
        return ProximityService.MODE_SCREEN_ON; // default
    }

    /** Refresh the status text below the header. */
    private void updateStatusText() {
        if (statusText == null) return;

        boolean running = isServiceRunning();
        boolean adminOk = devicePolicyManager.isAdminActive(adminComponent);
        int mode        = resolveSelectedMode();

        StringBuilder sb = new StringBuilder();
        sb.append(running ? "● Running" : "○ Stopped");

        if ((mode == ProximityService.MODE_SCREEN_OFF || mode == ProximityService.MODE_BOTH) && !adminOk) {
            sb.append("  ·  ⚠ Admin required");
        }

        statusText.setText(sb.toString());
    }
}
