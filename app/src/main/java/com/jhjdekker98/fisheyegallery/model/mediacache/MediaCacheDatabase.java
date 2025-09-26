package com.jhjdekker98.fisheyegallery.model.mediacache;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MediaCacheItem.class}, version = 1)
public abstract class MediaCacheDatabase extends RoomDatabase {

    public static MediaCacheDatabase getInstance(Context context) {
        return Room.databaseBuilder(context, MediaCacheDatabase.class, "media_cache_db")
                .fallbackToDestructiveMigration()
                .build();
    }

    public abstract MediaCacheDao mediaCacheDao();
}
