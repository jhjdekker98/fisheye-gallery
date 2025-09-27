package com.jhjdekker98.fisheyegallery.model.mediacache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IndexerType;

@Entity(tableName = "media_cache")
public class MediaCacheItem {
    @PrimaryKey
    @NonNull
    public String key;
    public String uri;
    public String album;
    public IndexerType indexerType;
    public long lastModified;

    public MediaCacheItem(@NonNull String key, String uri, String album, IndexerType indexerType, long lastModified) {
        this.key = key;
        this.uri = uri;
        this.album = album;
        this.indexerType = indexerType;
        this.lastModified = lastModified;
    }
}
