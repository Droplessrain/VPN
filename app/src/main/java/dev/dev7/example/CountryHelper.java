package dev.dev7.example;

import android.util.Base64;

import org.json.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class CountryHelper {

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
        final String flag;
        final String name;

        Info(String flag, String name) {
            this.flag = flag;
            this.name = name;
        }
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
            String[] names = NAMES.get(code);
            String name = names == null ? code : (russian ? names[1] : names[0]);
            return new Info(flag, name);
        }
        return null;
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
