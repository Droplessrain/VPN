package dev.dev7.lib.v2ray;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DURATION_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;
import static dev.dev7.lib.v2ray.utils.V2rayConfigs.connectionState;
import static dev.dev7.lib.v2ray.utils.V2rayConfigs.currentConfig;
import static dev.dev7.lib.v2ray.utils.V2rayConfigs.serviceMode;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.dev7.lib.v2ray.core.V2rayCoreExecutor;
import dev.dev7.lib.v2ray.interfaces.LatencyDelayListener;
import dev.dev7.lib.v2ray.services.V2rayVPNService;
import dev.dev7.lib.v2ray.services.V2rayProxyService;
import dev.dev7.lib.v2ray.utils.Utilities;
import dev.dev7.lib.v2ray.utils.V2rayConstants;
import libv2ray.Libv2ray;

public class V2rayController {
    private static final String TAG = "V2rayController";
    private static final long CONNECTED_DELAY_TIMEOUT_MS = 5000L;
    private static ActivityResultLauncher<Intent> activityResultLauncher;
    private static boolean stateReceiverRegistered = false;
    private static V2rayConstants.SERVICE_MODES runningServiceMode = V2rayConstants.SERVICE_MODES.VPN_MODE;
    static final BroadcastReceiver stateUpdaterBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                connectionState = (V2rayConstants.CONNECTION_STATES) Objects.requireNonNull(intent.getExtras()).getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
            } catch (Exception ignore) {}
        }
    };

    public static void init(final AppCompatActivity activity, final int app_icon, final String app_name) {
        Utilities.copyAssets(activity);
        currentConfig.applicationIcon = app_icon;
        currentConfig.applicationName = app_name;
        registerReceivers(activity);
        activityResultLauncher = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                requestNotificationPermissionIfNeeded(activity);
                startTunnel(activity);
            } else {
                Toast.makeText(activity, "Permission not granted.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void registerReceivers(final Context context) {
        if (stateReceiverRegistered) {
            return;
        }
        Context appContext = context.getApplicationContext();
        ContextCompat.registerReceiver(appContext, stateUpdaterBroadcastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
        stateReceiverRegistered = true;
    }

    public static V2rayConstants.CONNECTION_STATES getConnectionState() {
        return connectionState;
    }

    public static boolean isPreparedForConnection(final Context context) {
        Intent vpnServicePrepareIntent = VpnService.prepare(context);
        return vpnServicePrepareIntent == null;
    }

    private static void requestNotificationPermissionIfNeeded(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{POST_NOTIFICATIONS}, 101);
        }
    }

    private static void prepareForConnection(final Activity activity) {
        Intent vpnServicePrepareIntent = VpnService.prepare(activity);
        if (vpnServicePrepareIntent == null) {
            startTunnel(activity);
            return;
        }
        if (activityResultLauncher == null) {
            Toast.makeText(activity, "Call V2rayController.init() from Activity.onCreate().", Toast.LENGTH_LONG).show();
            return;
        }
        activityResultLauncher.launch(vpnServicePrepareIntent);
    }

    public static boolean startV2ray(final Activity activity, final String remark, final String config, final ArrayList<String> blocked_apps) {
        if (!Utilities.refillV2rayConfig(remark, config, blocked_apps)) {
            Toast.makeText(activity, "Invalid config. A SOCKS inbound is required.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!isPreparedForConnection(activity)) {
            prepareForConnection(activity);
            return false;
        }
        requestNotificationPermissionIfNeeded(activity);
        startTunnel(activity);
        return true;
    }

    public static void stopV2ray(final Context context) {
        Intent stop_intent = new Intent(V2RAY_SERVICE_COMMAND_INTENT);
        stop_intent.setPackage(context.getPackageName());
        stop_intent.putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE);
        context.sendBroadcast(stop_intent);
        if (connectionState == V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
            return;
        }
        Intent stopServiceIntent = new Intent(context, runningServiceMode == V2rayConstants.SERVICE_MODES.PROXY_MODE ? V2rayProxyService.class : V2rayVPNService.class);
        stopServiceIntent.setPackage(context.getPackageName());
        stopServiceIntent.putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.STOP_SERVICE);
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                context.startForegroundService(stopServiceIntent);
            } else {
                context.startService(stopServiceIntent);
            }
        } catch (Exception e) {
            Log.w(TAG, "stop service start failed", e);
        }
    }

    public static void getConnectedV2rayServerDelay(final Context context, final LatencyDelayListener latencyDelayCallback) {
        if (getConnectionState() != V2rayConstants.CONNECTION_STATES.CONNECTED) {
            latencyDelayCallback.OnResultReady(-1);
            return;
        }
        final Context appContext = context.getApplicationContext();
        final AtomicBoolean delivered = new AtomicBoolean(false);
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final BroadcastReceiver connectionLatencyBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (!delivered.compareAndSet(false, true)) {
                    return;
                }
                timeoutHandler.removeCallbacksAndMessages(null);
                try {
                    int delay = Objects.requireNonNull(intent.getExtras()).getInt(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA);
                    latencyDelayCallback.OnResultReady(delay);
                } catch (Exception ignore) {
                    latencyDelayCallback.OnResultReady(-1);
                }
                unregisterQuietly(appContext, this);
            }
        };
        try {
            ContextCompat.registerReceiver(appContext, connectionLatencyBroadcastReceiver, new IntentFilter(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception e) {
            Log.w(TAG, "delay receiver was not registered", e);
            latencyDelayCallback.OnResultReady(-1);
            return;
        }
        timeoutHandler.postDelayed(() -> {
            if (delivered.compareAndSet(false, true)) {
                unregisterQuietly(appContext, connectionLatencyBroadcastReceiver);
                latencyDelayCallback.OnResultReady(-1);
            }
        }, CONNECTED_DELAY_TIMEOUT_MS);
        Intent get_delay_intent = new Intent(V2RAY_SERVICE_COMMAND_INTENT);
        get_delay_intent.setPackage(appContext.getPackageName());
        get_delay_intent.putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.MEASURE_DELAY);
        appContext.sendBroadcast(get_delay_intent);
    }

    private static void unregisterQuietly(Context context, BroadcastReceiver receiver) {
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception ignore) {}
    }

    public static long getV2rayServerDelay(final String config) {
        return V2rayCoreExecutor.getConfigDelay(Utilities.normalizeV2rayFullConfig(config));
    }

    public static String getCoreVersion() {
        return Libv2ray.checkVersionX();
    }

    public static void toggleConnectionMode() {
        if (serviceMode == V2rayConstants.SERVICE_MODES.PROXY_MODE) {
            serviceMode = V2rayConstants.SERVICE_MODES.VPN_MODE;
        } else {
            serviceMode = V2rayConstants.SERVICE_MODES.PROXY_MODE;
        }
    }

    public static void toggleTrafficStatics() {
        if (currentConfig.enableTrafficStatics) {
            currentConfig.enableTrafficStatics = false;
            currentConfig.enableTrafficStaticsOnNotification = false;
        } else {
            currentConfig.enableTrafficStatics = true;
            currentConfig.enableTrafficStaticsOnNotification = true;
        }
    }

    private static void startTunnel(final Context context) {
        Intent start_intent;
        if (serviceMode == V2rayConstants.SERVICE_MODES.PROXY_MODE) {
            start_intent = new Intent(context, V2rayProxyService.class);
        } else {
            start_intent = new Intent(context, V2rayVPNService.class);
        }
        start_intent.setPackage(context.getPackageName());
        runningServiceMode = serviceMode;
        start_intent.putExtra(V2RAY_SERVICE_COMMAND_EXTRA, V2rayConstants.SERVICE_COMMANDS.START_SERVICE);
        start_intent.putExtra(V2RAY_SERVICE_CONFIG_EXTRA, currentConfig);
        connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING;
        broadcastConnectionState(context, V2rayConstants.CONNECTION_STATES.CONNECTING);
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                context.startForegroundService(start_intent);
            } else {
                context.startService(start_intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "startTunnel failed", e);
            connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
            broadcastConnectionState(context, V2rayConstants.CONNECTION_STATES.DISCONNECTED);
        }
    }

    private static void broadcastConnectionState(final Context context, final V2rayConstants.CONNECTION_STATES state) {
        Intent intent = new Intent(V2RAY_SERVICE_STATICS_BROADCAST_INTENT);
        intent.setPackage(context.getPackageName());
        intent.putExtra(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA, state);
        intent.putExtra(SERVICE_DURATION_BROADCAST_EXTRA, "00:00:00");
        intent.putExtra(SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA, "0.0 B/s");
        intent.putExtra(SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA, "0.0 B/s");
        intent.putExtra(SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA, "0.0 B");
        intent.putExtra(SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA, "0.0 B");
        context.sendBroadcast(intent);
    }

    @Deprecated
    public static boolean IsPreparedForConnection(final Context context) {
        return isPreparedForConnection(context);
    }

    @Deprecated
    public static void StartV2ray(final Context context, final String remark, final String config, final ArrayList<String> blocked_apps) {
        if (!Utilities.refillV2rayConfig(remark, config, blocked_apps)) {
            Log.w(TAG, "Invalid config. A SOCKS inbound is required.");
            return;
        }
        if (!isPreparedForConnection(context)) {
            Log.w(TAG, "VPN consent is missing. Use startV2ray(Activity, ...) so the system dialog can be shown.");
            return;
        }
        startTunnel(context);
    }

    @Deprecated
    public static void StopV2ray(final Context context) {
        stopV2ray(context);
    }
}
