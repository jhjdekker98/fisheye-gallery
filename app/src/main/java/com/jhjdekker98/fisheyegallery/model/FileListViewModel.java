package com.jhjdekker98.fisheyegallery.model;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
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
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.config.smb.SmbCredentials;
import com.jhjdekker98.fisheyegallery.model.mediacache.MediaCacheItem;
import com.jhjdekker98.fisheyegallery.model.mediacache.MediaCacheRepository;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IMediaIndexer;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IndexerType;
import com.jhjdekker98.fisheyegallery.security.SecureStorageHelper;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.content.Context.MODE_PRIVATE;

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

    // --- Load cache, then run indexers ---
    public void loadCacheThenIndex(List<IMediaIndexer> indexers) {
        stopIndexing();
        if (executor != null) executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            int skip = 0;
            final List<MediaCacheItem> batch = new ArrayList<>();
            final Context context = getApplication();

            do {
                final CountDownLatch latch = new CountDownLatch(1);

                cacheRepo.queryCache(skip, CACHE_BATCH_SIZE, items -> {
                    final List<MediaCacheItem> validItems = new ArrayList<>();
                    final List<MediaCacheItem> staleItems = new ArrayList<>();

                    items.forEach(mci -> {
                        boolean fileExists = checkUriExists(Uri.parse(mci.uri));
                        boolean acceptedBySettings = indexerTypeAcceptedByCurrentSettings(mci.indexerType, context);
                        Log.d("FileListViewModel", "Uri: " + mci.uri +
                                "\n\texists: " + fileExists +
                                "\n\taccepted: " + acceptedBySettings);
                        if (fileExists && acceptedBySettings) {
                            validItems.add(mci);
                        } else {
                            staleItems.add(mci);
                        }
                    });

                    processNewCacheItems(validItems, true);

                    if (!staleItems.isEmpty()) {
                        cacheRepo.deleteFromCache(staleItems);
                        removeFromMapsAndUi(staleItems);
                    }

                    batch.clear();
                    batch.addAll(validItems);
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

    // --- Process cache/indexed items ---
    private void processNewCacheItems(List<MediaCacheItem> items, boolean forcePost) {
        if (items == null || items.isEmpty()) return;

        synchronized (uriMap) {
            for (MediaCacheItem item : items) {
                Uri u = Uri.parse(item.uri);
                if (!uriMap.containsKey(item.key)) {
                    uriMap.put(item.key, u);

                    String dayKey = formatDay(item.lastModified);
                    List<GalleryItem.Image> dayList = groupedMap.computeIfAbsent(dayKey, k -> new ArrayList<>());
                    dayList.add(new GalleryItem.Image(u, item.indexerType));
                }
            }
        }

        if (forcePost) rebuildAndPost();
        else mainHandler.post(this::rebuildAndPost);
    }

    private void removeFromMapsAndUi(List<MediaCacheItem> staleItems) {
        synchronized (uriMap) {
            for (MediaCacheItem mci : staleItems) {
                uriMap.remove(mci.key);
                groupedMap.values().forEach(list ->
                        list.removeIf(img -> img.uri.toString().equals(mci.uri))
                );
            }
        }
        rebuildAndPost();
    }

    private void rebuildAndPost() {
        List<GalleryItem> rebuilt = new ArrayList<>();
        // Sort day keys descending (newest first)
        List<String> sortedKeys = new ArrayList<>(groupedMap.keySet());
        sortedKeys.sort(Comparator.reverseOrder());

        for (String dayKey : sortedKeys) {
            List<GalleryItem.Image> images = groupedMap.get(dayKey);
            if (images == null || images.isEmpty()) continue;
            rebuilt.add(new GalleryItem.Header(dayKey));
            rebuilt.addAll(images);
        }

        mainHandler.post(() -> {
            groupedMediaLive.setValue(rebuilt);
        });
    }

    // --- Indexing callbacks ---
    private void processNewUris(List<Uri> newUris, IndexerType indexerType, boolean forcePost) {
        if (newUris == null || newUris.isEmpty()) return;
        final List<MediaCacheItem> cacheItems = new ArrayList<>();

        for (Uri uri : newUris) {
            String key = normalizeKey(uri);
            cacheItems.add(new MediaCacheItem(
                    key,
                    uri.toString(),
                    null,
                    indexerType,
                    getFileDate(uri)));
        }

        processNewCacheItems(cacheItems, forcePost);
        cacheRepo.updateCache(cacheItems);
    }

    private synchronized void startIndexing(List<IMediaIndexer> indexers) {
        stopIndexing();
        if (indexers == null || indexers.isEmpty()) return;
        activeIndexers.addAll(indexers);

        for (IMediaIndexer idx : indexers) {
            idx.startIndexing(new IMediaIndexer.Callback() {
                @Override
                public void onMediaFound(List<Uri> newUris) {
                    processNewUris(newUris, idx.getIndexerType(), false);
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

    // --- Helpers ---
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
            } catch (Exception e) {
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

        if ("com.jhjdekker98.fisheyegallery.smb".equals(uri.getAuthority()) ||
                "media".equals(uri.getAuthority())) {
            try (Cursor cursor = resolver.query(
                    uri,
                    new String[]{MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    if (cursor.getColumnCount() == 1) return cursor.getLong(0);
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

    private String formatDay(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(millis));
    }

    private boolean indexerTypeAcceptedByCurrentSettings(IndexerType indexerType, Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE);
        final SecureStorageHelper ssh = SecureStorageHelper.getInstance(context);

        switch (indexerType) {
            case MEDIASTORE:
                return prefs.getBoolean(Constants.SHARED_PREFS_KEY_USE_MEDIASTORE, true);
            case SAF:
                return !prefs.getStringSet(Constants.SHARED_PREFS_KEY_SAF_FOLDERS, new HashSet<>()).isEmpty();
            case SMB:
                return !SmbCredentials.getSmbCredentials(ssh).isEmpty();
        }
        return true;
    }
}

