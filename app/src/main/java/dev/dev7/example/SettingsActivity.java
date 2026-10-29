package dev.dev7.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppPrefs.applyTheme(this);
        setContentView(R.layout.activity_settings);

        Button themeButton = findViewById(R.id.btn_theme);
        Button languageButton = findViewById(R.id.btn_language);
        Button switchMode = findViewById(R.id.btn_switch_mode);
        TextView switchHint = findViewById(R.id.switch_mode_hint);

        boolean light = AppPrefs.isLightTheme(this);
        themeButton.setText(light ? R.string.btn_dark_theme : R.string.btn_light_theme);
        languageButton.setText(R.string.btn_language);

        boolean developer = AppPrefs.isDeveloperMode(this);
        if (developer) {
            switchMode.setText(R.string.mode_user);
            switchHint.setText(R.string.mode_user_hint);
        } else {
            switchMode.setText(R.string.mode_developer);
            switchHint.setText(R.string.mode_developer_hint);
        }

        themeButton.setOnClickListener(v -> {
            AppPrefs.setLightTheme(this, !AppPrefs.isLightTheme(this));
            recreate();
        });
        languageButton.setOnClickListener(v -> {
            LocaleHelper.toggleLanguage(this);
            recreate();
        });
        switchMode.setOnClickListener(v -> {
            boolean nextDeveloper = !AppPrefs.isDeveloperMode(this);
            AppPrefs.setDeveloperMode(this, nextDeveloper);
            Intent intent = new Intent(this, nextDeveloper ? MainActivity.class : HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
