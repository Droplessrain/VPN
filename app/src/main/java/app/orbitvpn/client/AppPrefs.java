package app.orbitvpn.client;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPrefs {

    static final String LIGHT_THEME = "light_theme";
    static final String DEVELOPER_MODE = "developer_mode";
    static final String LAST_COUNTRY = "last_country";

    private AppPrefs() {
    }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("conf", Context.MODE_PRIVATE);
    }

    static boolean isLightTheme(Context context) {
        return get(context).getBoolean(LIGHT_THEME, false);
    }

    static void setLightTheme(Context context, boolean light) {
        get(context).edit().putBoolean(LIGHT_THEME, light).apply();
    }

    static boolean isDeveloperMode(Context context) {
        return get(context).getBoolean(DEVELOPER_MODE, false);
    }

    static void setDeveloperMode(Context context, boolean developer) {
        get(context).edit().putBoolean(DEVELOPER_MODE, developer).apply();
    }

    static String lastCountry(Context context) {
        return get(context).getString(LAST_COUNTRY, "");
    }

    static void setLastCountry(Context context, String code) {
        get(context).edit().putString(LAST_COUNTRY, code == null ? "" : code).apply();
    }

    static void applyTheme(android.app.Activity activity) {
        activity.setTheme(isLightTheme(activity)
                ? R.style.Theme_V2rayExample_Light
                : R.style.Theme_V2rayExample);
    }
}
