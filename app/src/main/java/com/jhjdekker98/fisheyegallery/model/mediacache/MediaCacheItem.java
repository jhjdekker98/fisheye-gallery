package com.jhjdekker98.fisheyegallery.model.mediacache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media_cache")
public class MediaCacheItem {
    @PrimaryKey
    @NonNull
    public String key;
    public String uri;
    public String album;
    public long lastModified;

    public MediaCacheItem(@NonNull String key, String uri, String album, long lastModified) {
        this.key = key;
        this.uri = uri;
        this.album = album;
        this.lastModified = lastModified;
    }
}
