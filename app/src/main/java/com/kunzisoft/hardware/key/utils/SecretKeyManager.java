package com.kunzisoft.hardware.key.utils;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecretKeyManager {
    private static final String KEY_STORE = "AndroidKeyStore";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int AES_TAG_SIZE = 128;

    private final KeyStore keyStore;

    public SecretKeyManager() throws KeyStoreException {
        keyStore = KeyStore.getInstance(KEY_STORE);
        try {
            keyStore.load(null);
        } catch (CertificateException | IOException | NoSuchAlgorithmException ex) {
            throw new KeyStoreException("unable to load KeyStore", ex);
        }
    }

    public boolean hasSecretKey(String secretKeyAlias) throws KeyStoreException {
        return keyStore.containsAlias(secretKeyAlias);
    }

    /**
     * @noinspection UnusedReturnValue
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static SecretKey createSecretKey(String secretKeyAlias) throws KeyStoreException {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE);

            KeyGenParameterSpec.Builder keyGenSpecBuilder = new KeyGenParameterSpec.Builder(
                    secretKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                keyGenSpecBuilder = keyGenSpecBuilder.setInvalidatedByBiometricEnrollment(true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                keyGenSpecBuilder = keyGenSpecBuilder.setUserAuthenticationParameters(
                        0, KeyProperties.AUTH_BIOMETRIC_STRONG);
            }

            generator.init(keyGenSpecBuilder.build());
            return generator.generateKey();
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException |
                 NoSuchProviderException ex) {
            throw new KeyStoreException("unable to create key: " + secretKeyAlias, ex);
        }
    }

    public void deleteSecretKey(String secretKeyAlias) throws KeyStoreException {
        keyStore.deleteEntry(secretKeyAlias);
    }

    public SecretKey getSecretKey(String secretKeyAlias) throws KeyStoreException {
        try {
            return (SecretKey) keyStore.getKey(secretKeyAlias, null);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException ex) {
            throw new KeyStoreException("unable to fetch key: " + secretKeyAlias, ex);
        }
    }

    private static Cipher createCipher() throws CryptException {
        try {
            return Cipher.getInstance(AES_MODE);
        } catch (NoSuchPaddingException ex) {
            throw new CryptException("no such padding", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new CryptException("no such algorithm", ex);
        }
    }

    public Cipher createEncryptCipher(String secretKeyAlias)
            throws KeyStoreException, CryptException {
        return createEncryptCipher(getSecretKey(secretKeyAlias));
    }

    public static Cipher createEncryptCipher(SecretKey secretKey) throws CryptException {
        try {
            Cipher cipher = createCipher();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher;
        } catch (InvalidKeyException ex) {
            throw new CryptException("invalid key", ex);
        }
    }

    public static byte[] encrypt(byte[] data, Cipher cipher) throws CryptException {
        try {
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data);

            final int ivSize = iv.length;
            final int encryptedDataSize = encryptedData.length;

            if ((ivSize & ~0xFF) != 0) {
                throw new CryptException("unable to store IV size (" + ivSize + ") in result");
            }

            data = new byte[encryptedDataSize + ivSize + 1];
            System.arraycopy(encryptedData, 0, data, 0, encryptedDataSize);
            System.arraycopy(iv, 0, data, encryptedDataSize, ivSize);
            data[data.length - 1] = (byte) ivSize;
            return data;
        } catch (IllegalBlockSizeException ex) {
            throw new CryptException("invalid block size", ex);
        } catch (BadPaddingException ex) {
            throw new CryptException("bad padding", ex);
        } catch (NullPointerException ex) {
            throw new CryptException("null as input", ex);
        }
    }

    public Cipher createDecryptCipher(String secretKeyAlias, byte[] iv)
            throws KeyStoreException, CryptException {
        return createDecryptCipher(getSecretKey(secretKeyAlias), iv);
    }

    public static Cipher createDecryptCipher(SecretKey secretKey, byte[] iv) throws CryptException {
        try {
            Cipher cipher = createCipher();
            GCMParameterSpec ivSpec = new GCMParameterSpec(AES_TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            return cipher;
        } catch (InvalidAlgorithmParameterException ex) {
            throw new CryptException("invalid algorithm parameter", ex);
        } catch (InvalidKeyException ex) {
            throw new CryptException("invalid key", ex);
        }
    }

    private static int getIvSize(byte[] data) {
        return (int) data[data.length - 1] & 0xFF;
    }

    private static int getDataSize(byte[] data, int ivSize) {
        return data.length - ivSize - 1;
    }

    public static byte[] getIv(byte[] data) {
        final int ivSize = getIvSize(data);
        final int dataSize = getDataSize(data, ivSize);
        final byte[] iv = new byte[ivSize];
        System.arraycopy(data, dataSize, iv, 0, ivSize);
        return iv;
    }

    public static byte[] decrypt(byte[] data, Cipher cipher) throws CryptException, BadPaddingException {
        try {
            final int dataSize = getDataSize(data, getIvSize(data));
            return cipher.doFinal(data, 0, dataSize);
        } catch (IllegalBlockSizeException ex) {
            throw new CryptException("invalid block size", ex);
        } catch (IndexOutOfBoundsException ex) {
            throw new CryptException("invalid data length", ex);
        } catch (NullPointerException ex) {
            throw new CryptException("null as input", ex);
        }
    }

    public static class CryptException extends Exception {
        private CryptException(String message) {
            super(message);
        }

        private CryptException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}