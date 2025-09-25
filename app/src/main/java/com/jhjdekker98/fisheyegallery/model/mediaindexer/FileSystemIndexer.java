package com.jhjdekker98.fisheyegallery.model.mediaindexer;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.util.Collections;
import java.util.concurrent.Executors;

public class FileSystemIndexer implements IMediaIndexer {
    private final Context context;
    private final Uri rootUri;
    private volatile boolean canceled = false;

    public FileSystemIndexer(Context context, Uri rootUri) {
        this.context = context.getApplicationContext();
        this.rootUri = rootUri;
    }

    @Override
    public void startIndexing(Callback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
            if (root == null || !root.exists()) {
                callback.onComplete();
                return;
            }
            walk(root, callback);
            callback.onComplete();
        });
    }

    private void walk(DocumentFile dir, Callback callback) {
        if (canceled) {
            return;
        }
        for (DocumentFile file : dir.listFiles()) {
            if (canceled) {
                return;
            }
            if (file.isDirectory()) {
                walk(file, callback);
            } else if (isMediaFile(file)) {
                callback.onMediaFound(Collections.singletonList(file.getUri()));
            }
        }
    }

    private boolean isMediaFile(DocumentFile file) {
        final String mimeType = file.getType();
        return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"));
    }

    @Override
    public void stop() {
        canceled = true;
    }
}
