package com.jhjdekker98.fisheyegallery.config.mediastore;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AlertDialog;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.config.ISettingsController;
import com.jhjdekker98.fisheyegallery.ui.IconSettingView;

public class MediaStoreSettingsController implements ISettingsController {

    private final Context context;
    private final IconSettingView iconSettingView;
    private boolean useMediaStore;

    public MediaStoreSettingsController(Context context, IconSettingView iconSettingView) {
        this.context = context;
        this.iconSettingView = iconSettingView;
    }

    @Override
    public void init() {
        iconSettingView.setOnClickListener(v -> showMediaStoreDialog());
    }

    @Override
    public void loadFromPrefs(SharedPreferences prefs) {
        useMediaStore = prefs.getBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, true);
        updateSubtitle();
    }

    @Override
    public void saveToPrefs(SharedPreferences.Editor editor) {
        editor.putBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, useMediaStore);
    }

    private void updateSubtitle() {
        String subtitle = useMediaStore ? "Display MediaStore files" : "Hide MediaStore files";
        iconSettingView.setSubtitle(subtitle);
    }

    private void showMediaStoreDialog() {
        final String[] options = {"Yes", "No"};
        int checkedItem = useMediaStore ? 0 : 1;

        new AlertDialog.Builder(context)
                .setTitle("Display MediaStore files?")
                .setSingleChoiceItems(options, checkedItem, null)
                .setPositiveButton("OK", (dialog, which) -> {
                    int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    useMediaStore = (selectedPosition == 0);

                    // Save preference
                    SharedPreferences prefs = context.getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, useMediaStore).apply();

                    // Update subtitle
                    updateSubtitle();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
