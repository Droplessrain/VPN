package app.orbitvpn.client;

import android.util.Base64;

import org.json.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CountryHelper {

    static final String UNKNOWN = "";

    private static final Map<String, String[]> NAMES = new HashMap<>();

    static {
        put("US", "United States", "США");
        put("RU", "Russia", "Россия");
        put("FR", "France", "Франция");
        put("DE", "Germany", "Германия");
        put("NL", "Netherlands", "Нидерланды");
        put("CA", "Canada", "Канада");
        put("IN", "India", "Индия");
        put("HK", "Hong Kong", "Гонконг");
        put("SG", "Singapore", "Сингапур");
        put("GB", "United Kingdom", "Великобритания");
        put("UK", "United Kingdom", "Великобритания");
        put("JP", "Japan", "Япония");
        put("KR", "South Korea", "Южная Корея");
        put("CN", "China", "Китай");
        put("TR", "Turkey", "Турция");
        put("IR", "Iran", "Иран");
        put("AE", "United Arab Emirates", "ОАЭ");
        put("FI", "Finland", "Финляндия");
        put("SE", "Sweden", "Швеция");
        put("NO", "Norway", "Норвегия");
        put("PL", "Poland", "Польша");
        put("IT", "Italy", "Италия");
        put("ES", "Spain", "Испания");
        put("BR", "Brazil", "Бразилия");
        put("AU", "Australia", "Австралия");
        put("TW", "Taiwan", "Тайвань");
        put("UA", "Ukraine", "Украина");
        put("KZ", "Kazakhstan", "Казахстан");
        put("BY", "Belarus", "Беларусь");
        put("IL", "Israel", "Израиль");
        put("CH", "Switzerland", "Швейцария");
        put("AT", "Austria", "Австрия");
        put("BE", "Belgium", "Бельгия");
        put("CZ", "Czechia", "Чехия");
        put("RO", "Romania", "Румыния");
        put("MD", "Moldova", "Молдова");
        put("LV", "Latvia", "Латвия");
        put("LT", "Lithuania", "Литва");
        put("EE", "Estonia", "Эстония");
        put("IE", "Ireland", "Ирландия");
        put("PT", "Portugal", "Португалия");
        put("MX", "Mexico", "Мексика");
        put("AR", "Argentina", "Аргентина");
        put("ZA", "South Africa", "ЮАР");
        put("ID", "Indonesia", "Индонезия");
        put("TH", "Thailand", "Таиланд");
        put("VN", "Vietnam", "Вьетнам");
        put("MY", "Malaysia", "Малайзия");
        put("PH", "Philippines", "Филиппины");
    }

    private CountryHelper() {
    }

    private static void put(String code, String en, String ru) {
        NAMES.put(code, new String[]{en, ru});
    }

    static final class Info {
        final String code;
        final String flag;
        final String name;

        Info(String code, String flag, String name) {
            this.code = code;
            this.flag = flag;
            this.name = name;
        }
    }

    static List<String> knownCodes() {
        List<String> codes = new ArrayList<>(NAMES.keySet());
        Collections.sort(codes);
        return codes;
    }

    static String nameOf(String code, boolean russian) {
        if (code == null || code.isEmpty()) {
            return russian ? "Неизвестно" : "Unknown";
        }
        String normalized = normalize(code);
        String[] names = NAMES.get(normalized);
        if (names == null && "UK".equals(normalized)) {
            names = NAMES.get("GB");
        }
        if (names == null) {
            return normalized;
        }
        return russian ? names[1] : names[0];
    }

    static String flagOf(String code) {
        if (code == null || code.isEmpty()) {
            return "🏳️";
        }
        String normalized = normalize(code);
        if ("UK".equals(normalized)) {
            normalized = "GB";
        }
        if (normalized.length() != 2) {
            return "🏳️";
        }
        char a = Character.toUpperCase(normalized.charAt(0));
        char b = Character.toUpperCase(normalized.charAt(1));
        if (a < 'A' || a > 'Z' || b < 'A' || b > 'Z') {
            return "🏳️";
        }
        return new String(Character.toChars(0x1F1E6 + (a - 'A')))
                + new String(Character.toChars(0x1F1E6 + (b - 'A')));
    }

    static String labelOf(String code, boolean russian) {
        return flagOf(code) + "  " + nameOf(code, russian);
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.US);
    }

    static String codeFromConfig(String config) {
        Info info = fromConfig(config, false);
        return info == null ? UNKNOWN : info.code;
    }

    static Info fromConfig(String config, boolean russian) {
        String remark = remarkOf(config);
        if (remark == null || remark.isEmpty()) {
            return null;
        }
        for (int i = 0; i < remark.length(); ) {
            int first = remark.codePointAt(i);
            int firstLen = Character.charCount(first);
            if (first < 0x1F1E6 || first > 0x1F1FF || i + firstLen >= remark.length()) {
                i += firstLen;
                continue;
            }
            int second = remark.codePointAt(i + firstLen);
            if (second < 0x1F1E6 || second > 0x1F1FF) {
                i += firstLen;
                continue;
            }
            String code = "" + (char) ('A' + (first - 0x1F1E6)) + (char) ('A' + (second - 0x1F1E6));
            String flag = new String(Character.toChars(first)) + new String(Character.toChars(second));
            return new Info(code, flag, nameOf(code, russian));
        }
        return null;
    }

    static Comparator<ServerEntry> byCountryThenNote(boolean russian) {
        return (a, b) -> {
            boolean unknownA = a.countryCode == null || a.countryCode.isEmpty();
            boolean unknownB = b.countryCode == null || b.countryCode.isEmpty();
            if (unknownA != unknownB) {
                return unknownA ? 1 : -1;
            }
            String nameA = nameOf(a.countryCode, russian);
            String nameB = nameOf(b.countryCode, russian);
            int byCountry = nameA.compareToIgnoreCase(nameB);
            if (byCountry != 0) {
                return byCountry;
            }
            boolean favA = a.favorite;
            boolean favB = b.favorite;
            if (favA != favB) {
                return favA ? -1 : 1;
            }
            if (a.delayMs > 0 && b.delayMs > 0 && a.delayMs != b.delayMs) {
                return Long.compare(a.delayMs, b.delayMs);
            }
            String noteA = a.note == null || a.note.isEmpty() ? a.uri : a.note;
            String noteB = b.note == null || b.note.isEmpty() ? b.uri : b.note;
            return noteA.compareToIgnoreCase(noteB);
        };
    }

    private static String remarkOf(String uri) {
        if (uri == null) {
            return "";
        }
        String trimmed = uri.trim();
        if (trimmed.startsWith("vmess://")) {
            String body = trimmed.substring("vmess://".length());
            int hash = body.indexOf('#');
            String encoded = hash >= 0 ? body.substring(0, hash) : body;
            try {
                byte[] raw = Base64.decode(encoded, Base64.DEFAULT);
                JSONObject json = new JSONObject(new String(raw, StandardCharsets.UTF_8));
                String ps = json.optString("ps", "");
                if (!ps.isEmpty()) {
                    return ps;
                }
            } catch (Exception ignored) {
            }
            if (hash >= 0 && hash < body.length() - 1) {
                return decode(body.substring(hash + 1));
            }
            return "";
        }
        int hash = trimmed.lastIndexOf('#');
        if (hash >= 0 && hash < trimmed.length() - 1) {
            return decode(trimmed.substring(hash + 1));
        }
        return "";
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
