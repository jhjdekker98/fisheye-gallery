package com.jhjdekker98.fisheyegallery.config.smb;

public interface TransferCallback {
    void onSuccess(String message);

    void onFailure(String message, Exception e);
}
