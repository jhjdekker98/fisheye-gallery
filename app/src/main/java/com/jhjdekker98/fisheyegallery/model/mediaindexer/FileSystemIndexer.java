package com.jhjdekker98.fisheyegallery.model.mediaindexer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.jhjdekker98.fisheyegallery.Constants;
import java.util.Collections;
import java.util.concurrent.Executors;

public class FileSystemIndexer implements IMediaIndexer {
    private final Context context;
    private final Uri rootUri;
    private final SharedPreferences prefs;
    private volatile boolean canceled = false;

    public FileSystemIndexer(Context context, Uri rootUri, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.rootUri = rootUri;
        this.prefs = prefs;
    }

    @Override
    public void startIndexing(Callback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
            if (root == null || !root.exists()) {
                callback.onComplete();
                return;
            }
            walk(root, 0, callback);
            callback.onComplete();
        });
    }

    private void walk(DocumentFile dir, int currentDepth, Callback callback) {
        if (canceled) {
            return;
        }
        final int maxDepth = getMaxDepth();
        if (maxDepth > 0 && currentDepth > maxDepth) {
            return;
        }
        for (DocumentFile file : dir.listFiles()) {
            if (canceled) {
                return;
            }
            if (file.isDirectory()) {
                walk(file, currentDepth + 1, callback);
            } else if (isMediaFile(file)) {
                callback.onMediaFound(Collections.singletonList(file.getUri()));
            }
        }
    }

    private boolean isMediaFile(DocumentFile file) {
        final String mimeType = file.getType();
        return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"));
    }

    private int getMaxDepth() {
        return prefs.getInt(Constants.SHARED_PREFS_KEY_DEPTH, 0);
    }

    @Override
    public void stop() {
        canceled = true;
    }
}
