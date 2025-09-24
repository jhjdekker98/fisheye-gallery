package com.jhjdekker98.fisheyegallery.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.util.LruCache;
import java.io.*;

public class ThumbnailManager {

    private static final long MILLIS_PER_DAY = 86400000L;
    private static final String CACHE_DIR_NAME = "thumbnails";
    private static final String EXT = ".jpg";
    private static final int THUMB_WIDTH = 128;
    private static final int THUMB_HEIGHT = 128;

    private final File cacheDir;
    private final LruCache<String, Bitmap> memoryCache;
    private final long ttlMillis;

    public ThumbnailManager(Context context, long ttlDays) {
        this.cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs(); // TODO: Improve error handling
        }

        // Memory cache = 1/8th of available heap
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        this.memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        this.ttlMillis = ttlDays * MILLIS_PER_DAY;
    }

    /**
     * Get a thumbnail for the given file path.
     * If not cached, generate and store it.
     */
    public Bitmap getThumbnail(Context context, Uri uri) {
        final String key = uri.toString();

        // Check memory cache
        final Bitmap thumb = memoryCache.get(key);
        if (thumb != null) {
            return thumb;
        }

        // Check disk cache
        final File thumbFile = getThumbFile(key);
        if (thumbFile.exists() && !isExpired(thumbFile)) {
            final Bitmap diskThumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
            if (diskThumb != null) {
                memoryCache.put(key, diskThumb);
                return diskThumb;
            }
        }

        // Generate new thumbnail
        try {
            final InputStream input = context.getContentResolver().openInputStream(uri);
            final Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                return null;
            }
            final Bitmap newThumb = ThumbnailUtils.extractThumbnail(bitmap, THUMB_WIDTH, THUMB_HEIGHT);
            saveToDisk(key, newThumb);
            memoryCache.put(key, newThumb);
            return newThumb;
        } catch (FileNotFoundException e) {
            e.printStackTrace(); // TODO: Improve error handling
            return null;
        }
    }

    /**
     * Save thumbnail bitmap to disk cache.
     */
    private void saveToDisk(String filePath, Bitmap bitmap) {
        final File thumbFile = getThumbFile(filePath);
        try (FileOutputStream out = new FileOutputStream(thumbFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
        } catch (IOException e) {
            e.printStackTrace(); // TODO: Improve error handling
        }
    }

    /**
     * Build a deterministic filename from file path.
     */
    private File getThumbFile(String filePath) {
        final String safeName = String.valueOf(filePath.hashCode());
        return new File(cacheDir, safeName + EXT);
    }

    /**
     * Expired if older than TTL.
     */
    private boolean isExpired(File file) {
        final long age = System.currentTimeMillis() - file.lastModified();
        return age > ttlMillis;
    }

    /**
     * Cleanup old thumbnails (call occasionally).
     */
    public void cleanup() {
        final File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        for (File f : files) {
            if (now - f.lastModified() > ttlMillis) {
                f.delete(); // TODO: Improve error handling
            }
        }
    }
}
