package com.jhjdekker98.fisheyegallery.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.config.ISettingsController;
import com.jhjdekker98.fisheyegallery.config.layout.LayoutSettingsController;
import com.jhjdekker98.fisheyegallery.config.mediastore.MediaStoreSettingsController;
import com.jhjdekker98.fisheyegallery.config.smb.SmbSettingsController;
import com.jhjdekker98.fisheyegallery.config.theme.ThemeSettingsController;
import java.util.ArrayList;
import java.util.List;

public class ConfigActivity extends AppCompatActivity {

    private List<ISettingsController> controllers;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE);

        // Init controllers
        controllers = new ArrayList<>();
        controllers.add(new MediaStoreSettingsController(this, findViewById(R.id.checkMediaStore)));
        controllers.add(new SmbSettingsController(this,
                findViewById(R.id.folderListContainer),
                findViewById(R.id.btnAddFolder),
                findViewById(R.id.editDepth)));
        controllers.add(new LayoutSettingsController(this, findViewById(R.id.columnsSetting)));
        controllers.add(new ThemeSettingsController(this, findViewById(R.id.themeSelect)));

        for (ISettingsController c : controllers) {
            c.init();
            c.loadFromPrefs(prefs);
        }

        btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            for (ISettingsController c : controllers) {
                c.saveToPrefs(editor);
            }
            editor.apply();
            Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });
    }
}
