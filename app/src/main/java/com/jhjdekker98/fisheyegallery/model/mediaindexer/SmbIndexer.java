package com.jhjdekker98.fisheyegallery.model.mediaindexer;

import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmbIndexer implements IMediaIndexer {
    private static final String TAG = "SmbIndexer";
    private static final int BATCH_SIZE = 50;

    private final String host;
    private final String share;
    private final String username;
    private final String password;
    private final String rootPath;
    private final int maxDepth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean canceled = false;

    public SmbIndexer(String host, String share, String username, String password, String rootPath, Integer maxDepth) {
        this.host = host;
        this.share = share;
        this.username = username;
        this.password = password;
        this.rootPath = rootPath == null ? "" : rootPath;
        this.maxDepth = maxDepth == null ? 0 : maxDepth;
    }

    public static Uri getContentUri(String host, String share, String relativePath) {
        String cleanPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return new Uri.Builder()
                .scheme("content")
                .authority("com.jhjdekker98.fisheyegallery.smb")
                .encodedPath(host + "/" + share + "/" + cleanPath)
                .build();
    }

    @Override
    public void startIndexing(Callback callback) {
        canceled = false;
        executor.execute(() -> {
            SMBClient client = new SMBClient();
            try (Connection connection = client.connect(host)) {
                AuthenticationContext auth = new AuthenticationContext(username, password.toCharArray(), "");
                Session session = connection.authenticate(auth);

                try (DiskShare diskShare = (DiskShare) session.connectShare(share)) {
                    walkDirectory(diskShare, rootPath, 0, callback);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error indexing SMB share", e);
            }

            callback.onComplete();
        });
    }

    /**
     * Recursive directory walker with depth and MIME filter.
     */
    private void walkDirectory(DiskShare share, String path, int currentDepth, Callback callback) {
        if (canceled) return;
        if (maxDepth > 0 && currentDepth > maxDepth) return;

        final List<Uri> batch = new ArrayList<>();

        for (FileIdBothDirectoryInformation f : share.list(path)) {
            if (canceled) return;

            String name = f.getFileName();
            if (name.equals(".") || name.equals("..")) continue;

            String fullPath = path.isEmpty() ? name : path + "/" + name;

            if (isDirectory(f)) {
                walkDirectory(share, fullPath, currentDepth + 1, callback);
            } else {
                // MIME type filter
                String extension = MimeTypeMap.getFileExtensionFromUrl(name);
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mimeType == null || !(mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
                    continue; // skip non-media files
                }

                Uri uri = SmbIndexer.getContentUri(host, this.share, fullPath);
                batch.add(uri);

                if (batch.size() >= BATCH_SIZE) {
                    callback.onMediaFound(new ArrayList<>(batch));
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            callback.onMediaFound(batch);
        }
    }

    @Override
    public void stop() {
        canceled = true;
    }

    private boolean isDirectory(FileIdBothDirectoryInformation f) {
        return (f.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
    }

    @Override
    public IndexerType getIndexerType() {
        return IndexerType.SMB;
    }
}

