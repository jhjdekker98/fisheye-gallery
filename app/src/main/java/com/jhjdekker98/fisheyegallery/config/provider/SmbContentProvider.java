package com.jhjdekker98.fisheyegallery.config.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.jhjdekker98.fisheyegallery.config.smb.SmbCredentials;
import com.jhjdekker98.fisheyegallery.security.SecureStorageHelper;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SmbContentProvider extends ContentProvider {

    private static final String AUTHORITY = "com.jhjdekker98.fisheyegallery.smb";
    private static final int SMB_FILE = 1;
    private static final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        matcher.addURI(AUTHORITY, "*", SMB_FILE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(path);
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (type != null) return type;
        }
        return "application/octet-stream";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Update not supported");
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) {
        try {
            // Parse SMB URI: content://authority/host/share/path/to/file
            final List<String> segments = uri.getPathSegments();
            if (segments.size() < 2) throw new FileNotFoundException("Invalid SMB URI");

            String host = segments.get(0);
            String shareName = segments.get(1);
            String smbPath = String.join("/", segments.subList(2, segments.size()));

            // Retrieve credentials from SecureStorageHelper
            final SecureStorageHelper ssh = SecureStorageHelper.getInstance(getContext().getApplicationContext());
            Map<String, SmbCredentials> credsMap = SmbCredentials.getSmbCredentials(ssh);
            if (credsMap == null) credsMap = new HashMap<>();

            SmbCredentials creds = credsMap.get(host + "/" + shareName);
            if (creds == null)
                throw new FileNotFoundException("No credentials for SMB share " + host + "/" + shareName);

            // Connect to SMB
            SMBClient client = new SMBClient();
            Connection connection = client.connect(host);
            Session session = connection.authenticate(
                    new com.hierynomus.smbj.auth.AuthenticationContext(
                            creds.username, creds.password.toCharArray(), null)
            );
            DiskShare share = (DiskShare) session.connectShare(shareName);

            // Open remote file
            com.hierynomus.smbj.share.File smbFile = share.openFile(
                    smbPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );

            // Copy to a temporary cache file
            java.io.File cacheFile = new java.io.File(getContext().getCacheDir(), UUID.randomUUID().toString());
            try (InputStream in = smbFile.getInputStream();
                 FileOutputStream out = new FileOutputStream(cacheFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            // Close SMB resources
            smbFile.close();
            share.close();
            session.close();
            connection.close();
            client.close();

            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("SMB open failed: " + e.getMessage(), e);
        }
    }
}
