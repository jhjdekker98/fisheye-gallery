package com.jhjdekker98.fisheyegallery.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;
import com.jhjdekker98.fisheyegallery.Constants;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FileHelper {
    private static final String TAG = "FileHelper";

    @SuppressLint("RestrictedApi")
    public static long getFileDate(Context context, Uri uri) {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                final ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
                final Long exifDt = exif.getDateTime();
                if (exifDt != null) {
                    return exifDt;
                }
            }
        } catch (IOException e) {
            // TODO: Implement error handling
        }

        final ContentResolver resolver = context.getContentResolver();

        if (Constants.SMB_CONTENT_AUTHORITY.equals(uri.getAuthority()) ||
                "media".equals(uri.getAuthority())) {
            try (Cursor cursor = resolver.query(
                    uri,
                    new String[]{MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    if (cursor.getColumnCount() == 1) return cursor.getLong(0);
                    long dateTaken = cursor.getLong(0);
                    long dateModified = cursor.getLong(1);
                    return dateTaken > 0 ? dateTaken : dateModified * 1000;
                }
            }
        }

        DocumentFile file = DocumentFile.fromSingleUri(context, uri);
        if (file != null && file.exists()) return file.lastModified();

        return System.currentTimeMillis();
    }

    /**
     * Delete a set of URIs. Handles both MediaStore items (batch or with user consent)
     * and non-MediaStore items (direct deletion).
     */
    public static int deleteUris(Context context, Set<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return 0;
        }

        Set<Uri> mediaStoreUris = new HashSet<>();
        Set<Uri> otherUris = new HashSet<>();

        // Classify URIs
        for (Uri uri : uris) {
            if (uri != null && "content".equals(uri.getScheme())
                    && "media".equals(uri.getAuthority())) {
                try {
                    mediaStoreUris.add(resolveToMediaItemUri(context, uri));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                otherUris.add(uri);
            }
        }

        int deletedCount = 0;

        // Delete MediaStore items
        if (!mediaStoreUris.isEmpty()) {
            deletedCount += deleteMediaStoreUris(context, mediaStoreUris);
        }

        // Delete non-MediaStore items
        if (!otherUris.isEmpty()) {
            deletedCount += deleteNonMediaUris(context, otherUris);
        }

        return deletedCount;
    }

    /**
     * Delete MediaStore Uris.
     * On Android Q+, attempts batch deletion with user consent if needed.
     */
    private static int deleteMediaStoreUris(Context context, Set<Uri> mediaUris) {
        ContentResolver resolver = context.getContentResolver();
        int successCount = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // First try direct delete
                for (Uri uri : mediaUris) {
                    try {
                        int rows = resolver.delete(uri, null, null);
                        if (rows > 0) {
                            successCount += rows;
                        }
                    } catch (SecurityException se) {
                        // Fallback: user consent
                        try {
                            IntentSender sender = MediaStore.createDeleteRequest(
                                    resolver, Collections.singletonList(uri)).getIntentSender();
                            if (context instanceof Activity) {
                                ((Activity) context).startIntentSenderForResult(
                                        sender,
                                        Constants.DELETE_MEDIASTORE_FILE_REQUEST_ID,
                                        null,
                                        0, 0, 0
                                );
                                Log.i(TAG, "Requested user consent to delete: " + uri);
                                return mediaUris.size(); // Presume successful deletion
                            } else {
                                Log.w(TAG, "Context not an Activity, cannot request delete for: " + uri);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to create delete request for: " + uri, e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Batch delete failed", e);
            }
        } else {
            // Legacy Android: try deleting directly
            for (Uri uri : mediaUris) {
                try {
                    int rows = resolver.delete(uri, null, null);
                    if (rows > 0) {
                        successCount += rows;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to delete media URI: " + uri, e);
                }
            }
        }

        return successCount;
    }

    /**
     * Delete non-MediaStore URIs: file:// and content:// from other providers.
     */
    private static int deleteNonMediaUris(Context context, Set<Uri> uris) {
        int successCount = 0;
        ContentResolver resolver = context.getContentResolver();

        for (Uri uri : uris) {
            try {
                if ("file".equals(uri.getScheme())) {
                    File f = new File(uri.getPath());
                    if (f.delete()) {
                        successCount++;
                    } else {
                        Log.w(TAG, "Failed to delete file: " + uri);
                    }
                } else if ("content".equals(uri.getScheme())) {
                    int rows = resolver.delete(uri, null, null);
                    if (rows > 0) {
                        successCount += rows;
                    } else {
                        Log.w(TAG, "Failed to delete content URI: " + uri);
                    }
                } else {
                    Log.w(TAG, "Unsupported URI scheme: " + uri);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete non-media URI: " + uri, e);
            }
        }

        return successCount;
    }

    private static Uri resolveToMediaItemUri(Context context, Uri uri) throws IOException {
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.MIME_TYPE},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                final long id = c.getLong(0);
                final String mime = c.getString(1);
                if (mime == null) {
                    throw new IOException("Unsupported Media URI");
                } else if (mime.startsWith("image/")) {
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                } else if (mime.startsWith("video/")) {
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                } else {
                    throw new IOException("Unsupported Media URI");
                }
            }
        }
        return null;
    }
}
