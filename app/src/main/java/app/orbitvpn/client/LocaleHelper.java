package app.orbitvpn.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

final class LocaleHelper {

    static final String PREF_LANGUAGE = "app_language";
    static final String LANG_EN = "en";
    static final String LANG_RU = "ru";

    private LocaleHelper() {
    }

    static String currentLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("conf", Context.MODE_PRIVATE);
        return prefs.getString(PREF_LANGUAGE, LANG_EN);
    }

    static String toggleLanguage(Context context) {
        String next = LANG_RU.equals(currentLanguage(context)) ? LANG_EN : LANG_RU;
        context.getSharedPreferences("conf", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LANGUAGE, next)
                .apply();
        return next;
    }

    static Context wrap(Context context) {
        String language = currentLanguage(context);
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        }
        config.locale = locale;
        resources.updateConfiguration(config, resources.getDisplayMetrics());
        return context;
    }
}
