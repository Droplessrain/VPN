package dev.dev7.example;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

final class ServerEntry {

    final String id;
    String uri;
    String countryCode;
    boolean favorite;
    String note;
    long delayMs;
    long lastCheckedAt;

    ServerEntry(String id, String uri, String countryCode, boolean favorite, String note) {
        this(id, uri, countryCode, favorite, note, 0L, 0L);
    }

    ServerEntry(String id, String uri, String countryCode, boolean favorite, String note, long delayMs, long lastCheckedAt) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.uri = uri == null ? "" : uri;
        this.countryCode = countryCode == null ? "" : countryCode.toUpperCase(Locale.US);
        this.favorite = favorite;
        this.note = note == null ? "" : note;
        this.delayMs = delayMs;
        this.lastCheckedAt = lastCheckedAt;
    }

    static ServerEntry fromJson(JSONObject json) throws JSONException {
        return new ServerEntry(
                json.optString("id", ""),
                json.optString("uri", ""),
                json.optString("country", ""),
                json.optBoolean("favorite", false),
                json.optString("note", ""),
                json.optLong("delayMs", 0L),
                json.optLong("lastCheckedAt", 0L)
        );
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("uri", uri);
        json.put("country", countryCode == null ? "" : countryCode);
        json.put("favorite", favorite);
        json.put("note", note == null ? "" : note);
        json.put("delayMs", delayMs);
        json.put("lastCheckedAt", lastCheckedAt);
        return json;
    }

    boolean hasCountry() {
        return countryCode != null && !countryCode.isEmpty();
    }
}
