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
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IMediaIndexer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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


    public FileListViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<GalleryItem>> getGroupedMediaItems() {
        return groupedMediaLive;
    }

    public synchronized void startIndexing(List<IMediaIndexer> indexers) {
        Log.d(TAG, "startIndexing(): requested " + (indexers == null ? 0 : indexers.size()) + " indexers");

        // stop previous run (if any)
        stopIndexing();

        // clear current set and notify UI (empty state)
        uriMap.clear();
        groupedMap.clear();
        groupedMediaLive.postValue(new ArrayList<>());

        if (indexers == null || indexers.isEmpty()) {
            return;
        }

        activeIndexers.addAll(indexers);

        // For each indexer, start it and merge its callbacks
        for (IMediaIndexer idx : indexers) {
            idx.startIndexing(new IMediaIndexer.Callback() {
                @Override
                public void onMediaFound(List<Uri> newUris) {
                    if (newUris == null || newUris.isEmpty()) return;

                    // Merge deduplicated on background thread to avoid contention on main thread
                    executor.execute(() -> {
                        boolean anyAdded = false;
                        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        final List<GalleryItem> batchForUI = new ArrayList<>();

                        synchronized (uriMap) {
                            for (Uri u : newUris) {
                                if (u == null) continue;
                                String key = normalizeKey(u);
                                if (!uriMap.containsKey(key)) {
                                    uriMap.put(key, u);
                                    anyAdded = true;

                                    // Determine day grouping
                                    long fileDate = getFileDate(u);
                                    String dayKey = sdf.format(new Date(fileDate));

                                    List<GalleryItem.Image> dayList = groupedMap.computeIfAbsent(dayKey, k -> new ArrayList<>());
                                    GalleryItem.Image imageItem = new GalleryItem.Image(u);
                                    dayList.add(imageItem);

                                    // Add header only if first image for the day
                                    if (dayList.size() == 1) {
                                        batchForUI.add(new GalleryItem.Header(dayKey));
                                    }
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
                            });
                        }
                    });
                }

                @Override
                public void onComplete() {
                    // indexer finished; remove from active list
                    synchronized (FileListViewModel.this) {
                        activeIndexers.remove(idx);
                    }
                    Log.d(TAG, "Indexer complete: " + idx);
                }
            });
        }
    }

    /**
     * Stop any running indexers (asks them to cancel). Does not clear LiveData by itself.
     */
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
