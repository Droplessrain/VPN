package dev.dev7.lib.v2ray.services;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.dev7.lib.v2ray.core.Tun2SocksExecutor;
import dev.dev7.lib.v2ray.core.V2rayCoreExecutor;
import dev.dev7.lib.v2ray.interfaces.StateListener;
import dev.dev7.lib.v2ray.interfaces.Tun2SocksListener;
import dev.dev7.lib.v2ray.model.V2rayConfigModel;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

import dev.dev7.lib.v2ray.interfaces.V2rayServicesListener;

public class V2rayVPNService extends VpnService implements V2rayServicesListener, Tun2SocksListener {
    private static final String TAG = "V2rayVPNService";
    private ParcelFileDescriptor tunnelInterface;
    private Tun2SocksExecutor tun2SocksExecutor;
    private V2rayCoreExecutor v2rayCoreExecutor;
    private NotificationService notificationService;
    private StaticsBroadCastService staticsBroadCastService;
    private V2rayConstants.CONNECTION_STATES connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
    private V2rayConfigModel currentConfig = new V2rayConfigModel();
    private boolean isServiceCreated = false;
    private boolean isServiceSetupStarted = false;
    private boolean commandReceiverRegistered = false;
    private int tunnelGeneration = 0;
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
            } catch (Exception ignore) {
            }
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        if (!isServiceCreated) {
            connectionState = V2rayConstants.CONNECTION_STATES.CONNECTING;
            StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(threadPolicy);
            tun2SocksExecutor = new Tun2SocksExecutor(this);
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
            V2rayConstants.SERVICE_COMMANDS serviceCommand = (V2rayConstants.SERVICE_COMMANDS) intent.getSerializableExtra(V2RAY_SERVICE_COMMAND_EXTRA);
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
                    tunnelGeneration++;
                    isStopping.set(false);
                    isServiceSetupStarted = false;
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

    @Override
    public void onRevoke() {
        stopService();
    }

    private void setupService() {
        if (isStopping.get()) {
            return;
        }
        if (isServiceSetupStarted) {
            return;
        }
        isServiceSetupStarted = true;
        final int generation = tunnelGeneration;
        closeTunnel();
        Intent prepare_intent = prepare(this);
        if (prepare_intent != null) {
            failTunnel(generation, "VPN consent is missing inside the service");
            return;
        }
        try {
            Builder builder = getBuilder();
            tunnelInterface = builder.establish();
            if (tunnelInterface == null) {
                failTunnel(generation, "VpnService.establish() returned null");
                return;
            }
            if (generation != tunnelGeneration || isStopping.get()) {
                closeTunnel();
                return;
            }
            int localDNSPort = 0;
            if (currentConfig.enableLocalTunneledDNS) {
                localDNSPort = currentConfig.localDNSPort;
            }
            File sockFile = new File(getFilesDir(), "sock_path");
            if (sockFile.exists() && !sockFile.delete()) {
                Log.w(TAG, "Could not delete stale tun2socks socket");
            }
            tun2SocksExecutor.run(this, currentConfig.localSocksPort, localDNSPort);
            if (!tun2SocksExecutor.isTun2SucksRunning()) {
                failTunnel(generation, "tun2socks did not start");
                return;
            }
            sendFileDescriptor(generation);
        } catch (Exception e) {
            Log.e(TAG, "setupFailed => ", e);
            failTunnel(generation, "setupFailed");
        }
    }

    private void onTunnelEstablished(final int generation) {
        if (generation != tunnelGeneration || isStopping.get()) {
            return;
        }
        connectionState = V2rayConstants.CONNECTION_STATES.CONNECTED;
        notificationService.setConnectedNotification(currentConfig.remark, currentConfig.applicationIcon);
        staticsBroadCastService.start();
    }

    private void failTunnel(final int generation, final String reason) {
        Log.e(TAG, reason);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (generation != tunnelGeneration) {
                return;
            }
            if (v2rayCoreExecutor != null) {
                v2rayCoreExecutor.stopCore(true);
            } else {
                stopService();
            }
        });
    }

    @NonNull
    private Builder getBuilder() {
        Builder builder = new Builder();
        builder.setSession(currentConfig.remark);
        builder.setMtu(1500);
        builder.addAddress("26.26.26.1", 30);
        builder.addRoute("0.0.0.0", 0);
        if (currentConfig.enableLocalTunneledDNS) {
            builder.addDnsServer("26.26.26.2");
        } else {
            builder.addDnsServer("1.1.1.1");
            builder.addDnsServer("8.8.8.8");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }
        if (currentConfig.blockedApplications != null) {
            for (int i = 0; i < currentConfig.blockedApplications.size(); i++) {
                try {
                    builder.addDisallowedApplication(currentConfig.blockedApplications.get(i));
                } catch (Exception ignore) {
                }
            }
        }
        return builder;
    }

    private void sendFileDescriptor(final int generation) {
        if (tunnelInterface == null) {
            failTunnel(generation, "TUN interface is missing");
            return;
        }
        String localSocksFile = new File(getApplicationContext().getFilesDir(), "sock_path").getAbsolutePath();
        FileDescriptor tunFd = tunnelInterface.getFileDescriptor();
        new Thread(() -> {
            boolean isSendFDSuccess = false;
            for (int sendFDTries = 0; sendFDTries < 10; sendFDTries++) {
                if (generation != tunnelGeneration) {
                    return;
                }
                LocalSocket clientLocalSocket = new LocalSocket();
                try {
                    Thread.sleep(50L * sendFDTries);
                    clientLocalSocket.connect(new LocalSocketAddress(localSocksFile, LocalSocketAddress.Namespace.FILESYSTEM));
                    OutputStream clientOutStream = clientLocalSocket.getOutputStream();
                    clientLocalSocket.setFileDescriptorsForSend(new FileDescriptor[]{tunFd});
                    clientOutStream.write(42);
                    clientLocalSocket.shutdownOutput();
                    isSendFDSuccess = true;
                    break;
                } catch (Exception ignore) {
                } finally {
                    try {
                        clientLocalSocket.close();
                    } catch (Exception ignoreClose) {
                    }
                }
            }
            if (generation != tunnelGeneration) {
                return;
            }
            if (isSendFDSuccess && tun2SocksExecutor.isTun2SucksRunning()) {
                new Handler(Looper.getMainLooper()).post(() -> onTunnelEstablished(generation));
            } else {
                Log.w("SendFDFailed", "Could not send file descriptor");
                failTunnel(generation, "Could not send the TUN file descriptor to tun2socks");
            }
        }, "sendFd_Thread").start();
    }

    private void closeTunnel() {
        if (tun2SocksExecutor != null) {
            tun2SocksExecutor.stopTun2Socks();
        }
        try {
            if (tunnelInterface != null) {
                tunnelInterface.close();
            }
        } catch (Exception ignore) {
        }
        tunnelInterface = null;
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
    public void onDestroy() {
        unregisterCommandReceiver();
        super.onDestroy();
    }

    @Override
    public boolean onProtect(int socket) {
        return protect(socket);
    }

    @Override
    public Service getService() {
        return this;
    }

    @Override
    public void startService() {
        setupService();
    }

    @Override
    public void stopService() {
        if (!isStopping.compareAndSet(false, true)) {
            return;
        }
        final int stopGeneration = ++tunnelGeneration;
        connectionState = V2rayConstants.CONNECTION_STATES.DISCONNECTED;
        isServiceSetupStarted = false;
        try {
            if (staticsBroadCastService != null) {
                staticsBroadCastService.stop();
            }
            closeTunnel();
            if (stopGeneration != tunnelGeneration || !isStopping.get()) {
                return;
            }
            if (notificationService != null) {
                notificationService.dismissNotification();
            }
            stopForeground(true);
            stopSelf();
            notifyDisconnected();
        } catch (Exception e) {
            Log.d(TAG, "stopService => ", e);
        }
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

    private void notifyDisconnected() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (staticsBroadCastService != null) {
                staticsBroadCastService.sendDisconnectedBroadCast(getApplicationContext());
            }
        });
    }

    @Override
    public void OnTun2SocksHasMassage(V2rayConstants.CORE_STATES tun2SocksState, String newMessage) {
        Log.i("TUN2SOCKS", newMessage);
    }

}
