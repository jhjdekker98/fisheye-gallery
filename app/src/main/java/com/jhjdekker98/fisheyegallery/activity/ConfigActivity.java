package com.jhjdekker98.fisheyegallery.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;
import java.util.ArrayList;
import java.util.List;

public class ConfigActivity extends AppCompatActivity {

    private static final String SAF_SEPARATOR = ":";
    private static final int COLUMNS_MAX = 9;
    private final List<Uri> safFolders = new ArrayList<>();
    private CheckBox checkMediaStore;
    private LinearLayout folderListContainer;
    private Button btnAddFolder;
    private EditText editDepth;
    private EditText editColumns;
    private RadioGroup themeRadioGroup;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        checkMediaStore = findViewById(R.id.checkMediaStore);
        folderListContainer = findViewById(R.id.folderListContainer);
        btnAddFolder = findViewById(R.id.btnAddFolder);

        // Media Store checkbox
        final SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        checkMediaStore.setChecked(prefs.getBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, true));

        // SAF folders
        final String savedFolders = prefs.getString(Constants.SHARED_PREFS_KEY_SAF_FOLDERS, "");
        if (!savedFolders.isEmpty()) {
            for (String uriString : savedFolders.split(SAF_SEPARATOR)) {
                final Uri folderUri = Uri.parse(uriString);
                safFolders.add(folderUri);
                addFolderTextView(folderUri);
            }
        }

        btnAddFolder.setOnClickListener(v -> {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, Constants.STORAGE_AREA_REQUEST_ID);
        });

        // TODO: Sort underneath

        editDepth = findViewById(R.id.editDepth);
        editColumns = findViewById(R.id.editColumns);
        themeRadioGroup = findViewById(R.id.themeRadioGroup);
        btnSave = findViewById(R.id.btnSave);

        editDepth.setText(String.valueOf(prefs.getInt(Constants.SHARED_PREFS_KEY_DEPTH, 0)));
        editColumns.setText(String.valueOf(prefs.getInt(Constants.SHARED_PREFS_KEY_COLUMNS, 3)));

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
            // Determine max depth
            int depth;
            try {
                depth = Integer.parseInt(editDepth.getText().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid depth", Toast.LENGTH_SHORT).show();
                return;
            }

            // Determine layout images per row
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

            // Apply theme
            AppCompatDelegate.setDefaultNightMode(selectedTheme);

            // Determine use mediastore or not
            boolean useMediastore = checkMediaStore.isChecked();

            // Determine SAF folders
            final StringBuilder sb = new StringBuilder();
            for (Uri uri : safFolders) {
                if (sb.length() > 0) {
                    sb.append(SAF_SEPARATOR);
                }
                sb.append(uri.toString());
            }


            // Save preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Constants.SHARED_PREFS_KEY_DEPTH, depth);
            editor.putInt(Constants.SHARED_PREFS_KEY_COLUMNS, columns);
            editor.putInt(Constants.SHARED_PREFS_KEY_THEME, selectedTheme);
            editor.putBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, useMediastore);
            editor.putString(Constants.SHARED_PREFS_KEY_SAF_FOLDERS, sb.toString());
            editor.apply();

            Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show();

            // Set result and return to main activity
            setResult(RESULT_OK);
            finish();
        });
    }

    @SuppressLint("WrongConstant")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.STORAGE_AREA_REQUEST_ID && resultCode == RESULT_OK && data != null) {
            final Uri folderUri = data.getData();

            if (folderUri != null) {
                safFolders.add(folderUri);
                addFolderTextView(folderUri);

                // Persistent URI permission
                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(folderUri, takeFlags);
            }
        }
    }

    private void addFolderTextView(Uri uri) {
        final TextView tv = new TextView(this);
        tv.setText(uri.getPath());
        tv.setPadding(8, 8, 8, 8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        folderListContainer.addView(tv);
    }
}

