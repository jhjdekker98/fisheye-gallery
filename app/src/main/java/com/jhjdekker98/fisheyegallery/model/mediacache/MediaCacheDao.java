package com.jhjdekker98.fisheyegallery.model.mediacache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MediaCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MediaCacheItem> items);

    @Query("SELECT * FROM `media_cache` ORDER BY `lastModified` DESC LIMIT :limit OFFSET :skip")
    List<MediaCacheItem> query(int skip, int limit);

    @Query("DELETE FROM `media_cache` WHERE `key` = :key")
    void deleteByKey(String key);

    @Query("DELETE FROM `media_cache` WHERE `key` IN (:keys)")
    void deleteByKeys(List<String> keys);
}
