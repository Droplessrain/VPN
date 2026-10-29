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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FastConnect";
    private static final String LIGHT_THEME = "light_theme";
    private static final AtomicBoolean SCAN_RUNNING = new AtomicBoolean(false);
    private static volatile boolean scanCancelFlag;
    private static volatile File logFile;
    private static WeakReference<MainActivity> scanUi = new WeakReference<>(null);

    private Button connection;
    private Button fastConnect;
    private Button themeButton;
    private Button languageButton;
    private TextView connection_speed, connection_traffic, connection_time, server_delay, connected_server_delay, connection_mode, core_version, fastConnectStatus, serverCountry;
    private EditText v2ray_config;
    private SharedPreferences sharedPreferences;
    private BroadcastReceiver v2rayBroadCastReceiver;
    private Spinner serverList;
    private final List<String> serverUris = new ArrayList<>();
    private boolean restartAfterModeSwitch;
    private boolean applyingServerSelection;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("conf", MODE_PRIVATE);
        boolean lightTheme = sharedPreferences.getBoolean(LIGHT_THEME, false);
        setTheme(lightTheme ? R.style.Theme_V2rayExample_Light : R.style.Theme_V2rayExample);
        setContentView(R.layout.activity_main);
        logFile = new File(getFilesDir(), "fast-connect.log");
        V2rayController.init(this, R.drawable.ic_launcher, "V2ray Android");
        connection = findViewById(R.id.btn_connection);
        connection_speed = findViewById(R.id.connection_speed);
        connection_time = findViewById(R.id.connection_duration);
        connection_traffic = findViewById(R.id.connection_traffic);
        server_delay = findViewById(R.id.server_delay);
        connection_mode = findViewById(R.id.connection_mode);
        connected_server_delay = findViewById(R.id.connected_server_delay);
        serverCountry = findViewById(R.id.server_country);
        v2ray_config = findViewById(R.id.v2ray_config);
        core_version = findViewById(R.id.core_version);
        fastConnect = findViewById(R.id.btn_fast_connect);
        fastConnectStatus = findViewById(R.id.fast_connect_status);
        scanUi = new WeakReference<>(this);

        core_version.setText(V2rayController.getCoreVersion());
        themeButton = findViewById(R.id.btn_theme);
        themeButton.setText(lightTheme ? R.string.btn_dark_theme : R.string.btn_light_theme);
        themeButton.setOnClickListener(view -> {
            sharedPreferences.edit().putBoolean(LIGHT_THEME, !lightTheme).apply();
            recreate();
        });
        languageButton = findViewById(R.id.btn_language);
        languageButton.setText(R.string.btn_language);
        languageButton.setOnClickListener(view -> {
            LocaleHelper.toggleLanguage(this);
            recreate();
        });
        v2ray_config.setText(sharedPreferences.getString("v2ray_config", getDefaultConfig()));
        v2ray_config.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCountryLabel(s == null ? "" : s.toString());
            }
        });
        updateCountryLabel(v2ray_config.getText().toString());
        serverList = findViewById(R.id.server_list);
        setupServerList();
        connection_mode.setText(getString(R.string.connection_mode, V2rayConfigs.serviceMode.toString()));
        connection_time.setText(getString(R.string.connection_time, "00:00:00"));
        connection_speed.setText(getString(R.string.connection_speed, "0 B/s", "0 B/s"));
        connection_traffic.setText(getString(R.string.connection_traffic, "0 B", "0 B"));
        connection.setOnClickListener(view -> {
            sharedPreferences.edit().putString("v2ray_config", v2ray_config.getText().toString()).apply();
            if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
                String config = v2ray_config.getText().toString();
                trace("I", "connect " + describe(config));
                if (V2rayController.startV2ray(this, selectedRemark(), config, null)) {
                    connection.setText(R.string.state_connecting);
                } else {
                    trace("W", "connect did not open the tunnel for " + describe(config));
                }
            } else {
                trace("I", "disconnect");
                V2rayController.stopV2ray(this);
            }
        });
        fastConnect.setOnClickListener(view -> onFastConnectClicked());
        if (SCAN_RUNNING.get()) {
            fastConnect.setText(R.string.btn_stop_scan);
            fastConnectStatus.setText(R.string.status_scan_running);
        }

        connected_server_delay.setOnClickListener(view -> {
            connected_server_delay.setText(R.string.connected_delay_measuring);
            V2rayController.getConnectedV2rayServerDelay(this, delayResult -> runOnUiThread(() ->
                    connected_server_delay.setText(getString(R.string.connected_delay, delayResult + "ms"))));
        });
        server_delay.setOnClickListener(view -> {
            server_delay.setText(R.string.server_delay_measuring);
            final String config = v2ray_config.getText().toString();
            new Thread(() -> {
                V2rayController.ServerProbe probe = V2rayController.probeServer(config);
                if (probe.delayMs > 0) {
                    trace("I", "delay OK " + describe(config) + " " + probe.delayMs + "ms");
                } else {
                    trace("W", "delay FAIL " + describe(config) + " " + (probe.error == null ? "no handshake" : probe.error));
                }
                runOnUiThread(() -> server_delay.setText(getString(R.string.server_delay, probe.delayMs + "ms")));
            }, "v2ray-server-delay").start();
        });

        connection_mode.setOnClickListener(view -> {
            restartAfterModeSwitch = V2rayController.getConnectionState() != V2rayConstants.CONNECTION_STATES.DISCONNECTED;
            V2rayController.toggleConnectionMode();
            connection_mode.setText(getString(R.string.connection_mode, V2rayConfigs.serviceMode.toString()));
            if (restartAfterModeSwitch) {
                V2rayController.stopV2ray(this);
            }
        });

        switch (V2rayController.getConnectionState()) {
            case CONNECTED:
                connection.setText(R.string.state_connected);
                connected_server_delay.callOnClick();
                break;
            case DISCONNECTED:
                connection.setText(R.string.state_disconnected);
                break;
            case CONNECTING:
                connection.setText(R.string.state_connecting);
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
                    connection_time.setText(getString(R.string.connection_time,
                            intent.getExtras().getString(SERVICE_DURATION_BROADCAST_EXTRA)));
                    connection_speed.setText(getString(R.string.connection_speed,
                            intent.getExtras().getString(SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA),
                            intent.getExtras().getString(SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA)));
                    connection_traffic.setText(getString(R.string.connection_traffic,
                            intent.getExtras().getString(SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA),
                            intent.getExtras().getString(SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA)));
                    connection_mode.setText(getString(R.string.connection_mode, V2rayConfigs.serviceMode.toString()));
                    V2rayConstants.CONNECTION_STATES state = (V2rayConstants.CONNECTION_STATES) intent.getExtras().getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
                    if (state == null) {
                        return;
                    }
                    switch (state) {
                        case CONNECTED:
                            connection.setText(R.string.state_connected);
                            break;
                        case DISCONNECTED:
                            connection.setText(R.string.state_disconnected);
                            connected_server_delay.setText(R.string.connected_delay_wait);
                            if (restartAfterModeSwitch) {
                                restartAfterModeSwitch = false;
                                if (V2rayController.startV2ray(MainActivity.this, selectedRemark(), v2ray_config.getText().toString(), null)) {
                                    connection.setText(R.string.state_connecting);
                                }
                            }
                            break;
                        case CONNECTING:
                            connection.setText(R.string.state_connecting);
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

    private void updateCountryLabel(String config) {
        boolean russian = LocaleHelper.LANG_RU.equals(LocaleHelper.currentLanguage(this));
        CountryHelper.Info info = CountryHelper.fromConfig(config, russian);
        if (info == null) {
            serverCountry.setText(R.string.country_unknown);
        } else {
            serverCountry.setText(getString(R.string.country_label, info.name, info.flag));
        }
    }

    private void onFastConnectClicked() {
        if (SCAN_RUNNING.get()) {
            scanCancelFlag = true;
            fastConnectStatus.setText(R.string.status_stopping);
            trace("I", "fast connect stop requested");
            return;
        }
        if (V2rayController.getConnectionState() != V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
            fastConnectStatus.setText(R.string.status_disconnect_first);
            trace("W", "fast connect skipped because a tunnel is open");
            return;
        }
        final List<String> queue = readServerUris();
        if (queue.isEmpty()) {
            fastConnectStatus.setText(R.string.status_list_empty);
            trace("W", "fast connect skipped because the server list is empty");
            return;
        }
        scanCancelFlag = false;
        if (!SCAN_RUNNING.compareAndSet(false, true)) {
            return;
        }
        fastConnect.setText(R.string.btn_stop_scan);
        fastConnectStatus.setText(getString(R.string.status_progress, 0, queue.size(), 0, 0));
        trace("I", "fast connect start " + queue.size() + " servers");
        final File filesDir = getFilesDir();
        new Thread(() -> runFastConnect(queue, filesDir), "v2ray-fast-connect").start();
    }

    private static void runFastConnect(List<String> queue, File filesDir) {
        List<RatedServer> working = new ArrayList<>();
        List<RatedServer> dead = new ArrayList<>();
        int total = queue.size();
        try {
            for (int i = 0; i < total; i++) {
                if (scanCancelFlag) {
                    break;
                }
                String uri = queue.get(i);
                String target = describe(uri);
                trace("I", (i + 1) + "/" + total + " probe " + target);
                V2rayController.ServerProbe probe = V2rayController.probeServer(uri);
                if (probe.delayMs > 0) {
                    working.add(new RatedServer(uri, probe.delayMs + "ms", true));
                    trace("I", (i + 1) + "/" + total + " OK " + target + " " + probe.delayMs + "ms");
                    final List<RatedServer> passed = new ArrayList<>(working);
                    postUi(activity -> activity.showPassedServers(passed, false));
                } else {
                    String error = probe.error == null ? "no handshake" : probe.error;
                    dead.add(new RatedServer(uri, error, false));
                    trace("W", (i + 1) + "/" + total + " FAIL " + target + " " + error);
                }
                final int done = i + 1;
                final int ok = working.size();
                final int bad = dead.size();
                postUi(activity -> activity.fastConnectStatus.setText(
                        activity.getString(R.string.status_progress, done, total, ok, bad)));
            }
        } finally {
            writeRatedServers(new File(filesDir, "fast-connect-working.txt"), working);
            writeRatedServers(new File(filesDir, "fast-connect-dead.txt"), dead);
            boolean cancelled = scanCancelFlag;
            trace("I", "fast connect " + (cancelled ? "stopped" : "done") + " working " + working.size() + " dead " + dead.size());
            SCAN_RUNNING.set(false);
            final List<RatedServer> workingResult = working;
            final List<RatedServer> deadResult = dead;
            postUi(activity -> activity.showScanResults(workingResult, deadResult, cancelled));
        }
    }

    private void showScanResults(List<RatedServer> working, List<RatedServer> dead, boolean cancelled) {
        fastConnect.setText(R.string.btn_fast_connect);
        showPassedServers(working, true);
        String current = v2ray_config.getText().toString();
        boolean currentPassed = false;
        for (RatedServer server : working) {
            if (server.uri.equals(current)) {
                currentPassed = true;
                break;
            }
        }
        if (!currentPassed && !working.isEmpty()) {
            v2ray_config.setText(working.get(0).uri);
            sharedPreferences.edit().putString("v2ray_config", working.get(0).uri).apply();
            trace("I", "first working server " + describe(working.get(0).uri));
        }
        fastConnectStatus.setText(getString(cancelled ? R.string.status_stopped : R.string.status_done,
                working.size(), dead.size()));
    }

    private void showPassedServers(List<RatedServer> working, boolean selectFirstWhenEmpty) {
        List<String> ordered = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.saved_custom));
        appendRated(working, ordered, labels, new HashSet<>());
        int selected = indexOfSaved(ordered);
        if (selected == 0 && selectFirstWhenEmpty && !working.isEmpty()) {
            selected = 1;
        }
        bindServerList(ordered, labels, selected);
    }

    private static void trace(String level, String message) {
        if ("W".equals(level)) {
            Log.w(TAG, message);
        } else {
            Log.i(TAG, message);
        }
        File file = logFile;
        if (file == null) {
            return;
        }
        synchronized (MainActivity.class) {
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date()));
                writer.write(' ');
                writer.write(level);
                writer.write(' ');
                writer.write(message);
                writer.write('\n');
            } catch (IOException ignored) {
            }
        }
    }

    private static void appendRated(List<RatedServer> rated, List<String> ordered, List<String> labels, Set<String> seen) {
        for (RatedServer server : rated) {
            if (!seen.add(server.uri)) {
                continue;
            }
            ordered.add(server.uri);
            String mark = server.ok ? "OK " + server.note : "FAIL " + shorten(server.note);
            labels.add(mark + "  " + labelFor(server.uri, ordered.size() - 1));
        }
    }

    private static void writeRatedServers(File file, List<RatedServer> servers) {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (RatedServer server : servers) {
                writer.write(server.ok ? "OK" : "FAIL");
                writer.write('\t');
                writer.write(server.note.replace('\t', ' ').replace('\n', ' '));
                writer.write('\t');
                writer.write(server.uri);
                writer.write('\n');
            }
        } catch (IOException error) {
            trace("W", "could not save " + file.getName() + ": " + error.getMessage());
        }
    }

    private static void postUi(UiUpdate update) {
        MainActivity activity = scanUi.get();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed()) {
                return;
            }
            update.apply(activity);
        });
    }

    private void setupServerList() {
        File workingFile = new File(getFilesDir(), "fast-connect-working.txt");
        File deadFile = new File(getFilesDir(), "fast-connect-dead.txt");
        if (!workingFile.isFile() && !deadFile.isFile()) {
            List<String> assets = readServerUris();
            List<String> labels = new ArrayList<>();
            labels.add(getString(R.string.saved_custom));
            for (int i = 0; i < assets.size(); i++) {
                labels.add(labelFor(assets.get(i), i));
            }
            bindServerList(assets, labels, indexOfSaved(assets));
            return;
        }
        List<RatedServer> working = readRatedServers(workingFile);
        showPassedServers(working, false);
        int deadCount = readRatedServers(deadFile).size();
        fastConnectStatus.setText(getString(R.string.status_saved_scan, working.size(), deadCount));
    }

    private int indexOfSaved(List<String> uris) {
        String saved = v2ray_config.getText().toString();
        for (int i = 0; i < uris.size(); i++) {
            if (uris.get(i).equals(saved)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void bindServerList(List<String> uris, List<String> labels, int selected) {
        serverUris.clear();
        serverUris.addAll(uris);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(R.layout.server_spinner_item);
        applyingServerSelection = true;
        serverList.setAdapter(adapter);
        serverList.setSelection(selected);
        serverList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (applyingServerSelection) {
                    return;
                }
                if (position <= 0 || position - 1 >= serverUris.size()) {
                    return;
                }
                String uri = serverUris.get(position - 1);
                v2ray_config.setText(uri);
                sharedPreferences.edit().putString("v2ray_config", uri).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        serverList.post(() -> applyingServerSelection = false);
    }

    private static List<RatedServer> readRatedServers(File file) {
        List<RatedServer> servers = new ArrayList<>();
        if (!file.isFile()) {
            return servers;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                String uri = cleanServerLine(parts[2]);
                if (uri == null) {
                    continue;
                }
                servers.add(new RatedServer(uri, parts[1], "OK".equals(parts[0])));
            }
        } catch (IOException error) {
            trace("W", "could not read " + file.getName() + ": " + error.getMessage());
        }
        return servers;
    }

    private interface UiUpdate {
        void apply(MainActivity activity);
    }

    private static final class RatedServer {
        final String uri;
        final String note;
        final boolean ok;

        RatedServer(String uri, String note, boolean ok) {
            this.uri = uri;
            this.note = note;
            this.ok = ok;
        }
    }

    private List<String> readServerUris() {
        LinkedHashSet<String> uris = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("servers.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String uri = cleanServerLine(line);
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } catch (IOException ignored) {
        }
        return new ArrayList<>(uris);
    }

    static String cleanServerLine(String raw) {
        if (raw == null) {
            return null;
        }
        String line = raw.trim();
        int comment = line.indexOf(" #");
        if (comment > 0) {
            line = line.substring(0, comment).trim();
        }
        if (!(line.startsWith("ss://") || line.startsWith("vless://") || line.startsWith("vmess://") || line.startsWith("trojan://"))) {
            return null;
        }
        if (line.startsWith("vmess://")) {
            return line.length() > "vmess://".length() ? line : null;
        }
        int at = line.indexOf('@');
        if (at <= "ss://".length() || !line.contains(":")) {
            return null;
        }
        return line;
    }

    private static String labelFor(String uri, int index) {
        String scheme = uri.substring(0, uri.indexOf("://"));
        String remark = "";
        int hash = uri.lastIndexOf('#');
        if (hash >= 0 && hash < uri.length() - 1) {
            remark = decode(uri.substring(hash + 1));
        }
        String host = hostOf(uri);
        String title = remark.isEmpty() ? host : remark;
        if (title.length() > 72) {
            title = title.substring(0, 72);
        }
        return (index + 1) + ". " + scheme + "  " + title;
    }

    private static String describe(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return "empty config";
        }
        int schemeEnd = uri.indexOf("://");
        String scheme = schemeEnd > 0 ? uri.substring(0, schemeEnd) : "config";
        String host = hostOf(uri);
        return host.isEmpty() ? scheme : scheme + " " + host;
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String oneLine = value.replace('\n', ' ').trim();
        return oneLine.length() > 42 ? oneLine.substring(0, 42) : oneLine;
    }

    private static String hostOf(String uri) {
        int at = uri.indexOf('@');
        if (at < 0) {
            return "";
        }
        int start = at + 1;
        int end = uri.length();
        for (int i = start; i < uri.length(); i++) {
            char c = uri.charAt(i);
            if (c == ':' || c == '?' || c == '#' || c == '/') {
                end = i;
                break;
            }
        }
        return uri.substring(start, end);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private String selectedRemark() {
        if (serverList == null || serverList.getSelectedItem() == null) {
            return "Server";
        }
        String label = serverList.getSelectedItem().toString();
        return label.isEmpty() ? "Server" : label;
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
