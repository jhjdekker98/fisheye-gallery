package com.jhjdekker98.fisheyegallery.model;

import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IMediaIndexer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListViewModel extends AndroidViewModel {
    private static final String TAG = "FileListViewModel";
    private final MutableLiveData<List<GalleryItem>> groupedMediaLive = new MutableLiveData<>(new ArrayList<>());
    private final LinkedHashMap<String, Uri> uriMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<GalleryItem.Image>> groupedMap = new LinkedHashMap<>();
    private final List<IMediaIndexer> activeIndexers = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final File cacheFile;


    public FileListViewModel(@NonNull Application application) {
        super(application);
        cacheFile = new File(application.getFilesDir(), "media_cache.json");
    }

    public LiveData<List<GalleryItem>> getGroupedMediaItems() {
        return groupedMediaLive;
    }

    /**
     * Load cached URIs first, post to UI, then start indexers.
     */
    public void loadCacheThenIndex(List<IMediaIndexer> indexers) {
        synchronized (uriMap) {
            uriMap.clear();
            groupedMap.clear();
        }
        groupedMediaLive.postValue(new ArrayList<>());

        executor.execute(() -> {
            // Setup params
            final Map<String, String> cacheMap = loadCache();
            final List<Uri> urisToProcess = new ArrayList<>();

            // Rebuild uriMap from cache
            final Iterator<Map.Entry<String, String>> it = cacheMap.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<String, String> entry = it.next();
                final Uri uri = Uri.parse(entry.getValue());
                if (checkUriExists(uri)) {
                    urisToProcess.add(uri);
                } else {
                    it.remove(); // stale
                }
            }

            // Post-process cached URIs
            processNewUris(urisToProcess, true);

            // Save cleaned cache
            saveCache(cacheMap);

            // Start indexing after cache
            startIndexing(indexers);
        });
    }

    /**
     * Central method to post-process a batch of new URIs:
     * - Adds to groupedMap
     * - Adds headers if needed
     * - Updates groupedMediaLive
     * - Updates cache
     */
    private void processNewUris(List<Uri> newUris, boolean forcePost) {
        if (newUris == null || newUris.isEmpty()) return;

        executor.execute(() -> {
            boolean anyAdded = forcePost;
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            final List<GalleryItem> batchForUI = new ArrayList<>();

            synchronized (uriMap) {

                if (forcePost) {
                    Log.d("FileListViewModel", "size: " + uriMap.size());
                }

                for (Uri u : newUris) {
                    if (u == null) continue;
                    String key = normalizeKey(u);
                    if (!uriMap.containsKey(key)) {
                        uriMap.put(key, u);
                        anyAdded = true;

                        long fileDate = getFileDate(u);
                        String dayKey = sdf.format(new Date(fileDate));
                        List<GalleryItem.Image> dayList = groupedMap.computeIfAbsent(dayKey, k -> new ArrayList<>());
                        GalleryItem.Image imageItem = new GalleryItem.Image(u);
                        dayList.add(imageItem);

                        if (dayList.size() == 1) batchForUI.add(new GalleryItem.Header(dayKey));
                        batchForUI.add(imageItem);
                    }
                }
            }

            if (anyAdded) {
                mainHandler.post(() -> {
                    List<GalleryItem> current = groupedMediaLive.getValue();
                    if (current == null) current = new ArrayList<>();
                    current.addAll(batchForUI);
                    groupedMediaLive.setValue(current);

                    // Update cache
                    LinkedHashMap<String, String> cacheMap = new LinkedHashMap<>();
                    for (Map.Entry<String, Uri> entry : uriMap.entrySet()) {
                        cacheMap.put(entry.getKey(), entry.getValue().toString());
                    }
                    saveCache(cacheMap);
                });
            }
        });
    }

    private boolean checkUriExists(Uri uri) {
        try {
            if ("file".equals(uri.getScheme())) return new File(uri.getPath()).exists();
            if ("content".equals(uri.getScheme())) {
                DocumentFile df = DocumentFile.fromSingleUri(getApplication(), uri);
                return df != null && df.exists();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Cache load/save ---
    private Map<String, String> loadCache() {
        if (!cacheFile.exists()) return new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            Type type = new TypeToken<LinkedHashMap<String, String>>() {
            }.getType();
            return new Gson().fromJson(sb.toString(), type);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load cache", e);
            return new LinkedHashMap<>();
        }
    }

    private void saveCache(Map<String, String> cacheMap) {
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            String json = new Gson().toJson(cacheMap);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "Failed to save cache", e);
        }
    }

    // --- Existing indexing logic ---
    private synchronized void startIndexing(List<IMediaIndexer> indexers) {
        Log.d(TAG, "startIndexing(): requested " + (indexers == null ? 0 : indexers.size()) + " indexers");

        // Stop running indexers
        stopIndexing();

        if (indexers == null || indexers.isEmpty()) return;
        activeIndexers.addAll(indexers);

        for (IMediaIndexer idx : indexers) {
            idx.startIndexing(new IMediaIndexer.Callback() {
                @Override
                public void onMediaFound(List<Uri> newUris) {
                    processNewUris(newUris, false);
                }

                @Override
                public void onComplete() {
                    synchronized (FileListViewModel.this) {
                        activeIndexers.remove(idx);
                    }
                    Log.d(TAG, "Indexer complete: " + idx);
                }
            });
        }
    }

    public synchronized void stopIndexing() {
        if (!activeIndexers.isEmpty()) {
            Log.d(TAG, "stopIndexing(): cancelling " + activeIndexers.size() + " indexers");
        }
        for (IMediaIndexer idx : activeIndexers) {
            try {
                idx.stop();
            } catch (Exception e) {
                Log.w(TAG, "stopIndexing: exception stopping indexer", e);
            }
        }
        activeIndexers.clear();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopIndexing();
        executor.shutdownNow();
    }

    /**
     * Simple normalization to deduplicate results of multiple indexers
     */
    private String normalizeKey(Uri uri) {
        if (uri == null) return "";

        final ContentResolver resolver = getApplication().getContentResolver();

        if ("media".equals(uri.getAuthority())) {
            try (Cursor c = resolver.query(uri,
                    new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    final String name = c.getString(0);
                    final String path = c.getString(1) == null ? "" : c.getString(1);
                    return path + name;
                }
            } catch (NullPointerException e) {
                return uri.toString();
            }
        }

        if (uri.getAuthority() == null) return uri.toString();

        if (DocumentsContract.isDocumentUri(getApplication(), uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            if (docId != null && docId.startsWith("primary:")) {
                return docId.substring("primary:".length());
            } else {
                return docId != null ? docId : uri.toString();
            }
        }

        return uri.toString();
    }

    private long getFileDate(Uri uri) {
        final ContentResolver resolver = getApplication().getContentResolver();

        // Try to get MediaStore metadata time first
        if (uri.getAuthority().equals("media")) {
            try (Cursor cursor = resolver.query(
                    uri,
                    new String[]{MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long dateTaken = cursor.getLong(0);
                    long dateModified = cursor.getLong(1);
                    return dateTaken > 0 ? dateTaken : dateModified * 1000; // DATE_MODIFIED is in seconds
                }
            }
        }

        // Try to get SAF metadata lastModified time
        final DocumentFile file = DocumentFile.fromSingleUri(getApplication(), uri);
        if (file != null && file.exists()) {
            return file.lastModified();
        }

        // Fallback to current time
        return System.currentTimeMillis();
    }
}
