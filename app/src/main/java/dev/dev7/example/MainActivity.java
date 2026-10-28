package dev.dev7.example;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DURATION_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class MainActivity extends AppCompatActivity {

    private Button connection;
    private TextView connection_speed, connection_traffic, connection_time, server_delay, connected_server_delay, connection_mode, core_version;
    private EditText v2ray_config;
    private SharedPreferences sharedPreferences;
    private BroadcastReceiver v2rayBroadCastReceiver;
    private boolean restartAfterModeSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        V2rayController.init(this, R.drawable.ic_launcher, "V2ray Android");
        connection = findViewById(R.id.btn_connection);
        connection_speed = findViewById(R.id.connection_speed);
        connection_time = findViewById(R.id.connection_duration);
        connection_traffic = findViewById(R.id.connection_traffic);
        server_delay = findViewById(R.id.server_delay);
        connection_mode = findViewById(R.id.connection_mode);
        connected_server_delay = findViewById(R.id.connected_server_delay);
        v2ray_config = findViewById(R.id.v2ray_config);
        core_version = findViewById(R.id.core_version);

        core_version.setText(V2rayController.getCoreVersion());
        sharedPreferences = getSharedPreferences("conf", MODE_PRIVATE);
        v2ray_config.setText(sharedPreferences.getString("v2ray_config", getDefaultConfig()));
        connection_mode.setText("connection mode : " + V2rayConfigs.serviceMode.toString());
        connection.setOnClickListener(view -> {
            sharedPreferences.edit().putString("v2ray_config", v2ray_config.getText().toString()).apply();
            if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
                if (V2rayController.startV2ray(this, "Test Server", v2ray_config.getText().toString(), null)) {
                    connection.setText("CONNECTING");
                }
            } else {
                V2rayController.stopV2ray(this);
            }
        });


        connected_server_delay.setOnClickListener(view -> {
            connected_server_delay.setText("connected server delay : measuring...");
            V2rayController.getConnectedV2rayServerDelay(this, delayResult -> runOnUiThread(() -> connected_server_delay.setText("connected server delay : " + delayResult + "ms")));
        });
        server_delay.setOnClickListener(view -> {
            server_delay.setText("server delay : measuring...");
            final String config = v2ray_config.getText().toString();
            new Thread(() -> {
                long delay = V2rayController.getV2rayServerDelay(config);
                runOnUiThread(() -> server_delay.setText("server delay : " + delay + "ms"));
            }, "v2ray-server-delay").start();
        });

        connection_mode.setOnClickListener(view -> {
            restartAfterModeSwitch = V2rayController.getConnectionState() != V2rayConstants.CONNECTION_STATES.DISCONNECTED;
            V2rayController.toggleConnectionMode();
            connection_mode.setText("connection mode : " + V2rayConfigs.serviceMode.toString());
            if (restartAfterModeSwitch) {
                V2rayController.stopV2ray(this);
            }
        });

        switch (V2rayController.getConnectionState()) {
            case CONNECTED:
                connection.setText("CONNECTED");
                connected_server_delay.callOnClick();
                break;
            case DISCONNECTED:
                connection.setText("DISCONNECTED");
                break;
            case CONNECTING:
                connection.setText("CONNECTING");
                break;
            default:
                break;
        }
        v2rayBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                runOnUiThread(() -> {
                    if (intent.getExtras() == null) {
                        return;
                    }
                    connection_time.setText("connection time : " + intent.getExtras().getString(SERVICE_DURATION_BROADCAST_EXTRA));
                    connection_speed.setText("connection speed : " + intent.getExtras().getString(SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA) + " | " + intent.getExtras().getString(SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA));
                    connection_traffic.setText("connection traffic : " + intent.getExtras().getString(SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA) + " | " + intent.getExtras().getString(SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA));
                    connection_mode.setText("connection mode : " + V2rayConfigs.serviceMode.toString());
                    V2rayConstants.CONNECTION_STATES state = (V2rayConstants.CONNECTION_STATES) intent.getExtras().getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
                    if (state == null) {
                        return;
                    }
                    switch (state) {
                        case CONNECTED:
                            connection.setText("CONNECTED");
                            break;
                        case DISCONNECTED:
                            connection.setText("DISCONNECTED");
                            connected_server_delay.setText("connected server delay : wait for connection");
                            if (restartAfterModeSwitch) {
                                restartAfterModeSwitch = false;
                                if (V2rayController.startV2ray(MainActivity.this, "Test Server", v2ray_config.getText().toString(), null)) {
                                    connection.setText("CONNECTING");
                                }
                            }
                            break;
                        case CONNECTING:
                            connection.setText("CONNECTING");
                            break;
                        default:
                            break;
                    }
                });
            }
        };

        ContextCompat.registerReceiver(this, v2rayBroadCastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public static String getDefaultConfig() {
        return "";
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (v2rayBroadCastReceiver != null) {
            try {
                unregisterReceiver(v2rayBroadCastReceiver);
            } catch (Exception ignore) {
            }
        }
    }
}
