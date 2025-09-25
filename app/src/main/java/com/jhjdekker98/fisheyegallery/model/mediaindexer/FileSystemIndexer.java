package com.jhjdekker98.fisheyegallery.model.mediaindexer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.jhjdekker98.fisheyegallery.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileSystemIndexer implements IMediaIndexer {
    private static final int BATCH_SIZE = 50;
    private final Context context;
    private final Uri rootUri;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean canceled = false;

    public FileSystemIndexer(Context context, Uri rootUri, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.rootUri = rootUri;
        this.prefs = prefs;
    }

    @Override
    public void startIndexing(Callback callback) {
        executor.execute(() -> {
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
        if (canceled) return;

        final int maxDepth = getMaxDepth();
        if (maxDepth > 0 && currentDepth > maxDepth) return;

        List<Uri> batch = new ArrayList<>();

        for (DocumentFile file : dir.listFiles()) {
            if (canceled) return;

            if (file.isDirectory()) {
                walk(file, currentDepth + 1, callback);
            } else if (isMediaFile(file)) {
                batch.add(file.getUri());
                if (batch.size() >= BATCH_SIZE) {
                    sendBatch(batch, callback);
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            sendBatch(batch, callback);
        }
    }

    private void sendBatch(List<Uri> batch, Callback callback) {
        // Reuse the same executor: runs sequentially on the background thread
        callback.onMediaFound(new ArrayList<>(batch));

        // Optional: tiny pause for network shares
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
