package app.orbitvpn.client;

import android.content.Context;
import android.os.Bundle;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

public class PrivacyPolicyActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppPrefs.applyTheme(this);
        setContentView(R.layout.activity_privacy);

        WebView web = findViewById(R.id.privacy_web);
        web.getSettings().setJavaScriptEnabled(false);
        String asset = LocaleHelper.LANG_RU.equals(LocaleHelper.currentLanguage(this))
                ? "privacy_policy_ru.html"
                : "privacy_policy_en.html";
        web.loadUrl("file:///android_asset/" + asset);
    }
}
