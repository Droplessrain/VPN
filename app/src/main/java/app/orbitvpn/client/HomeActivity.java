package app.orbitvpn.client;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class HomeActivity extends AppCompatActivity {

    private static final AtomicBoolean CONNECT_RUNNING = new AtomicBoolean(false);

    private Button connectButton;
    private TextView statusView;
    private TextView noCountriesHint;
    private Spinner countryList;
    private final List<String> countryCodes = new ArrayList<>();
    private BroadcastReceiver stateReceiver;
    private boolean applyingCountry;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AppPrefs.isDeveloperMode(this)) {
            openDeveloper();
            return;
        }
        AppPrefs.applyTheme(this);
        setContentView(R.layout.activity_home);
        V2rayController.init(this, R.mipmap.ic_launcher, getString(R.string.app_name));

        connectButton = findViewById(R.id.btn_connect);
        statusView = findViewById(R.id.home_status);
        noCountriesHint = findViewById(R.id.no_countries_hint);
        countryList = findViewById(R.id.country_list);

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        connectButton.setOnClickListener(v -> onConnectClicked());
        refreshCountries();
        refreshConnectionUi(V2rayController.getConnectionState());

        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getExtras() == null) {
                    return;
                }
                V2rayConstants.CONNECTION_STATES state =
                        (V2rayConstants.CONNECTION_STATES) intent.getExtras()
                                .getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
                if (state != null) {
                    runOnUiThread(() -> refreshConnectionUi(state));
                }
            }
        };
        ContextCompat.registerReceiver(this, stateReceiver,
                new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppPrefs.isDeveloperMode(this)) {
            openDeveloper();
            return;
        }
        refreshCountries();
        refreshConnectionUi(V2rayController.getConnectionState());
    }

    private void openDeveloper() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void refreshCountries() {
        boolean russian = LocaleHelper.LANG_RU.equals(LocaleHelper.currentLanguage(this));
        List<String> codes = ServerRepository.countriesWithFavorites(this);
        countryCodes.clear();
        countryCodes.addAll(codes);
        List<String> labels = new ArrayList<>();
        for (String code : codes) {
            labels.add(CountryHelper.labelOf(code, russian));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.home_spinner_item, labels);
        adapter.setDropDownViewResource(R.layout.server_spinner_item);
        applyingCountry = true;
        countryList.setAdapter(adapter);
        if (codes.isEmpty()) {
            noCountriesHint.setVisibility(View.VISIBLE);
            connectButton.setEnabled(false);
            statusView.setText(R.string.no_favorite_countries);
        } else {
            noCountriesHint.setVisibility(View.GONE);
            connectButton.setEnabled(true);
            String last = AppPrefs.lastCountry(this);
            int selected = Math.max(0, codes.indexOf(CountryHelper.normalize(last)));
            countryList.setSelection(selected);
            if (!CONNECT_RUNNING.get()
                    && V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
                statusView.setText(R.string.home_status_idle);
            }
        }
        countryList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (applyingCountry || position < 0 || position >= countryCodes.size()) {
                    return;
                }
                AppPrefs.setLastCountry(HomeActivity.this, countryCodes.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        countryList.post(() -> applyingCountry = false);
    }

    private void refreshConnectionUi(V2rayConstants.CONNECTION_STATES state) {
        if (state == V2rayConstants.CONNECTION_STATES.CONNECTED) {
            connectButton.setText(R.string.btn_disconnect);
            statusView.setText(R.string.home_status_connected);
        } else if (state == V2rayConstants.CONNECTION_STATES.CONNECTING || CONNECT_RUNNING.get()) {
            connectButton.setText(R.string.btn_connecting);
        } else {
            connectButton.setText(R.string.btn_connect);
            if (countryCodes.isEmpty()) {
                statusView.setText(R.string.no_favorite_countries);
            } else if (!CONNECT_RUNNING.get()) {
                statusView.setText(R.string.home_status_idle);
            }
        }
    }

    private void onConnectClicked() {
        if (V2rayController.getConnectionState() != V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
            V2rayController.stopV2ray(this);
            CONNECT_RUNNING.set(false);
            refreshConnectionUi(V2rayConstants.CONNECTION_STATES.DISCONNECTED);
            return;
        }
        if (countryCodes.isEmpty()) {
            statusView.setText(R.string.no_favorite_countries);
            return;
        }
        int index = countryList.getSelectedItemPosition();
        if (index < 0 || index >= countryCodes.size()) {
            return;
        }
        final String country = countryCodes.get(index);
        final List<ServerEntry> favorites = ServerRepository.favoritesForCountry(this, country);
        if (favorites.isEmpty()) {
            statusView.setText(R.string.no_favorite_countries);
            refreshCountries();
            return;
        }
        if (!CONNECT_RUNNING.compareAndSet(false, true)) {
            return;
        }
        connectButton.setText(R.string.btn_connecting);
        final boolean russian = LocaleHelper.LANG_RU.equals(LocaleHelper.currentLanguage(this));
        new Thread(() -> {
            List<ServerEntry> ranked = probeAndRank(favorites);
            boolean started = false;
            for (int i = 0; i < ranked.size(); i++) {
                final int attempt = i + 1;
                final int total = ranked.size();
                final long delay = ranked.get(i).delayMs;
                runOnUiThread(() -> statusView.setText(
                        getString(R.string.home_status_trying_fastest, attempt, total, delay)));
                ServerEntry entry = ranked.get(i);
                String remark = entry.note == null || entry.note.isEmpty()
                        ? CountryHelper.labelOf(country, russian)
                        : entry.note;
                boolean ok = V2rayController.startV2ray(HomeActivity.this, remark, entry.uri, null);
                if (ok) {
                    AppPrefs.get(this).edit().putString("v2ray_config", entry.uri).apply();
                    started = true;
                    break;
                }
            }
            final boolean connected = started;
            CONNECT_RUNNING.set(false);
            runOnUiThread(() -> {
                if (!connected) {
                    statusView.setText(R.string.home_status_failed);
                    connectButton.setText(R.string.btn_connect);
                }
            });
        }, "home-connect").start();
    }

    private List<ServerEntry> probeAndRank(List<ServerEntry> favorites) {
        List<ServerEntry> alive = new ArrayList<>();
        int total = favorites.size();
        for (int i = 0; i < total; i++) {
            if (!CONNECT_RUNNING.get()) {
                break;
            }
            ServerEntry entry = favorites.get(i);
            final int done = i + 1;
            runOnUiThread(() -> statusView.setText(getString(R.string.home_status_measuring, done, total)));
            V2rayController.ServerProbe probe = V2rayController.probeServer(entry.uri);
            long delay = probe.delayMs > 0 ? probe.delayMs : 0L;
            ServerRepository.updateProbeResult(this, entry.uri, delay);
            if (delay > 0) {
                entry.delayMs = delay;
                entry.lastCheckedAt = System.currentTimeMillis();
                alive.add(entry);
            }
        }
        Collections.sort(alive, (a, b) -> Long.compare(a.delayMs, b.delayMs));
        return alive;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stateReceiver != null) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (Exception ignored) {
            }
        }
    }
}
