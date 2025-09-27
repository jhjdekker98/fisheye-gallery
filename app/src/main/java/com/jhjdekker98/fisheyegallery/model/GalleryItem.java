package com.jhjdekker98.fisheyegallery.model;

import android.net.Uri;
import com.jhjdekker98.fisheyegallery.model.mediaindexer.IndexerType;

public abstract class GalleryItem {
    public static class Header extends GalleryItem {
        public final String dateLabel;

        public Header(String dateLabel) {
            this.dateLabel = dateLabel;
        }
    }

    public static class Image extends GalleryItem {
        public final Uri uri;
        public final IndexerType indexerType;

        public Image(Uri uri, IndexerType indexerType) {
            this.uri = uri;
            this.indexerType = indexerType;
        }
    }
}
