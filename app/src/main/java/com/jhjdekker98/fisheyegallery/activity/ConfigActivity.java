package com.jhjdekker98.fisheyegallery.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;

public class ConfigActivity extends AppCompatActivity {

    private static final String FOLDER_DEFAULT = "/storage/emulated/0/DCIM";
    private static final int COLUMNS_MAX = 9;

    private TextView txtFolderPath;
    private EditText editDepth;
    private EditText editColumns;
    private RadioGroup themeRadioGroup;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        txtFolderPath = findViewById(R.id.txtFolderPath);
        editDepth = findViewById(R.id.editDepth);
        editColumns = findViewById(R.id.editColumns);
        themeRadioGroup = findViewById(R.id.themeRadioGroup);
        btnSave = findViewById(R.id.btnSave);

        // Load current configuration
        SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        String folderUriString = prefs.getString(Constants.SHARED_PREFS_KEY_FOLDER, null);
        if (folderUriString != null) {
            Uri folderUri = Uri.parse(folderUriString);
            txtFolderPath.setText(folderUri.getPath());
        } else {
            txtFolderPath.setText(FOLDER_DEFAULT);
        }
        editDepth.setText(String.valueOf(prefs.getInt(Constants.SHARED_PREFS_KEY_DEPTH, 0)));
        editColumns.setText(String.valueOf(prefs.getInt(Constants.SHARED_PREFS_KEY_COLUMNS, 3)));

        // Folder picker
        Button btnPickFolder = findViewById(R.id.btnPickFolder);
        btnPickFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, Constants.STORAGE_AREA_REQUEST_ID);
        });

        // Set initial radio button selection
        final int themeId = prefs.getInt(Constants.SHARED_PREFS_KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        switch (themeId) {
            case AppCompatDelegate.MODE_NIGHT_NO:
                themeRadioGroup.check(R.id.themeRadioLight);
                break;
            case AppCompatDelegate.MODE_NIGHT_YES:
                themeRadioGroup.check(R.id.themeRadioDark);
                break;
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                themeRadioGroup.check(R.id.themeRadioSystem);
                break;
        }

        // Save configuration
        btnSave.setOnClickListener(v -> {
            int depth;
            try {
                depth = Integer.parseInt(editDepth.getText().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid depth", Toast.LENGTH_SHORT).show();
                return;
            }

            int columns;
            try {
                columns = Integer.parseInt(editColumns.getText().toString());
                if (columns <= 0 || columns > COLUMNS_MAX) {
                    throw new IllegalArgumentException("msg");
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number of columns (must be a number)", Toast.LENGTH_SHORT).show();
                return;
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, "Invalid number of columns (must be 1-9)", Toast.LENGTH_SHORT).show();
                return;
            }

            // Determine which theme is selected
            int selectedTheme;
            int checkedId = themeRadioGroup.getCheckedRadioButtonId();
            if (checkedId == R.id.themeRadioLight) {
                selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.themeRadioDark) {
                selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }

            // Apply theme and save preferences
            AppCompatDelegate.setDefaultNightMode(selectedTheme);

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Constants.SHARED_PREFS_KEY_DEPTH, depth);
            editor.putInt(Constants.SHARED_PREFS_KEY_COLUMNS, columns);
            editor.putInt(Constants.SHARED_PREFS_KEY_THEME, selectedTheme);
            editor.apply();

            Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show();

            // Set result and return to main activity
            setResult(RESULT_OK);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.STORAGE_AREA_REQUEST_ID && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();

            // Persist permission
            getContentResolver().takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            // Save URI
            SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(Constants.SHARED_PREFS_KEY_FOLDER, treeUri.toString()).apply();

            // Update displayed folder
            txtFolderPath.setText(treeUri.getPath());
        }
    }
}

