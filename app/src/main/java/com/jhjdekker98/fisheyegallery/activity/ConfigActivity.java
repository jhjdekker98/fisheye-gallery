package com.jhjdekker98.fisheyegallery.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.config.ISettingsController;
import com.jhjdekker98.fisheyegallery.config.layout.LayoutSettingsController;
import com.jhjdekker98.fisheyegallery.config.saf.SafSettingsController;
import com.jhjdekker98.fisheyegallery.config.theme.ThemeSettingsController;
import java.util.ArrayList;
import java.util.List;

public class ConfigActivity extends AppCompatActivity {

    private List<ISettingsController> controllers;
    private SafSettingsController safController;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE);

        // Init controllers
        controllers = new ArrayList<>();
        controllers.add(new ThemeSettingsController(this, findViewById(R.id.themeSelect)));
        safController = new SafSettingsController(this,
                findViewById(R.id.checkMediaStore),
                findViewById(R.id.folderListContainer),
                findViewById(R.id.btnAddFolder),
                findViewById(R.id.editDepth));
        controllers.add(safController);
        controllers.add(new LayoutSettingsController(this, findViewById(R.id.columnsSetting)));

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(resultCode, resultCode, data);
        if (requestCode != Constants.STORAGE_AREA_REQUEST_ID || resultCode != RESULT_OK || data == null) {
            return;
        }
        final Uri folderUri = data.getData();
        if (folderUri == null) {
            return;
        }
        final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
        safController.handleFolderResult(folderUri, takeFlags);
    }
}
