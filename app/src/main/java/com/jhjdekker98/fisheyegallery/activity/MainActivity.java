package com.jhjdekker98.fisheyegallery.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.adapter.ImageAdapter;
import com.jhjdekker98.fisheyegallery.model.FileEntry;
import com.jhjdekker98.fisheyegallery.util.FileUtil;
import com.jhjdekker98.fisheyegallery.util.ThumbnailManager;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ImageAdapter imageAdapter;
    private RecyclerView recyclerView;
    private ThumbnailManager thumbnailManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load configuration
        final SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        // Set dark mode by default
        final int themeId = prefs.getInt(Constants.SHARED_PREFS_KEY_THEME, AppCompatDelegate.getDefaultNightMode());
        AppCompatDelegate.setDefaultNightMode(themeId);

        // Set view to Main Activity layout on startup
        setContentView(R.layout.activity_main);

        // Toolbar
        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // RecyclerView
        int columns = prefs.getInt(Constants.SHARED_PREFS_KEY_COLUMNS, 3);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, columns));

        // Thumbnails
        thumbnailManager = new ThumbnailManager(this, prefs.getInt(Constants.SHARED_PREFS_KEY_THUMB_TTL, 7));
        thumbnailManager.cleanup();

        // Load files into RecyclerView
        loadFiles();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_config) {
            final Intent intent = new Intent(this, ConfigActivity.class);
            startActivityForResult(intent, Constants.CONFIG_REQUEST_ID);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.CONFIG_REQUEST_ID && resultCode == RESULT_OK) {
            loadFiles();
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            final String[] permissions = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };

            boolean needsRequest = false;
            for (String perm : permissions) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    needsRequest = true;
                    break;
                }
            }

            if (needsRequest) {
                ActivityCompat.requestPermissions(this, permissions, Constants.PERMISSION_REQUEST_ID);
            } else {
                loadFiles();
            }
        } else {
            // For older versions of android, just rely on the legacy READ_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Constants.PERMISSION_REQUEST_ID);
            } else {
                loadFiles();
            }
        }
    }

    private void loadFiles() {
        final SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        final String folderUriString = prefs.getString(Constants.SHARED_PREFS_KEY_FOLDER, null);
        int maxDepth = prefs.getInt(Constants.SHARED_PREFS_KEY_DEPTH, 0);
        int columns = prefs.getInt(Constants.SHARED_PREFS_KEY_COLUMNS, 3);

        if (folderUriString == null) {
            Toast.makeText(this, "No folder selected. Please configure a folder.", Toast.LENGTH_SHORT).show();
            return;
        }

        final Uri folderUri = Uri.parse(folderUriString);
        final DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);

        final List<FileEntry> imageEntries = new ArrayList<>();

        if (folder != null && folder.exists() && folder.isDirectory()) {
            // Walk folder tree and collect image/video files
            imageEntries.addAll(FileUtil.walkDocumentTree(folder, 0, maxDepth));
        } else {
            Toast.makeText(this, "Folder does not exist or cannot be accessed.", Toast.LENGTH_SHORT).show();
        }

        // Set up RecyclerView adapter
        imageAdapter = new ImageAdapter(this, imageEntries, thumbnailManager);
        recyclerView.setAdapter(imageAdapter);

        // Update layout manager if columns changed in config
        recyclerView.setLayoutManager(new GridLayoutManager(this, columns));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == Constants.PERMISSION_REQUEST_ID) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                loadFiles();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage("Media access permission is required to run this app. You may need to enable this in the system app settings.")
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
        }
    }
}
