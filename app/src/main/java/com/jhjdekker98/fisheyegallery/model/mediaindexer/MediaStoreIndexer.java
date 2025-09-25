package com.jhjdekker98.fisheyegallery.model.mediaindexer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MediaStoreIndexer implements IMediaIndexer {
    private static final int BATCH_SIZE = 100;
    private final Context context;

    public MediaStoreIndexer(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void startIndexing(Callback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final List<Uri> uris = new ArrayList<>();
            final String[] projection = {MediaStore.MediaColumns._ID};
            final Uri collection = MediaStore.Files.getContentUri("external");
            final String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " +
                    MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";
            final String[] selectionArgs = {
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            };

            try (Cursor cursor = context.getContentResolver().query(
                    collection, projection, selection, selectionArgs,
                    MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                    while (cursor.moveToNext()) {
                        final long id = cursor.getLong(idColumn);
                        final Uri contentUri = ContentUris.withAppendedId(collection, id);
                        uris.add(contentUri);

                        if (uris.size() >= BATCH_SIZE) {
                            callback.onMediaFound(new ArrayList<>(uris));
                            uris.clear();
                        }
                    }
                }
            }

            if (!uris.isEmpty()) {
                callback.onMediaFound(uris);
            }
            callback.onComplete();
        });
    }

    @Override
    public void stop() {
        // TODO: Implement
    }
}
