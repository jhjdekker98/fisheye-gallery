package com.jhjdekker98.fisheyegallery.model;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.jhjdekker98.fisheyegallery.util.CollectionUtil;
import com.jhjdekker98.fisheyegallery.util.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListViewModel extends AndroidViewModel {
    private static final Set<String> ALLOWED_EXTENSIONS = CollectionUtil.setOf(
            "jpg",
            "jpeg"
    );
    private final MutableLiveData<List<DocumentFile>> mediaFiles = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FileListViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<DocumentFile>> getMediaFiles() {
        return mediaFiles;
    }

    public void loadFromTreeUri(final Uri treeUri, final int maxDepth) {
        if (treeUri == null) {
            mediaFiles.postValue(new ArrayList<>());
            return;
        }

        executor.execute(() -> {
            final DocumentFile root = DocumentFile.fromTreeUri(getApplication(), treeUri);
            final List<DocumentFile> out = new ArrayList<>();
            if (root != null && root.exists() && root.isDirectory()) {
                walkCollectMedia(root, 0, maxDepth, out);
            }
            mediaFiles.postValue(out);
        });
    }

    private void walkCollectMedia(DocumentFile current, int currentDepth, int maxDepth, List<DocumentFile> out) {
        if (current == null || !current.isDirectory() || !(maxDepth <= 0 || currentDepth < maxDepth)) {
            return;
        }

        for (DocumentFile child : current.listFiles()) {
            if (child.isDirectory()) {
                walkCollectMedia(child, currentDepth + 1, maxDepth, out);
                continue;
            }

            final String name = child.getName() != null ? child.getName().toLowerCase() : "";
            final String[] nameParts = StringUtil.splitOnLast(name, '.');
            if (nameParts.length <= 1 || !ALLOWED_EXTENSIONS.contains(nameParts[1])) {
                continue;
            }
            out.add(child);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
