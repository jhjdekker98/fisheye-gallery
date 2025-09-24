package com.jhjdekker98.fisheyegallery.util;

import androidx.documentfile.provider.DocumentFile;
import com.jhjdekker98.fisheyegallery.model.FileEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileUtil {
    public static List<FileEntry> walkDocumentTree(DocumentFile current, int currentDepth, int maxDepth) {
        return walkDocumentTree(current, currentDepth, maxDepth, null);
    }

    public static List<FileEntry> walkDocumentTree(DocumentFile current, int currentDepth, int maxDepth, Set<String> allowedExtensions) {
        final List<FileEntry> result = new ArrayList<>();

        if (current.isDirectory() && (maxDepth <= 0 || currentDepth < maxDepth)) {
            for (DocumentFile child : current.listFiles()) {
                if (child.isDirectory()) {
                    result.addAll(walkDocumentTree(child, currentDepth + 1, maxDepth, allowedExtensions));
                } else {
                    final String name = child.getName().toLowerCase();
                    if (allowedExtensions == null) {
                        result.add(new FileEntry(child, currentDepth));
                        continue;
                    }
                    if (!name.contains(".")) {
                        continue;
                    }

                    final String extension = StringUtil.splitOnLast(name, '.')[1];
                    if (allowedExtensions.contains(extension)) {
                        result.add(new FileEntry(child, currentDepth));
                    }
                }
            }
        }
        return result;
    }
}
