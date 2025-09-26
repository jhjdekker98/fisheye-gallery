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
import com.jhjdekker98.fisheyegallery.model.mediacache.MediaCacheItem;
import com.jhjdekker98.fisheyegallery.model.mediacache.MediaCacheRepository;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IMediaIndexer;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListViewModel extends AndroidViewModel {
    private static final int CACHE_BATCH_SIZE = 100;

    private final MutableLiveData<List<GalleryItem>> groupedMediaLive = new MutableLiveData<>(new ArrayList<>());
    private final LinkedHashMap<String, Uri> uriMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<GalleryItem.Image>> groupedMap = new LinkedHashMap<>();
    private final List<IMediaIndexer> activeIndexers = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final MediaCacheRepository cacheRepo;
    private ExecutorService executor;

    public FileListViewModel(@NonNull Application application) {
        super(application);
        Log.d("FileListViewModel", "recreating cacherepo");
        this.cacheRepo = new MediaCacheRepository(application);
    }

    public LiveData<List<GalleryItem>> getGroupedMediaItems() {
        return groupedMediaLive;
    }

    /**
     * Load cached URIs in batches, post to UI, then start indexers.
     */
    public void loadCacheThenIndex(List<IMediaIndexer> indexers) {
        Log.d("FileListViewModel", "loadCacheThenIndex");

        stopIndexing(); // cancel any running indexers
        if (executor != null) {
            executor.shutdownNow(); // cancel any previously running tasks
        }
        executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            int skip = 0;
            final List<MediaCacheItem> batch = new ArrayList<>();

            do {
                final CountDownLatch latch = new CountDownLatch(1);

                cacheRepo.queryCache(skip, CACHE_BATCH_SIZE, items -> {
                    final List<MediaCacheItem> validItems = new ArrayList<>();
                    final List<MediaCacheItem> staleItems = new ArrayList<>();
                    items.forEach(mci -> {
                        boolean fileExists = checkUriExists(Uri.parse(mci.uri));
                        (fileExists ? validItems : staleItems).add(mci);
                    });
                    Log.d("FileListViewModel", "Posting " + validItems.size() + " items to view");
                    processNewCacheItems(validItems, true);
                    batch.clear();
                    batch.addAll(validItems);
                    if (!staleItems.isEmpty()) {
                        cacheRepo.deleteFromCache(staleItems);
                    }
                    latch.countDown();
                });

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                skip += CACHE_BATCH_SIZE;
            } while (!batch.isEmpty());

            startIndexing(indexers);
        });
    }

    /**
     * Process cache items or newly indexed items into UI and uriMap.
     */
    private void processNewCacheItems(List<MediaCacheItem> items, boolean forcePost) {
        if (items == null || items.isEmpty()) return;

        boolean anyAdded = forcePost;
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        final List<GalleryItem> batchForUI = new ArrayList<>();

        synchronized (uriMap) {
            for (MediaCacheItem item : items) {
                Uri u = Uri.parse(item.uri);
                if (!uriMap.containsKey(item.key)) {
                    uriMap.put(item.key, u);
                    anyAdded = true;

                    String dayKey = sdf.format(new Date(item.lastModified));
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
            });
        }
    }

    /**
     * Called by indexers when new media is found.
     */
    private void processNewUris(List<Uri> newUris, boolean forcePost) {
        if (newUris == null || newUris.isEmpty()) return;

        final List<MediaCacheItem> cacheItems = new ArrayList<>();

        for (Uri uri : newUris) {
            String key = normalizeKey(uri);
            cacheItems.add(new MediaCacheItem(
                    key,
                    uri.toString(),
                    null, // TODO: Implement album name
                    getFileDate(uri)));
        }

        processNewCacheItems(cacheItems, forcePost);

        // Persist new items to Room
        cacheRepo.updateCache(cacheItems);
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

    // --- Indexing logic ---
    private synchronized void startIndexing(List<IMediaIndexer> indexers) {
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
                }
            });
        }
    }

    public synchronized void stopIndexing() {
        for (IMediaIndexer idx : activeIndexers) {
            try {
                idx.stop();
            } catch (Exception ignored) {
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

    // --- Utility methods ---
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

        if ("media".equals(uri.getAuthority())) {
            try (Cursor cursor = resolver.query(
                    uri,
                    new String[]{MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long dateTaken = cursor.getLong(0);
                    long dateModified = cursor.getLong(1);
                    return dateTaken > 0 ? dateTaken : dateModified * 1000;
                }
            }
        }

        DocumentFile file = DocumentFile.fromSingleUri(getApplication(), uri);
        if (file != null && file.exists()) return file.lastModified();

        return System.currentTimeMillis();
    }
}
