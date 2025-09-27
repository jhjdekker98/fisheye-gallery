package com.jhjdekker98.fisheyegallery.config.smb;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jhjdekker98.fisheyegallery.Constants;
import com.jhjdekker98.fisheyegallery.security.SecureStorageHelper;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class SmbCredentials {
    private static final String EMPTY_MAP_JSON = "{}";
    public final String host;
    public final String share;
    public final String username;
    public final String password;
    public final String rootPath;

    public SmbCredentials(String host, String share, String username, String password, String rootPath) {
        this.host = host;
        this.share = share;
        this.username = username;
        this.password = password;
        this.rootPath = rootPath;
    }

    public static Map<String, SmbCredentials> getSmbCredentials(SecureStorageHelper ssh) {
        String json = ssh.retrieveDecrypted(Constants.SECURE_SHARED_PREFS_KEY_SMB_CONNS);
        if (json == null || json.isEmpty()) return new HashMap<>();
        Type type = new TypeToken<Map<String, SmbCredentials>>() {
        }.getType();
        return new Gson().fromJson(json, type);
    }

    public static void saveSmbCredentials(SecureStorageHelper ssh, SmbCredentials creds) {
        Map<String, SmbCredentials> credentialsMap = SmbCredentials.getSmbCredentials(ssh);
        if (credentialsMap == null) credentialsMap = new HashMap<>();
        credentialsMap.put(creds.host + "/" + creds.share, creds);
        String serialized = new Gson().toJson(credentialsMap);
        ssh.storeEncrypted(Constants.SECURE_SHARED_PREFS_KEY_SMB_CONNS, serialized);
    }

    public static void clearSmbCredentials(SecureStorageHelper ssh) {
        ssh.storeEncrypted(Constants.SECURE_SHARED_PREFS_KEY_SMB_CONNS, EMPTY_MAP_JSON);
    }
}
