package com.jhjdekker98.fisheyegallery.config.smb;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.config.ISettingsController;
import com.jhjdekker98.fisheyegallery.security.SecureStorageHelper;
import com.jhjdekker98.fisheyegallery.ui.IconSettingView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SmbSettingsController implements ISettingsController {

    private final Context context;
    private final LinearLayout folderListContainer;
    private final Button btnAddFolder;
    private final IconSettingView depthSetting;

    private final List<SmbCredentials> smbCredsList = new ArrayList<>();
    private int depth;

    public SmbSettingsController(Context context,
                                 LinearLayout folderListContainer,
                                 Button btnAddFolder,
                                 IconSettingView depthSetting) {
        this.context = context;
        this.folderListContainer = folderListContainer;
        this.btnAddFolder = btnAddFolder;
        this.depthSetting = depthSetting;
    }

    @Override
    public void init() {
        btnAddFolder.setOnClickListener(v -> showAddSmbDialog());
        depthSetting.setOnClickListener(v -> showDepthDialog());
    }

    @Override
    public void loadFromPrefs(SharedPreferences prefs) {
        // Folders
        smbCredsList.clear();
        folderListContainer.removeAllViews();

        final SecureStorageHelper ssh = SecureStorageHelper.getInstance(context.getApplicationContext());
        final Map<String, SmbCredentials> savedCreds = SmbCredentials.getSmbCredentials(ssh);
        if (savedCreds != null) {
            for (SmbCredentials creds : savedCreds.values()) {
                smbCredsList.add(creds);
                addFolderTextView(creds);
            }
        }

        // Scan depth
        depth = prefs.getInt(Constants.SHARED_PREFS_KEY_DEPTH, 0);
        refreshDepthSetting();
    }

    @Override
    public void saveToPrefs(SharedPreferences.Editor editor) {
        // Save depth as before
        editor.putInt(Constants.SHARED_PREFS_KEY_DEPTH, depth);

        // Save SMB credentials
        final SecureStorageHelper ssh = SecureStorageHelper.getInstance(context.getApplicationContext());
        for (SmbCredentials creds : smbCredsList) {
            SmbCredentials.saveSmbCredentials(ssh, creds);
        }
    }

    private void showAddSmbDialog() {
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 16, 32, 16);

        final EditText hostInput = new EditText(context);
        hostInput.setHint("Host (e.g., 192.168.1.12)");
        container.addView(hostInput);

        final EditText shareInput = new EditText(context);
        shareInput.setHint("Share name");
        container.addView(shareInput);

        final EditText userInput = new EditText(context);
        userInput.setHint("Username");
        container.addView(userInput);

        final EditText passInput = new EditText(context);
        passInput.setHint("Password");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        container.addView(passInput);

        final EditText rootPathInput = new EditText(context);
        rootPathInput.setHint("Root path");
        container.addView(rootPathInput);

        new AlertDialog.Builder(context)
                .setTitle("Add SMB folder")
                .setView(container)
                .setPositiveButton("Add", (dialog, which) -> {
                    String host = hostInput.getText().toString().trim();
                    String share = shareInput.getText().toString().trim();
                    String user = userInput.getText().toString().trim();
                    String pass = passInput.getText().toString();
                    String rootPath = rootPathInput.getText().toString();

                    if (!host.isEmpty() && !share.isEmpty()) {
                        final SmbCredentials creds = new SmbCredentials(host, share, user, pass, rootPath);
                        smbCredsList.add(creds);
                        addFolderTextView(creds);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addFolderTextView(SmbCredentials creds) {
        String display = smbCredentialsToFolderString(creds);

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.setPadding(0, 4, 0, 4);

        final TextView tv = new TextView(context);
        tv.setText(display);
        tv.setPadding(8, 8, 8, 8);
        tv.setTextSize(16);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        final ImageButton btnDelete = new ImageButton(context);
        btnDelete.setImageResource(R.drawable.md_close_24px);
        btnDelete.setBackgroundResource(0);
        btnDelete.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        btnDelete.setOnClickListener(v -> {
            folderListContainer.removeView(container);
            smbCredsList.remove(creds);
        });

        container.addView(tv);
        container.addView(btnDelete);
        folderListContainer.addView(container);
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
                .setTitle("Set SMB scan depth")
                .setMessage("0 = unlimited")
                .setView(container)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        int newDepth = Integer.parseInt(input.getText().toString());
                        if (newDepth < 0) throw new IllegalArgumentException();
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

    private void refreshDepthSetting() {
        final String subtitleText;
        if (depth == 0) subtitleText = "Unlimited";
        else if (depth == 1) subtitleText = "1 folder";
        else subtitleText = depth + " folders";
        depthSetting.setSubtitle(subtitleText);
    }

    private String smbCredentialsToFolderString(SmbCredentials creds) {
        return creds.username + "@" + creds.host + "/" + creds.share;
    }
}
