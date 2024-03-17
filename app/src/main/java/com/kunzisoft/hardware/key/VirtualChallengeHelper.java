package com.kunzisoft.hardware.key;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class VirtualChallengeHelper {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int BASE_64_FLAGS = Base64.NO_PADDING | Base64.NO_WRAP;

    @NonNull
    public static String bytes2String(@NonNull byte[] bytes) {
        return Base64.encodeToString(bytes, BASE_64_FLAGS);
    }

    @NonNull
    public static byte[] string2Bytes(@NonNull String text) {
        return Base64.decode(text, BASE_64_FLAGS);
    }

    @Nullable
    public static String calcHashString(@NonNull byte[] bytes) {
        byte[] hash = calcHash(bytes);
        return hash != null ? bytes2String(hash) : null;
    }

    @Nullable
    public static byte[] calcHash(@NonNull byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }
}
