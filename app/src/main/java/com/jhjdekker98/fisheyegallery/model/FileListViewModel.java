package com.jhjdekker98.fisheyegallery.model;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IMediaIndexer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListViewModel extends AndroidViewModel {
    private static final String TAG = "FileListViewModel";
    private final MutableLiveData<List<Uri>> mediaUrisLive = new MutableLiveData<>(new ArrayList<>());
    private final LinkedHashSet<String> uriSet = new LinkedHashSet<>();
    private final List<IMediaIndexer> activeIndexers = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    public FileListViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<Uri>> getMediaUris() {
        return mediaUrisLive;
    }

    /**
     * Starts indexing using the supplied indexers. This will:
     * - stop any currently running indexers,
     * - clear the existing canonical set,
     * - start each indexer and merge batches deduplicated.
     */
    public synchronized void startIndexing(List<IMediaIndexer> indexers) {
        Log.d(TAG, "startIndexing(): requested " + (indexers == null ? 0 : indexers.size()) + " indexers");

        // stop previous run (if any)
        stopIndexing();

        // clear current set and notify UI (empty state)
        uriSet.clear();
        mediaUrisLive.postValue(new ArrayList<>());

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
                        List<Uri> addedThisBatch = new ArrayList<>();

                        synchronized (uriSet) {
                            for (Uri u : newUris) {
                                if (u == null) continue;
                                String key = normalizeKey(u);
                                if (!uriSet.contains(key)) {
                                    uriSet.add(key);
                                    addedThisBatch.add(u);
                                    anyAdded = true;
                                }
                            }
                        }

                        if (anyAdded) {
                            // Build snapshot list in insertion order
                            List<Uri> snapshot = new ArrayList<>();
                            synchronized (uriSet) {
                                for (String s : uriSet) {
                                    snapshot.add(Uri.parse(s));
                                }
                            }
                            Log.d(TAG, "onMediaFound(): added " + addedThisBatch.size() + " new items, total=" + snapshot.size());
                            mainHandler.post(() -> mediaUrisLive.setValue(snapshot));
                        } else {
                            Log.d(TAG, "onMediaFound(): 0 new (all duplicates)");
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
     * Simple normalization: use the exact uri.toString() as the key.
     * You can improve this if you want to collapse equivalent URIs from different sources.
     */
    private String normalizeKey(Uri uri) {
        // using uri.toString() is OK for most cases; you can add extra normalization if needed
        return Objects.requireNonNull(uri).toString();
    }

    public LiveData<List<GalleryItem>> getGroupedMediaItems() {
        final MutableLiveData<List<GalleryItem>> groupedLive = new MutableLiveData<>();
        getMediaUris().observeForever(uris -> {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            final Map<String, List<Uri>> grouped = new LinkedHashMap<>();

            for (Uri uri : uris) {
                final String dayKey = sdf.format(new Date(getFileDate(uri)));
                grouped.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(uri);
            }

            final List<GalleryItem> flattened = new ArrayList<>();
            for (Map.Entry<String, List<Uri>> entry : grouped.entrySet()) {
                flattened.add(new GalleryItem.Header(entry.getKey()));
                for (Uri u : entry.getValue()) {
                    flattened.add(new GalleryItem.Image(u));
                }
            }

            groupedLive.setValue(flattened);
        });
        return groupedLive;
    }

    private long getFileDate(Uri uri) {
        // Try to get MediaStore metadata time first
        try (Cursor cursor = getApplication().getContentResolver().query(
                uri,
                new String[]{MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long dateTaken = cursor.getLong(0);
                long dateModified = cursor.getLong(1);
                return dateTaken > 0 ? dateTaken : dateModified * 1000; // DATE_MODIFIED is in seconds
            }
        }
        // Fallback to current time
        return System.currentTimeMillis();
    }
}
