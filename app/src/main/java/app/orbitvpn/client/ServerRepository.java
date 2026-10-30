package app.orbitvpn.client;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class ServerRepository {

    private static final String FILE_NAME = "servers.json";
    private static final Object LOCK = new Object();

    private ServerRepository() {
    }

    static List<ServerEntry> load(Context context) {
        synchronized (LOCK) {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.isFile()) {
                List<ServerEntry> seeded = seedFromAssets(context);
                saveLocked(context, seeded);
                return seeded;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                JSONArray array = new JSONArray(builder.toString());
                List<ServerEntry> servers = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    servers.add(ServerEntry.fromJson(array.getJSONObject(i)));
                }
                return servers;
            } catch (Exception error) {
                List<ServerEntry> seeded = seedFromAssets(context);
                saveLocked(context, seeded);
                return seeded;
            }
        }
    }

    static void save(Context context, List<ServerEntry> servers) {
        synchronized (LOCK) {
            saveLocked(context, servers);
        }
    }

    private static void saveLocked(Context context, List<ServerEntry> servers) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            JSONArray array = new JSONArray();
            for (ServerEntry server : servers) {
                array.put(server.toJson());
            }
            writer.write(array.toString());
        } catch (Exception ignored) {
        }
    }

    static List<ServerEntry> sorted(Context context) {
        boolean russian = LocaleHelper.LANG_RU.equals(LocaleHelper.currentLanguage(context));
        List<ServerEntry> servers = load(context);
        Collections.sort(servers, CountryHelper.byCountryThenNote(russian));
        return servers;
    }

    static List<ServerEntry> favoritesForCountry(Context context, String countryCode) {
        String code = CountryHelper.normalize(countryCode);
        List<ServerEntry> result = new ArrayList<>();
        for (ServerEntry server : sorted(context)) {
            if (server.favorite && code.equals(CountryHelper.normalize(server.countryCode))) {
                result.add(server);
            }
        }
        return result;
    }

    static void updateProbeResult(Context context, String uri, long delayMs) {
        List<ServerEntry> servers = load(context);
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (ServerEntry server : servers) {
            if (server.uri.equals(uri)) {
                server.delayMs = delayMs;
                server.lastCheckedAt = now;
                changed = true;
                break;
            }
        }
        if (changed) {
            save(context, servers);
        }
    }

    static List<ServerEntry> favoritesAliveByDelay(Context context, String countryCode) {
        List<ServerEntry> favorites = favoritesForCountry(context, countryCode);
        List<ServerEntry> alive = new ArrayList<>();
        for (ServerEntry server : favorites) {
            if (server.delayMs > 0) {
                alive.add(server);
            }
        }
        Collections.sort(alive, (a, b) -> Long.compare(a.delayMs, b.delayMs));
        return alive;
    }

    static List<String> countriesWithFavorites(Context context) {
        boolean russian = LocaleHelper.LANG_RU.equals(LocaleHelper.currentLanguage(context));
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        List<ServerEntry> favorites = new ArrayList<>();
        for (ServerEntry server : load(context)) {
            if (server.favorite && server.countryCode != null && !server.countryCode.isEmpty()) {
                favorites.add(server);
            }
        }
        Collections.sort(favorites, CountryHelper.byCountryThenNote(russian));
        for (ServerEntry server : favorites) {
            codes.add(CountryHelper.normalize(server.countryCode));
        }
        return new ArrayList<>(codes);
    }

    static ServerEntry findById(Context context, String id) {
        for (ServerEntry server : load(context)) {
            if (server.id.equals(id)) {
                return server;
            }
        }
        return null;
    }

    static void upsert(Context context, ServerEntry entry) {
        List<ServerEntry> servers = load(context);
        boolean replaced = false;
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).id.equals(entry.id)) {
                servers.set(i, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            servers.add(entry);
        }
        save(context, servers);
    }

    static void delete(Context context, String id) {
        List<ServerEntry> servers = load(context);
        for (int i = servers.size() - 1; i >= 0; i--) {
            if (servers.get(i).id.equals(id)) {
                servers.remove(i);
            }
        }
        save(context, servers);
    }

    static int retainUris(Context context, java.util.Collection<String> keepUris) {
        java.util.HashSet<String> keep = new java.util.HashSet<>(keepUris);
        List<ServerEntry> servers = load(context);
        List<ServerEntry> retained = new ArrayList<>();
        for (ServerEntry server : servers) {
            if (keep.contains(server.uri)) {
                retained.add(server);
            }
        }
        save(context, retained);
        return servers.size() - retained.size();
    }

    static int removeUris(Context context, java.util.Collection<String> dropUris) {
        java.util.HashSet<String> drop = new java.util.HashSet<>(dropUris);
        List<ServerEntry> servers = load(context);
        List<ServerEntry> retained = new ArrayList<>();
        for (ServerEntry server : servers) {
            if (!drop.contains(server.uri)) {
                retained.add(server);
            }
        }
        int removed = servers.size() - retained.size();
        if (removed > 0) {
            save(context, retained);
        }
        return removed;
    }

    private static List<ServerEntry> seedFromAssets(Context context) {
        LinkedHashSet<String> uris = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("servers.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String uri = cleanServerLine(line);
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } catch (Exception ignored) {
        }
        List<ServerEntry> servers = new ArrayList<>();
        for (String uri : uris) {
            String country = CountryHelper.codeFromConfig(uri);
            servers.add(new ServerEntry(null, uri, country, false, remarkOf(uri)));
        }
        return servers;
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

    private static String remarkOf(String uri) {
        int hash = uri.lastIndexOf('#');
        if (hash < 0 || hash >= uri.length() - 1) {
            return "";
        }
        try {
            return URLDecoder.decode(uri.substring(hash + 1), "UTF-8");
        } catch (Exception e) {
            return uri.substring(hash + 1);
        }
    }
}
