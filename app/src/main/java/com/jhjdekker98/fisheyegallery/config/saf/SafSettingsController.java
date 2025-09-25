package com.jhjdekker98.fisheyegallery.config.saf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.config.ISettingsController;
import com.jhjdekker98.fisheyegallery.ui.IconSettingView;
import java.util.ArrayList;
import java.util.List;

public class SafSettingsController implements ISettingsController {
    private final Context context;
    private final CheckBox checkMediaStore;
    private final LinearLayout folderListContainer;
    private final Button btnAddFolder;
    private final IconSettingView depthSetting;

    private final List<Uri> safFolders = new ArrayList<>();
    private boolean useMediaStore;
    private int depth;

    public SafSettingsController(Context context,
                                 CheckBox checkMediaStore,
                                 LinearLayout folderListContainer,
                                 Button btnAddFolder,
                                 IconSettingView depthSetting) {
        this.context = context;
        this.checkMediaStore = checkMediaStore;
        this.folderListContainer = folderListContainer;
        this.btnAddFolder = btnAddFolder;
        this.depthSetting = depthSetting;
    }

    @Override
    public void init() {
        checkMediaStore.setOnCheckedChangeListener((btn, checked) -> useMediaStore = checked);

        btnAddFolder.setOnClickListener(v -> {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            ((Activity) context).startActivityForResult(intent, Constants.STORAGE_AREA_REQUEST_ID);
        });

        depthSetting.setOnClickListener(v -> showDepthDialog());
    }

    @Override
    public void loadFromPrefs(SharedPreferences prefs) {
        // MediaStore
        useMediaStore = prefs.getBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, true);
        checkMediaStore.setChecked(useMediaStore);

        // SAF folders
        final String savedFolders = prefs.getString(Constants.SHARED_PREFS_KEY_SAF_FOLDERS, "");
        safFolders.clear();
        folderListContainer.removeAllViews();

        if (!savedFolders.isEmpty()) {
            for (String uriString : savedFolders.split(Constants.SAF_SEPARATOR)) {
                final Uri folderUri = Uri.parse(uriString);
                safFolders.add(folderUri);
                addFolderTextView(folderUri);
            }
        }

        // Scan depth
        depth = prefs.getInt(Constants.SHARED_PREFS_KEY_DEPTH, 0);
        refreshDepthSetting();
    }

    @Override
    public void saveToPrefs(SharedPreferences.Editor editor) {
        // MediaStore
        editor.putBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, useMediaStore);

        // SAF folders
        final StringBuilder sb = new StringBuilder();
        for (Uri uri : safFolders) {
            if (sb.length() > 0) {
                sb.append(Constants.SAF_SEPARATOR);
            }
            sb.append(uri.toString());
        }
        editor.putString(Constants.SHARED_PREFS_KEY_SAF_FOLDERS, sb.toString());

        // Scan depth
        editor.putInt(Constants.SHARED_PREFS_KEY_DEPTH, depth);
    }

    private void addFolderTextView(Uri uri) {
        // Container
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.setPadding(0, 4, 0, 4);

        // Folder path TextView
        final TextView tv = new TextView(context);
        tv.setText(uri.getPath());
        tv.setPadding(8, 8, 8, 8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        tv.setGravity(RelativeLayout.CENTER_VERTICAL);

        // Delete button
        final ImageButton btnDelete = new ImageButton(context);
        btnDelete.setImageResource(R.drawable.md_close_24px);
        btnDelete.setBackgroundResource(0);
        btnDelete.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnDelete.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        btnDelete.setForegroundGravity(RelativeLayout.CENTER_VERTICAL);
        btnDelete.setOnClickListener(v -> {
            showDeleteSafFolderDialog(container, uri);
        });

        // Add to list container
        container.addView(tv);
        container.addView(btnDelete);
        folderListContainer.addView(container);
    }

    public void handleFolderResult(Uri folderUri, int takeFlags) {
        if (folderUri == null) {
            return;
        }
        safFolders.add(folderUri);
        addFolderTextView(folderUri);
        context.getContentResolver().takePersistableUriPermission(folderUri, takeFlags);
    }

    private void refreshDepthSetting() {
        final String subtitleText;
        if (depth == 0) {
            subtitleText = "Unlimited";
        } else if (depth == 1) {
            subtitleText = depth + " folder";
        } else {
            subtitleText = depth + " folders";
        }
        depthSetting.setSubtitle(subtitleText);
    }

    private void showDepthDialog() {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setMinEms(4);
        input.setText(String.valueOf(depth));

        final LinearLayout container = new LinearLayout(context);
        container.setPadding(32, 16, 32, 16);
        container.addView(input);

        new AlertDialog.Builder(context)
                .setTitle("Set scan depth")
                .setMessage("0 = unlimited")
                .setView(container)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        int newDepth = Integer.parseInt(input.getText().toString());
                        if (newDepth < 0) {
                            throw new IllegalArgumentException();
                        }
                        depth = newDepth;
                        refreshDepthSetting();
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Must be a number", Toast.LENGTH_SHORT).show();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(context, "Must be >= 0", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteSafFolderDialog(LinearLayout container, Uri uri) {
        new AlertDialog.Builder(context)
                .setTitle("Remove folder")
                .setMessage("Stop tracking " + uri.getPath() + "?")
                .setPositiveButton("OK", (dialog, which) -> {
                    folderListContainer.removeView(container);
                    safFolders.remove(uri);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
