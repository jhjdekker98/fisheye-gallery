package com.jhjdekker98.fisheyegallery.model;

import android.net.Uri;

public abstract class GalleryItem {
    public static class Header extends GalleryItem {
        public final String dateLabel;

        public Header(String dateLabel) {
            this.dateLabel = dateLabel;
        }
    }

    public static class Image extends GalleryItem {
        public final Uri uri;

        public Image(Uri uri) {
            this.uri = uri;
        }
    }
}
