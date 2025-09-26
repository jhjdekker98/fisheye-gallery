package com.jhjdekker98.fisheyegallery.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecureStorageHelper {
    private static final String KEY_ALIAS = "FisheyeGalleryKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String PREF_NAME = "secure_prefs";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int TAG_SIZE = 128;
    private static SecureStorageHelper instance;
    private final SharedPreferences sharedPreferences;

    private SecureStorageHelper(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        createKeyIfNeeded();
    }

    public static SecureStorageHelper getInstance(Context context) {
        if (instance == null) {
            instance = new SecureStorageHelper(context);
        }
        return instance;
    }

    private void createKeyIfNeeded() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build();

                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                );
                keyGenerator.init(keySpec);
                keyGenerator.generateKey();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create encryption key", e);
        }
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return ((SecretKey) keyStore.getKey(KEY_ALIAS, null));
    }

    public void storeEncrypted(String key, String value) {
        try {
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());

            byte[] iv = cipher.getIV(); // Get IV from this Cipher
            byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            String encryptedData = Base64.encodeToString(iv, Base64.DEFAULT) + ":" +
                    Base64.encodeToString(encryptedBytes, Base64.DEFAULT);

            sharedPreferences.edit().putString(key, encryptedData).apply();
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String retrieveDecrypted(String key) {
        try {
            String encryptedData = sharedPreferences.getString(key, null);
            if (encryptedData == null) return null;

            String[] parts = encryptedData.split(":");
            byte[] iv = Base64.decode(parts[0], Base64.DEFAULT);
            byte[] encryptedBytes = Base64.decode(parts[1], Base64.DEFAULT);

            Cipher cipher = Cipher.getInstance(AES_MODE);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}

