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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListViewModel extends AndroidViewModel {
    private static final String TAG = "FileListViewModel";
    private final MutableLiveData<List<Uri>> mediaUrisLive = new MutableLiveData<>(new ArrayList<>());
    private final LinkedHashMap<String, Uri> uriMap = new LinkedHashMap<>();
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
        uriMap.clear();
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

                        synchronized (uriMap) {
                            for (Uri u : newUris) {
                                if (u == null) continue;
                                String key = normalizeKey(u);
                                if (!uriMap.containsKey(key)) {
                                    uriMap.put(key, u);
                                    addedThisBatch.add(u);
                                    anyAdded = true;
                                }
                            }
                        }

                        if (anyAdded) {
                            // Build snapshot list in insertion order
                            List<Uri> snapshot;
                            synchronized (uriMap) {
                                snapshot = new ArrayList<>(uriMap.values());
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
     * Simple normalization to deduplicate results of multiple indexers
     */
    private String normalizeKey(Uri uri) {
        if (uri == null) {
            return "";
        }

        // MediaStore URI
        final ContentResolver resolver = getApplication().getContentResolver();
        if (uri.getAuthority().equals("media")) {
            try (Cursor c = resolver.query(uri,
                    new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    final String name = c.getString(0);
                    final String path = c.getString(1) == null ? "" : c.getString(1);
                    return path + name;
                }
            } catch (NullPointerException e) {
                return uri.toString(); // fallback
            }
        }

        final String authority = uri.getAuthority();
        if (authority == null) {
            return Objects.requireNonNull(uri).toString();
        }

        // SAF URI
        if (DocumentsContract.isDocumentUri(getApplication(), uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            if (docId != null && docId.startsWith("primary:")) {
                return docId.substring("primary:".length());
            } else {
                return docId != null ? docId : uri.toString();
            }
        }

        return Objects.requireNonNull(uri).toString();
    }

    public LiveData<List<GalleryItem>> getGroupedMediaItems() {
        final MutableLiveData<List<GalleryItem>> groupedLive = new MutableLiveData<>();
        getMediaUris().observeForever(uris -> {
            executor.execute(() -> {
                final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                final Map<String, List<Uri>> grouped = new HashMap<>();
                final Map<Uri, Long> fileDates = new HashMap<>();

                // Collect dates and group URIs by day
                for (Uri uri : uris) {
                    final long fileDate = getFileDate(uri);
                    fileDates.put(uri, fileDate);

                    final String dayKey = sdf.format(new Date(fileDate));
                    grouped.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(uri);
                }

                // Sort day keys in descending order
                final List<String> sortedDays = new ArrayList<>(grouped.keySet());
                sortedDays.sort((d1, d2) -> {
                    try {
                        Date date1 = sdf.parse(d1);
                        Date date2 = sdf.parse(d2);
                        return date2.compareTo(date1); // newest day first
                    } catch (ParseException e) {
                        return 0;
                    }
                });

                // Flatten grouped data
                final List<GalleryItem> flattened = new ArrayList<>();
                for (String dayKey : sortedDays) {
                    String headerContent = dayKey;
                    try {
                        final Date date = sdf.parse(dayKey);
                        final Calendar cal = Calendar.getInstance();
                        final int currYear = cal.get(Calendar.YEAR);
                        cal.setTime(date);
                        if (cal.get(Calendar.YEAR) == currYear) {
                            final SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
                            headerContent = dayFormat.format(date);
                        }
                    } catch (ParseException e) {
                        // TODO: Error handling?
                    }
                    flattened.add(new GalleryItem.Header(headerContent));

                    grouped.get(dayKey).stream()
                            .sorted(Comparator.comparingLong(fileDates::get).reversed())
                            .forEach(u -> flattened.add(new GalleryItem.Image(u)));
                }

                // Post result back to main thread
                mainHandler.post(() -> groupedLive.setValue(flattened));
            });
        });

        return groupedLive;
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
