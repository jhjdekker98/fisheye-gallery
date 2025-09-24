package com.jhjdekker98.fisheyegallery.model;

import androidx.documentfile.provider.DocumentFile;

public class FileEntry {
    private final DocumentFile file;
    private final int depth;

    public FileEntry(DocumentFile file, int depth) {
        this.file = file;
        this.depth = depth;
    }

    public DocumentFile getFile() {
        return file;
    }

    public int getDepth() {
        return depth;
    }
}
