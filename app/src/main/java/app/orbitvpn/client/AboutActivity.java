package app.orbitvpn.client;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppPrefs.applyTheme(this);
        setContentView(R.layout.activity_about);

        TextView version = findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        findViewById(R.id.btn_privacy).setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyPolicyActivity.class)));
    }
}
