package dev.dev7.lib.v2ray.services;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.dev7.lib.v2ray.core.V2rayCoreExecutor;
import dev.dev7.lib.v2ray.interfaces.StateListener;
import dev.dev7.lib.v2ray.interfaces.V2rayServicesListener;
import dev.dev7.lib.v2ray.model.V2rayConfigModel;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class V2rayProxyService extends Service implements V2rayServicesListener {
    private static final String TAG = "V2rayProxyService";
    private V2rayCoreExecutor v2rayCoreExecutor;
    private NotificationService notificationService;
    private StaticsBroadCastService staticsBroadCastService;
    private V2rayConstants.CONNECTION_STATES connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
    private V2rayConfigModel currentConfig = new V2rayConfigModel();
    private boolean isServiceCreated = false;
    private boolean commandReceiverRegistered = false;
    private final AtomicBoolean isStopping = new AtomicBoolean(false);

    private final BroadcastReceiver serviceCommandBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                V2rayConstants.SERVICE_COMMANDS serviceCommand = (V2rayConstants.SERVICE_COMMANDS) intent.getSerializableExtra(V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA);
                if (serviceCommand == null) {
                    return;
                }
                switch (serviceCommand) {
                    case STOP_SERVICE:
                        stopRunningCore();
                        break;
                    case MEASURE_DELAY:
                        if (v2rayCoreExecutor != null) {
                            v2rayCoreExecutor.broadCastCurrentServerDelay();
                        }
                        break;
                    default:
                        break;
                }
            } catch (Exception ignore) {}
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isServiceCreated) {
            connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING;
            v2rayCoreExecutor = new V2rayCoreExecutor(this);
            notificationService = new NotificationService(this);
            staticsBroadCastService = new StaticsBroadCastService(this, new StateListener() {
                @Override
                public V2rayConstants.CONNECTION_STATES getConnectionState() {
                    return connectionState;
                }

                @Override
                public V2rayConstants.CORE_STATES getCoreState() {
                    if (v2rayCoreExecutor == null) {
                        return V2rayConstants.CORE_STATES.IDLE;
                    }
                    return v2rayCoreExecutor.getCoreState();
                }

                @Override
                public long getDownloadSpeed() {
                    if (v2rayCoreExecutor == null) {
                        return -1;
                    }
                    return v2rayCoreExecutor.getDownloadSpeed();
                }

                @Override
                public long getUploadSpeed() {
                    if (v2rayCoreExecutor == null) {
                        return -1;
                    }
                    return v2rayCoreExecutor.getUploadSpeed();
                }
            });
            isServiceCreated = true;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w(TAG, "Service restarted without a start intent");
            stopService();
            return START_NOT_STICKY;
        }
        if (notificationService != null) {
            notificationService.ensureForeground(this);
        }
        try {
            V2rayConstants.SERVICE_COMMANDS serviceCommand = (V2rayConstants.SERVICE_COMMANDS) intent.getSerializableExtra(V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA);
            if (serviceCommand == null) {
                return START_NOT_STICKY;
            }
            switch (serviceCommand) {
                case STOP_SERVICE:
                    stopRunningCore();
                    break;
                case START_SERVICE:
                    currentConfig = (V2rayConfigModel) intent.getSerializableExtra(V2rayConstants.V2RAY_SERVICE_CONFIG_EXTRA);
                    if (currentConfig == null) {
                        stopService();
                        break;
                    }
                    isStopping.set(false);
                    connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING;
                    staticsBroadCastService.isTrafficStaticsEnabled = currentConfig.enableTrafficStatics;
                    if (currentConfig.enableTrafficStatics && currentConfig.enableTrafficStaticsOnNotification) {
                        staticsBroadCastService.trafficListener = notificationService.trafficListener;
                    }
                    registerCommandReceiver();
                    v2rayCoreExecutor.startCore(currentConfig);
                    return START_STICKY;
                default:
                    stopService();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "onStartCommand failed", e);
            stopService();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterCommandReceiver();
        super.onDestroy();
    }

    private void registerCommandReceiver() {
        if (commandReceiverRegistered) {
            return;
        }
        ContextCompat.registerReceiver(this, serviceCommandBroadcastReceiver, new IntentFilter(V2RAY_SERVICE_COMMAND_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
        commandReceiverRegistered = true;
    }

    private void unregisterCommandReceiver() {
        if (!commandReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(serviceCommandBroadcastReceiver);
        } catch (Exception ignore) {
        }
        commandReceiverRegistered = false;
    }

    @Override
    public boolean onProtect(int socket) {
        return true;
    }

    @Override
    public Service getService() {
        return this;
    }

    @Override
    public void startService() {
        if (isStopping.get()) {
            return;
        }
        connectionState = V2rayConstants.CONNECTION_STATES.CONNECTED;
        notificationService.setConnectedNotification(currentConfig.remark, currentConfig.applicationIcon);
        staticsBroadCastService.start();
    }

    private void stopRunningCore() {
        if (isStopping.get()) {
            if (notificationService != null) {
                notificationService.dismissNotification();
            }
            stopForeground(true);
            stopSelf();
            return;
        }
        if (v2rayCoreExecutor != null) {
            v2rayCoreExecutor.stopCore(true);
        } else {
            stopService();
        }
    }

    @Override
    public void stopService() {
        if (!isStopping.compareAndSet(false, true)) {
            return;
        }
        connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
        try {
            if (staticsBroadCastService != null) {
                staticsBroadCastService.stop();
            }
            if (notificationService != null) {
                notificationService.dismissNotification();
            }
            stopForeground(true);
            stopSelf();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (staticsBroadCastService != null) {
                    staticsBroadCastService.sendDisconnectedBroadCast(getApplicationContext());
                }
            });
        } catch (Exception e) {
            Log.d(TAG, "stopService => ", e);
        }
    }
}
