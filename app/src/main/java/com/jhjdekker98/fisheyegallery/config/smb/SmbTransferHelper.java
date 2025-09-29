package com.jhjdekker98.fisheyegallery.config.smb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.exifinterface.media.ExifInterface;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msdtyp.FileTime;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileBasicInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.security.SecureStorageHelper;
import com.jhjdekker98.fisheyegallery.util.CollectionUtil;
import com.jhjdekker98.fisheyegallery.util.FileHelper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmbTransferHelper {
    private final Context context;
    private final ExecutorService executor;
    private final ActivityResultLauncher<Intent> createDocLauncher;
    private final TransferCallback callback;
    private Uri pendingDownloadUri;

    public SmbTransferHelper(ComponentActivity activity, TransferCallback callback) {
        this.context = activity;
        this.callback = callback;
        this.executor = Executors.newSingleThreadExecutor();

        this.createDocLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

                    final Uri folderUri = result.getData().getData();
                    if (folderUri == null || pendingDownloadUri == null) {
                        callback.onFailure("Missing folder or SMB source URI", new IllegalArgumentException());
                        return;
                    }

                    executor.execute(() -> performSmbDownloadToFolder(folderUri, activity));
                }
        );
    }

    public void uploadToSmb(Activity activity, Uri localFileUri) {
        final SecureStorageHelper ssh = SecureStorageHelper.getInstance(context);
        final Map<String, SmbCredentials> smbCredentialsMap = SmbCredentials.getSmbCredentials(ssh);

        if (smbCredentialsMap.isEmpty()) {
            callback.onFailure("No SMB connections defined", new IOException("No SMB connections defined"));
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Upload")
                .setItems(smbCredentialsMap.keySet().toArray(new String[0]), (dialog, which) -> {
                    final String chosenKey = (String) smbCredentialsMap.keySet().toArray()[which];
                    final SmbCredentials creds = smbCredentialsMap.get(chosenKey);

                    executor.execute(() -> {
                        try (SMBClient client = new SMBClient();
                             Connection connection = client.connect(creds.host)) {

                            final AuthenticationContext auth = new AuthenticationContext(
                                    creds.username,
                                    creds.password.toCharArray(),
                                    ""
                            );

                            final Session session = connection.authenticate(auth);
                            final DiskShare share = (DiskShare) session.connectShare(creds.share);
                            final String remoteFilename = String.format("%s/%s",
                                    creds.rootPath,
                                    getFileNameFromUri(localFileUri));
                            final com.hierynomus.smbj.share.File smbFile = share.openFile(
                                    remoteFilename,
                                    EnumSet.of(AccessMask.GENERIC_WRITE),
                                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                                    SMB2ShareAccess.ALL,
                                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                    EnumSet.of(SMB2CreateOptions.FILE_WRITE_THROUGH));

                            try (InputStream in = context.getContentResolver().openInputStream(localFileUri);
                                 OutputStream out = smbFile.getOutputStream()) {

                                byte[] buffer = new byte[Constants.BUFFER_SIZE];
                                int len;
                                while ((len = in.read(buffer)) > 0) {
                                    out.write(buffer, 0, len);
                                }
                            } finally {
                                smbFile.close();
                            }

                            final long lastModified = getLastModifiedForUri(localFileUri);
                            share.setFileInformation(remoteFilename, new FileBasicInformation(
                                    FileTime.ofEpochMillis(lastModified), // CreationTime
                                    FileTime.ofEpochMillis(lastModified), // LastAccessTime
                                    FileTime.ofEpochMillis(lastModified), // LastWriteTime
                                    FileTime.ofEpochMillis(lastModified), // ChangeTime
                                    0 // FileAttributes
                            ));

                            runOnUiThread(activity, () -> {
                                final Set<Uri> urisToDelete = CollectionUtil.setOf(localFileUri);
                                final int deletedCount = FileHelper.deleteUris(activity, urisToDelete);
                                if (deletedCount == urisToDelete.size()) {
                                    callback.onSuccess("Uploaded successfully");
                                } else {
                                    callback.onFailure("One or more local files were not deleted", null);
                                }
                            });

                        } catch (Exception e) {
                            runOnUiThread(activity, () -> callback.onFailure("Upload failed", e));
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    public void downloadToLocal(Activity activity, Uri smbFileUri) {
        new AlertDialog.Builder(activity)
                .setTitle("Download")
                .setMessage("Move this file to local storage?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    pendingDownloadUri = smbFileUri;

                    // Launch system folder picker
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    createDocLauncher.launch(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @SuppressLint("RestrictedApi")
    private void performSmbDownloadToFolder(Uri folderUri, ComponentActivity activity) {
        try {
            final SmbCredentials creds = getCredsFromUri(pendingDownloadUri);
            try (SMBClient client = new SMBClient();
                 Connection connection = client.connect(creds.host)) {

                final AuthenticationContext auth = new AuthenticationContext(
                        creds.username,
                        creds.password.toCharArray(),
                        "" // Optional: domain
                );

                final Session session = connection.authenticate(auth);
                final DiskShare share = (DiskShare) session.connectShare(creds.share);
                final String smbFilePath = getSmbFilePathFor(pendingDownloadUri, creds);

                final long smbLastModified = getLastModifiedForSmb(pendingDownloadUri);
                final String fileName = getFileNameFromUri(pendingDownloadUri);
                final String mimeType = getMimeTypeFromFileName(fileName);

                // Create file
                final Uri folderDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                        folderUri, DocumentsContract.getTreeDocumentId(folderUri));
                final Uri newFileUri = DocumentsContract.createDocument(
                        context.getContentResolver(),
                        folderDocumentUri,
                        mimeType,
                        fileName);
                if (newFileUri == null) {
                    throw new IOException("Failed to create destination file");
                }

                // Write to file
                try (InputStream in = share.openFile(
                        smbFilePath,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                ).getInputStream();
                     OutputStream out = context.getContentResolver().openOutputStream(newFileUri)) {

                    byte[] buffer = new byte[Constants.BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                // Attempt to set Exif DateTime immediately
                try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(newFileUri, "rw")) {
                    if (pfd != null) {
                        final ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
                        exif.setDateTime(smbLastModified);
                        exif.saveAttributes();
                    }
                }

                // Clean up remote SMB file
                share.rm(smbFilePath);

                runOnUiThread(activity, () -> callback.onSuccess("Download complete"));
            }
        } catch (Exception e) {
            runOnUiThread(activity, () -> callback.onFailure("Download failed", e));
        } finally {
            pendingDownloadUri = null;
        }
    }

    // --- Helpers ---

    private String getFileNameFromUri(Uri uri) {
        if ("file".equals(uri.getScheme())) return new File(uri.getPath()).getName();
        if ("content".equals(uri.getScheme()) && Constants.SMB_CONTENT_AUTHORITY.equals(uri.getAuthority())) {
            return uri.getLastPathSegment();
        }

        final Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        return "unknown_file";
    }

    private SmbCredentials getCredsFromUri(Uri uri) {
        final SecureStorageHelper ssh = SecureStorageHelper.getInstance(context);
        final Map<String, SmbCredentials> smbCredentialsMap = SmbCredentials.getSmbCredentials(ssh);

        for (SmbCredentials creds : smbCredentialsMap.values()) {
            final String credsPrefix = String.format("content://%s/%s/%s/",
                    Constants.SMB_CONTENT_AUTHORITY,
                    creds.host,
                    creds.share);
            if (uri.toString().startsWith(credsPrefix)) {
                return creds;
            }
        }

        throw new IllegalArgumentException("No matching SMB credentials found for URI: " + uri);
    }

    private String getSmbFilePathFor(Uri uri, SmbCredentials creds) {
        String path = uri.getPath();
        if (path == null) return "";
        String prefix = String.format("/%s/%s", creds.host, creds.share);
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return path;
    }

    private void runOnUiThread(Activity activity, Runnable runnable) {
        activity.runOnUiThread(runnable);
    }

    private long getLastModifiedForUri(Uri uri) {
        return FileHelper.getFileDate(context, uri);
    }

    private long getLastModifiedForSmb(Uri smbUri) throws IOException {
        final SmbCredentials creds = getCredsFromUri(smbUri);
        try (SMBClient client = new SMBClient();
             Connection connection = client.connect(creds.host)) {

            final AuthenticationContext auth = new AuthenticationContext(
                    creds.username,
                    creds.password.toCharArray(),
                    ""
            );
            final Session session = connection.authenticate(auth);
            final DiskShare share = (DiskShare) session.connectShare(creds.share);

            final String path = getSmbFilePathFor(smbUri, creds);
            final FileAllInformation info = share.getFileInformation(path);
            return info.getBasicInformation().getChangeTime().toEpochMillis();
        }
    }

    private String getMimeTypeFromFileName(String fileName) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
    }
}
