package com.jhjdekker98.fisheyegallery.config.layout;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.config.ISettingsController;
import com.jhjdekker98.fisheyegallery.ui.IconSettingView;

public class LayoutSettingsController implements ISettingsController {
    private static final int COLUMNS_MAX = 9;
    private final Context context;
    private final IconSettingView columnsSetting;

    private int columns;

    public LayoutSettingsController(Context context, IconSettingView columnsSetting) {
        this.context = context;
        this.columnsSetting = columnsSetting;
    }

    @Override
    public void init() {
        columnsSetting.setOnClickListener(v -> showColumnsDialog());
    }

    @Override
    public void loadFromPrefs(SharedPreferences prefs) {
        columns = prefs.getInt(Constants.SHARED_PREFS_KEY_COLUMNS, 3);
        refreshColumnsSetting();
    }

    @Override
    public void saveToPrefs(SharedPreferences.Editor editor) {
        editor.putInt(Constants.SHARED_PREFS_KEY_COLUMNS, columns);
    }

    private void refreshColumnsSetting() {
        columnsSetting.setSubtitle(columns + " images");
    }

    private void showColumnsDialog() {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setMinEms(4);
        input.setText(String.valueOf(columns));

        final LinearLayout container = new LinearLayout(context);
        container.setPadding(32, 16, 32, 16);
        container.addView(input);

        new AlertDialog.Builder(context)
                .setTitle("Set images per row")
                .setView(container)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        final int newVal = Integer.parseInt(input.getText().toString());
                        if (newVal <= 0 || newVal > COLUMNS_MAX) {
                            throw new IllegalArgumentException("Column count should be between 1 and 9");
                        }
                        columns = newVal;
                        refreshColumnsSetting();
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Must be a number", Toast.LENGTH_SHORT).show();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(context, "Must be between 1 and " + COLUMNS_MAX, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
