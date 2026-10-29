package dev.dev7.example;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class ServersActivity extends AppCompatActivity {

    private final List<ServerEntry> servers = new ArrayList<>();
    private ServerAdapter adapter;
    private boolean russian;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppPrefs.applyTheme(this);
        setContentView(R.layout.activity_servers);
        russian = LocaleHelper.LANG_RU.equals(LocaleHelper.currentLanguage(this));

        ListView listView = findViewById(R.id.server_items);
        adapter = new ServerAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> openEditor(servers.get(position)));

        Button add = findViewById(R.id.btn_add_server);
        add.setOnClickListener(v -> openEditor(null));
        reload();
    }

    private void reload() {
        servers.clear();
        servers.addAll(ServerRepository.sorted(this));
        adapter.notifyDataSetChanged();
    }

    private void openEditor(ServerEntry existing) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_edit_server, null, false);
        EditText uriField = content.findViewById(R.id.edit_uri);
        EditText noteField = content.findViewById(R.id.edit_note);
        Spinner countrySpinner = content.findViewById(R.id.edit_country);
        CheckBox favoriteBox = content.findViewById(R.id.edit_favorite);

        List<String> codes = new ArrayList<>();
        codes.add("");
        codes.addAll(CountryHelper.knownCodes());
        List<String> labels = new ArrayList<>();
        for (String code : codes) {
            labels.add(CountryHelper.labelOf(code, russian));
        }
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        countryAdapter.setDropDownViewResource(R.layout.server_spinner_item);
        countrySpinner.setAdapter(countryAdapter);

        if (existing != null) {
            uriField.setText(existing.uri);
            noteField.setText(existing.note);
            favoriteBox.setChecked(existing.favorite);
            int index = codes.indexOf(CountryHelper.normalize(existing.countryCode));
            countrySpinner.setSelection(Math.max(0, index));
        } else {
            favoriteBox.setChecked(false);
            countrySpinner.setSelection(0);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.add_server_title : R.string.edit_server_title)
                .setView(content)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.btn_delete, (d, w) -> {
                ServerRepository.delete(this, existing.id);
                reload();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String uri = ServerRepository.cleanServerLine(uriField.getText().toString());
            if (uri == null) {
                Toast.makeText(this, R.string.invalid_uri, Toast.LENGTH_SHORT).show();
                return;
            }
            int countryIndex = countrySpinner.getSelectedItemPosition();
            String country = countryIndex >= 0 && countryIndex < codes.size() ? codes.get(countryIndex) : "";
            if (country.isEmpty()) {
                country = CountryHelper.codeFromConfig(uri);
            }
            String note = noteField.getText().toString().trim();
            ServerEntry entry = existing == null
                    ? new ServerEntry(null, uri, country, favoriteBox.isChecked(), note)
                    : new ServerEntry(existing.id, uri, country, favoriteBox.isChecked(), note);
            ServerRepository.upsert(this, entry);
            dialog.dismiss();
            reload();
        }));
        dialog.show();
    }

    private final class ServerAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return servers.size();
        }

        @Override
        public ServerEntry getItem(int position) {
            return servers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(ServersActivity.this).inflate(R.layout.item_server, parent, false);
            }
            ServerEntry entry = getItem(position);
            TextView title = view.findViewById(R.id.item_title);
            TextView subtitle = view.findViewById(R.id.item_subtitle);
            String star = entry.favorite ? getString(R.string.favorite_mark) + " " : "";
            title.setText(star + CountryHelper.labelOf(entry.countryCode, russian));
            String note = entry.note == null || entry.note.isEmpty() ? entry.uri : entry.note;
            if (note.length() > 120) {
                note = note.substring(0, 120);
            }
            subtitle.setText(note);
            return view;
        }
    }
}
