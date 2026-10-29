package dev.dev7.lib.v2ray.core;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT;
import static dev.dev7.lib.v2ray.utils.Utilities.getUserAssetsPath;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import dev.dev7.lib.v2ray.interfaces.V2rayServicesListener;
import dev.dev7.lib.v2ray.model.V2rayConfigModel;
import dev.dev7.lib.v2ray.utils.Utilities;
import dev.dev7.lib.v2ray.utils.V2rayConstants;
import go.Seq;
import libv2ray.Libv2ray;
import libv2ray.V2RayPoint;
import libv2ray.V2RayVPNServiceSupportsSet;

public class V2rayCoreExecutor {

    private V2rayConstants.CORE_STATES coreState;
    public V2rayServicesListener v2rayServicesListener;
    private boolean preserveServiceOnShutdown = false;


    public final V2RayPoint v2RayPoint = Libv2ray.newV2RayPoint(new V2RayVPNServiceSupportsSet() {
        @Override
        public long shutdown() {
            if (preserveServiceOnShutdown) {
                return 0;
            }
            try {
                if (v2rayServicesListener == null) {
                    Log.d(V2rayCoreExecutor.class.getSimpleName(), "shutdown => can`t find initialed service.");
                    return -1;
                }
                v2rayServicesListener.stopService();
                return 0;
            } catch (Exception e) {
                Log.d(V2rayCoreExecutor.class.getSimpleName(), "shutdown =>", e);
                return -1;
            }
        }

        @Override
        public long prepare() {
            return 0;
        }

        @Override
        public boolean protect(long l) {
            if (v2rayServicesListener != null) {
                return v2rayServicesListener.onProtect((int) l);
            }
            return false;
        }

        @Override
        public long onEmitStatus(long l, String s) {
            return 0;
        }

        @Override
        public long setup(String s) {
            if (v2rayServicesListener == null) {
                Log.d(V2rayCoreExecutor.class.getSimpleName(), "setup => service listener is missing.");
                return -1;
            }
            try {
                coreState = V2rayConstants.CORE_STATES.RUNNING;
                v2rayServicesListener.startService();
                return 0;
            } catch (Exception e) {
                Log.d(V2rayCoreExecutor.class.getSimpleName(), "setupFailed => ", e);
                return -1;
            }
        }
    }, Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);


    public V2rayCoreExecutor(final Service targetService) {
        this.v2rayServicesListener = (V2rayServicesListener) targetService;
        Seq.setContext(targetService);
        Libv2ray.initV2Env(getUserAssetsPath(targetService.getApplicationContext()), Utilities.getDeviceIdForXUDPBaseKey(targetService.getApplicationContext()));
        coreState = V2rayConstants.CORE_STATES.IDLE;
        Log.d(V2rayCoreExecutor.class.getSimpleName(), "V2rayCoreExecutor -> New initialize from : " + targetService.getClass().getSimpleName());
    }

    public void startCore(final V2rayConfigModel v2rayConfig) {
        try {
            preserveServiceOnShutdown = true;
            try {
                stopCore(false);
            } finally {
                preserveServiceOnShutdown = false;
            }
            try {
                Libv2ray.testConfig(v2rayConfig.fullJsonConfig);
            } catch (Exception testException) {
                coreState = V2rayConstants.CORE_STATES.STOPPED;
                Log.w("FastConnect", "core rejected config: " + messageOf(testException), testException);
                stopCore(true);
                return;
            }
            v2RayPoint.setConfigureFileContent(v2rayConfig.fullJsonConfig);
            v2RayPoint.setDomainName(Utilities.normalizeIpv6(v2rayConfig.currentServerAddress) + ":" + v2rayConfig.currentServerPort);
            v2RayPoint.runLoop(false);
        } catch (Exception e) {
            Log.e("FastConnect", "core failed to run: " + messageOf(e), e);
            stopCore(true);
        }
    }

    public void stopCore(final boolean shouldStopService) {
        try {
            if (v2RayPoint.getIsRunning()) {
                v2RayPoint.stopLoop();
            }
            coreState = V2rayConstants.CORE_STATES.STOPPED;
        } catch (Exception e) {
            Log.d(V2rayCoreExecutor.class.getSimpleName(), "stopCore =>", e);
        }
        if (shouldStopService && v2rayServicesListener != null && !preserveServiceOnShutdown) {
            try {
                v2rayServicesListener.stopService();
            } catch (Exception e) {
                Log.d(V2rayCoreExecutor.class.getSimpleName(), "stopCore stopService =>", e);
            }
        }
    }

    public long getDownloadSpeed() {
        return v2RayPoint.queryStats("block", "downlink") + v2RayPoint.queryStats("proxy", "downlink");
    }

    public long getUploadSpeed() {
        return v2RayPoint.queryStats("block", "uplink")  + v2RayPoint.queryStats("proxy", "uplink");
    }

    public V2rayConstants.CORE_STATES getCoreState() {
        if (coreState == V2rayConstants.CORE_STATES.RUNNING) {
            if (!v2RayPoint.getIsRunning()) {
                coreState = V2rayConstants.CORE_STATES.STOPPED;
            }
            return coreState;
        }
        return coreState;
    }

    public void broadCastCurrentServerDelay() {
        if (v2rayServicesListener == null || v2rayServicesListener.getService() == null) {
            return;
        }
        int serverDelay = -1;
        try {
            serverDelay = (int) v2RayPoint.measureDelay("");
        } catch (Exception e) {
            Log.d(V2rayCoreExecutor.class.getSimpleName(), "broadCastCurrentServerDelay => ", e);
        }
        try {
            Intent serverDelayBroadcast = new Intent(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_INTENT);
            serverDelayBroadcast.setPackage(v2rayServicesListener.getService().getPackageName());
            serverDelayBroadcast.putExtra(V2RAY_SERVICE_CURRENT_CONFIG_DELAY_BROADCAST_EXTRA, serverDelay);
            v2rayServicesListener.getService().sendBroadcast(serverDelayBroadcast);
        } catch (Exception e) {
            Log.d(V2rayCoreExecutor.class.getSimpleName(), "broadCastCurrentServerDelay send => ", e);
        }
    }

    public static final class DelayMeasure {
        public final long delayMs;
        public final String error;

        public DelayMeasure(long delayMs, String error) {
            this.delayMs = delayMs;
            this.error = error;
        }
    }

    public static long getConfigDelay(final String config) {
        return measureConfig(config).delayMs;
    }

    public static DelayMeasure measureConfig(final String config) {
        if (config == null || config.trim().isEmpty()) {
            return new DelayMeasure(-1, "empty config");
        }
        try {
            JSONObject configJson = new JSONObject(config);
            configJson.remove("routing");
            configJson.remove("dns");
            JSONObject routing = new JSONObject();
            routing.put("domainStrategy", "IPIfNonMatch");
            configJson.put("routing", routing);
            configJson.put("dns", new JSONObject("{\n" +
                    "    \"hosts\": {\n" +
                    "        \"domain:googleapis.cn\": \"googleapis.com\"\n" +
                    "    },\n" +
                    "    \"servers\": [\n" +
                    "        \"1.1.1.1\"\n" +
                    "    ]\n" +
                    "}"));
            long delay = Libv2ray.measureOutboundDelay(configJson.toString(), "");
            if (delay <= 0) {
                return new DelayMeasure(delay, "no handshake");
            }
            return new DelayMeasure(delay, null);
        } catch (Exception error) {
            return new DelayMeasure(-1, messageOf(error));
        }
    }

    private static String messageOf(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').trim();
    }

}
