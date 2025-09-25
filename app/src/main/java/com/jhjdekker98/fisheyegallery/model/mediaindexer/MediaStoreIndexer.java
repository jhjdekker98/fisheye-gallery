package com.jhjdekker98.fisheyegallery.model.mediaindexer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MediaStoreIndexer implements IMediaIndexer {
    private static final int BATCH_SIZE = 50; // tweak for smooth UI
    private final Context context;
    private volatile boolean canceled = false;

    public MediaStoreIndexer(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void startIndexing(Callback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
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
                if (cursor == null) {
                    callback.onComplete();
                    return;
                }

                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                List<Uri> batch = new ArrayList<>();
                while (!canceled && cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    batch.add(contentUri);

                    if (batch.size() >= BATCH_SIZE) {
                        callback.onMediaFound(new ArrayList<>(batch));
                        batch.clear();
                    }
                }

                if (!batch.isEmpty()) {
                    callback.onMediaFound(batch);
                }
            } catch (Exception e) {
                Log.e("MediaStoreIndexer", "Error querying MediaStore", e);
            }

            callback.onComplete();
        });
    }

    @Override
    public void stop() {
        canceled = true;
    }
}
