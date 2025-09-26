package com.jhjdekker98.fisheyegallery.model.mediacache;

import android.content.Context;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MediaCacheRepository {
    private final MediaCacheDao dao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public MediaCacheRepository(Context context) {
        dao = MediaCacheDatabase.getInstance(context).mediaCacheDao();
    }

    public void updateCache(List<MediaCacheItem> items) {
        executor.execute(() -> dao.insertAll(items));
    }

    public void queryCache(int skip, int limit, Consumer<List<MediaCacheItem>> callback) {
        executor.execute(() -> {
            final List<MediaCacheItem> results = dao.query(skip, limit);
            callback.accept(results);
        });
    }

    public void deleteFromCache(List<MediaCacheItem> items) {
        executor.execute(() -> {
            dao.deleteByKeys(items.stream().map(mci -> mci.key).collect(Collectors.toList()));
        });
    }
}
