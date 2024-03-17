package com.kunzisoft.hardware.key;

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
import javax.crypto.spec.IvParameterSpec;

public class KeyManager {
    private static final String KEY_STORE = "AndroidKeyStore";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int AES_IV_SIZE = 16;

    private final KeyStore keyStore;

    public KeyManager() throws KeyStoreException {
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

    /** @noinspection UnusedReturnValue*/
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

    public byte[] encrypt(byte[] data, String secretKeyAlias)
            throws KeyStoreException, CryptException {
        return encrypt(data, getSecretKey(secretKeyAlias));
    }

    public byte[] encrypt(byte[] data, SecretKey secretKey)
            throws CryptException {
        try {
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data);

            if (iv.length != AES_IV_SIZE) {
                throw new CryptException("IV has invalid size: " + iv.length);
            }

            final int encryptedDataSize = encryptedData.length;
            data = new byte[encryptedDataSize + AES_IV_SIZE];
            System.arraycopy(encryptedData, 0, data, 0, encryptedDataSize);
            System.arraycopy(iv, 0, data, encryptedDataSize, AES_IV_SIZE);
            return data;
        } catch (NoSuchPaddingException ex) {
            throw new CryptException("no such padding", ex);
        } catch (IllegalBlockSizeException ex) {
            throw new CryptException("invalid block size", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new CryptException("no such algorithm", ex);
        } catch (BadPaddingException ex) {
            throw new CryptException("bad padding", ex);
        } catch (InvalidKeyException ex) {
            throw new CryptException("invalid key", ex);
        } catch (NullPointerException ex) {
            throw new CryptException("null as input", ex);
        }
    }

    public byte[] decrypt(byte[] data, String secretKeyAlias)
            throws KeyStoreException, CryptException, BadPaddingException {
        return decrypt(data, getSecretKey(secretKeyAlias));
    }

    public byte[] decrypt(byte[] data, SecretKey secretKey)
            throws CryptException, BadPaddingException {
        try {
            final int dataSize = data.length - AES_IV_SIZE;
            final byte[] iv = new byte[AES_IV_SIZE];
            System.arraycopy(data, dataSize, iv, 0, AES_IV_SIZE);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            return cipher.doFinal(data, 0, dataSize);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new CryptException("invalid algorithm parameter", ex);
        } catch (NoSuchPaddingException ex) {
            throw new CryptException("no such padding", ex);
        } catch (IllegalBlockSizeException ex) {
            throw new CryptException("invalid block size", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new CryptException("no such algorithm", ex);
        } catch (InvalidKeyException ex) {
            throw new CryptException("invalid key", ex);
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