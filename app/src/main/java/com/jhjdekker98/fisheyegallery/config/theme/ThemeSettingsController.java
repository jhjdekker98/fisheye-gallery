package com.jhjdekker98.fisheyegallery.config.theme;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.config.ISettingsController;
import com.jhjdekker98.fisheyegallery.ui.IconSettingView;
import java.util.ArrayList;

public class ThemeSettingsController implements ISettingsController {
    private final Context context;
    private final IconSettingView themeSelect;
    private int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

    public ThemeSettingsController(Context context, IconSettingView themeSelect) {
        this.context = context;
        this.themeSelect = themeSelect;
    }

    @Override
    public void init() {
        themeSelect.setOnClickListener(v -> showThemeDialog());
    }

    @Override
    public void loadFromPrefs(SharedPreferences prefs) {
        selectedTheme = prefs.getInt(
                Constants.SHARED_PREFS_KEY_THEME,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        refreshSubtitle();
    }

    @Override
    public void saveToPrefs(SharedPreferences.Editor editor) {
        editor.putInt(Constants.SHARED_PREFS_KEY_THEME, selectedTheme);
        AppCompatDelegate.setDefaultNightMode(selectedTheme);
    }

    private void showThemeDialog() {
        final int prevTheme = selectedTheme;
        final String[] optionLabels = Constants.THEME_LOOKUP.values().toArray(new String[0]);
        final ArrayList<Integer> keyArr = new ArrayList<>(Constants.THEME_LOOKUP.keySet());
        final int checkedIndex = keyArr.indexOf(selectedTheme);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Choose theme");

        builder.setSingleChoiceItems(optionLabels, checkedIndex, (dialog, which) -> {
            selectedTheme = keyArr.get(which);
        });

        builder.setPositiveButton("OK", (dialog, which) -> {
            refreshSubtitle();
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            selectedTheme = prevTheme;
            refreshSubtitle();
            dialog.dismiss();
        });

        builder.show();
    }

    private void refreshSubtitle() {
        themeSelect.setSubtitle(Constants.THEME_LOOKUP.getOrDefault(selectedTheme, "ERR"));
    }
}
