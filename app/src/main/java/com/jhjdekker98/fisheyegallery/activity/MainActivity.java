package com.jhjdekker98.fisheyegallery.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.R;
import com.jhjdekker98.fisheyegallery.config.smb.SmbCredentials;
import com.jhjdekker98.fisheyegallery.model.FileListViewModel;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IMediaIndexer;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.MediaStoreIndexer;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.SmbIndexer;
import com.jhjdekker98.fisheyegallery.security.SecureStorageHelper;
import com.jhjdekker98.fisheyegallery.ui.MediaAdapter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private MediaAdapter adapter;
    private RecyclerView recyclerView;
    private FileListViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load configuration
        final SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        // Set dark mode by default
        final int themeId = prefs.getInt(Constants.SHARED_PREFS_KEY_THEME, AppCompatDelegate.getDefaultNightMode());
        AppCompatDelegate.setDefaultNightMode(themeId);

        // RecyclerView
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recyclerView);

        // Toolbar
        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // ViewModel
        adapter = new MediaAdapter();
        final int columns = prefs.getInt(Constants.SHARED_PREFS_KEY_COLUMNS, 3);
        final GridLayoutManager layoutManager = new GridLayoutManager(this, columns);
        layoutManager.setSpanSizeLookup(new SpanSizeLookup(adapter, columns));

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermissions();
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
                initViewModelAndLoad();
            }
        } else {
            // For older versions of android, just rely on the legacy READ_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Constants.PERMISSION_REQUEST_ID);
            } else {
                initViewModelAndLoad();
            }
        }
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
            final SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            final int columns = prefs.getInt(Constants.SHARED_PREFS_KEY_COLUMNS, 3);
            final GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
            layoutManager.setSpanCount(columns);
            layoutManager.setSpanSizeLookup(new SpanSizeLookup(adapter, columns));
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());

            // TODO: Only update viewModel if scan folders changed
            initViewModelAndLoad();
        }
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
                initViewModelAndLoad();
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

    private void initViewModelAndLoad() {
        if (viewModel == null) {
            viewModel = new ViewModelProvider(this).get(FileListViewModel.class);

            // Observe updates
            viewModel.getGroupedMediaItems().observe(this, grouped -> {
                adapter.submitList(new ArrayList<>(grouped));
            });
        }

        // Build indexers
        final List<IMediaIndexer> indexers = new ArrayList<>();
        final SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        // -- Add MediaStore indexer
        if (prefs.getBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, true)) {
            indexers.add(new MediaStoreIndexer(this));
        }

        // -- Add SMB indexers
        // TODO: See if we can refactor this somewhat and use SmbCredentials.getSmbCredentials
        final SecureStorageHelper ssh = SecureStorageHelper.getInstance(getApplicationContext());
        final String json = ssh.retrieveDecrypted(Constants.SECURE_SHARED_PREFS_KEY_SMB_CONNS);
        if (json != null && !json.isEmpty()) {
            final Type type = new TypeToken<Map<String, SmbCredentials>>() {
            }.getType();
            Map<String, SmbCredentials> credsMap = new Gson().fromJson(json, type);

            final int smbDepth = prefs.getInt(Constants.SHARED_PREFS_KEY_DEPTH, 0);

            for (SmbCredentials creds : credsMap.values()) {
                indexers.add(new SmbIndexer(
                        creds.host,
                        creds.share,
                        creds.username,
                        creds.password,
                        creds.rootPath,
                        smbDepth
                ));
            }
        }

        // Start indexing
        viewModel.loadCacheThenIndex(indexers, () -> getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE));
    }

    private static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
        private final MediaAdapter adapter;
        private final int columns;

        SpanSizeLookup(MediaAdapter adapter, int columns) {
            this.adapter = adapter;
            this.columns = columns;
        }

        @Override
        public int getSpanSize(int i) {
            return adapter.getItemViewType(i) == 0 ? columns : 1;
        }
    }
}
