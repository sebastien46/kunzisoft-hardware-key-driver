package com.kunzisoft.hardware.key.utils;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.security.KeyStoreException;
import java.util.Objects;

public class SecretKeyHelper {
    private static final String TAG = "SecretKeyHelper";

    private final SecretKeyManager secretKeyManager;

    public SecretKeyHelper() {
        SecretKeyManager secretKeyManager;
        try {
            secretKeyManager = new SecretKeyManager();
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to create key manager");
            secretKeyManager = null;
        }
        this.secretKeyManager = secretKeyManager;
    }

    private boolean checkIfNoKeyManager() {
        if (secretKeyManager == null) {
            Log.e(TAG, "was unable to create key manager");
            return true;
        } else return false;
    }

    @NonNull
    public SecretKeyManager getKeyManager() throws NullPointerException {
        return Objects.requireNonNull(secretKeyManager, "no key manager");
    }

    public boolean createSecretKey(@NonNull String secretKeyAlias) {
        if (checkIfNoKeyManager()) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecretKeyManager.createSecretKey(secretKeyAlias);
                return true;
            } else {
                Log.e(TAG, "SDK version must be at least 23 (Android 6.0)");
                return false;
            }
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to create secret key for key manager", ex);
            return false;
        }
    }

    public boolean hasSecretKey(@NonNull String secretKeyAlias) {
        if (checkIfNoKeyManager()) return false;
        try {
            return secretKeyManager.hasSecretKey(secretKeyAlias);
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to check if secret key exists in key manager", ex);
            return false;
        }
    }

    public boolean deleteSecretKey(@NonNull String secretKeyAlias) {
        if (checkIfNoKeyManager()) return false;
        try {
            secretKeyManager.deleteSecretKey(secretKeyAlias);
            return true;
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to remove secret key from key manager", ex);
            return false;
        }
    }
}
