package com.jhjdekker98.fisheyegallery.config.smb;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.security.SecureStorageHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class SmbUriLoader implements ModelLoader<Uri, InputStream> {

    private final Context context;

    public SmbUriLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(@NonNull Uri uri, int width, int height,
                                               @NonNull Options options) {
        return new LoadData<>(new ObjectKey(uri), new SmbDataFetcher(context, uri));
    }

    @Override
    public boolean handles(@NonNull Uri uri) {
        return "content".equals(uri.getScheme()) && Constants.SMB_CONTENT_AUTHORITY.equals(uri.getAuthority());
    }

    // --- DataFetcher ---
    public static class SmbDataFetcher implements DataFetcher<InputStream> {

        private final Context context;
        private final Uri uri;
        private InputStream inputStream;

        public SmbDataFetcher(Context context, Uri uri) {
            this.context = context.getApplicationContext();
            this.uri = uri;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    SmbCredentials creds = getCredentialsForUri(uri);
                    if (creds == null) throw new IOException("No SMB credentials found for " + uri);

                    SMBClient client = new SMBClient();
                    Connection connection = client.connect(creds.host);
                    Session session = connection.authenticate(
                            new com.hierynomus.smbj.auth.AuthenticationContext(
                                    creds.username,
                                    creds.password.toCharArray(),
                                    null
                            )
                    );
                    DiskShare share = (DiskShare) session.connectShare(creds.share);

                    String path = buildSmbPath(uri);
                    com.hierynomus.smbj.share.File smbFile = share.openFile(
                            path,
                            EnumSet.of(AccessMask.GENERIC_READ),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                            SMB2CreateDisposition.FILE_OPEN,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                    );

                    inputStream = smbFile.getInputStream(); // give Glide the stream
                    callback.onDataReady(inputStream);

                    // Close SMB resources asynchronously when Glide is done
                    // We'll close in cleanup()
                } catch (Exception e) {
                    callback.onLoadFailed(e);
                }
            });
        }

        @Override
        public void cleanup() {
            try {
                if (inputStream != null) inputStream.close();
            } catch (IOException ignored) {
            }
        }

        @Override
        public void cancel() {
        }

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.REMOTE;
        }

        private SmbCredentials getCredentialsForUri(Uri uri) {
            // Lookup credentials using the key from SecureStorageHelper
            final SecureStorageHelper ssh = SecureStorageHelper.getInstance(context.getApplicationContext());
            Map<String, SmbCredentials> credsMap = SmbCredentials.getSmbCredentials(ssh);
            String key = uri.getPathSegments().get(0) + "/" + uri.getPathSegments().get(1);
            return credsMap.get(key);
        }

        private String buildSmbPath(Uri uri) {
            List<String> segments = uri.getPathSegments();
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < segments.size(); i++) {
                sb.append(segments.get(i));
                if (i < segments.size() - 1) sb.append("/");
            }
            return sb.toString();
        }
    }
}

