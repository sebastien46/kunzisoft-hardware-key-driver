package com.kunzisoft.hardware.key;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.kunzisoft.hardware.yubikey.Slot;
import com.kunzisoft.hardware.yubikey.YubiKeyException;
import com.kunzisoft.hardware.yubikey.challenge.YubiKey;

import java.security.KeyStoreException;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;

public class VirtualChallengeManager {
    private static final String TAG = "VirtualKeyManager";
    private static final int AUTHENTICATION_TYPE = BiometricManager.Authenticators.BIOMETRIC_STRONG;
    private static final String CHALLENGE_STORAGE_NAME = "virtual_challenge_results";

    @NonNull
    public static final String YUBICO_SECRET_KEY_ALIAS = "yubico_virtual_key";

    private final KeyManager keyManager;
    private final SharedPreferences challengeStorage;
    private final BiometricManager biometricManager;

    private final String authenticatePromptTitle;
    private final String authenticatePromptCancel;

    public VirtualChallengeManager(@NonNull Context context) {
        KeyManager keyManager;
        try {
            keyManager = new KeyManager();
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to create key manager");
            keyManager = null;
        }
        this.keyManager = keyManager;

        this.challengeStorage = context.getSharedPreferences(
                CHALLENGE_STORAGE_NAME, Context.MODE_PRIVATE);
        this.biometricManager = BiometricManager.from(context);

        this.authenticatePromptTitle = context.getString(R.string.authenticate_biometrics);
        this.authenticatePromptCancel = context.getString(R.string.authenticate_biometrics_cancel);
    }

    private boolean checkIfNoKeyManager() {
        if (keyManager == null) {
            Log.e(TAG, "was unable to create key manager");
            return true;
        } else return false;
    }

    @Nullable
    public KeyManager getKeyManager() {
        return keyManager;
    }

    public void clearAllChallenges() {
        challengeStorage.edit()
                .clear()
                .apply();
    }

    public boolean hasChallenge(@Nullable String challengeHash) {
        return challengeHash != null
                && challengeStorage.contains(challengeHash);
    }

    @Nullable
    private String getEncryptedChallengeResponse(@NonNull String challengeHash) {
        return challengeStorage.getString(challengeHash, null);
    }

    private void setEncryptedChallengeResponse(@NonNull String challengeHash,
                                               @NonNull String encryptedChallengeResponse) {
        challengeStorage.edit()
                .putString(challengeHash, encryptedChallengeResponse)
                .apply();
    }

    public boolean createSecretKey(@NonNull String secretKeyAlias) {
        if (checkIfNoKeyManager()) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyManager.createSecretKey(secretKeyAlias);
                return true;
            } // else {}
            // TODO: Create secret key if Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            return false;
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to create secret key for key manager", ex);
            return false;
        }
    }

    public boolean hasSecretKey(@NonNull String secretKeyAlias) {
        if (checkIfNoKeyManager()) return false;
        try {
            return keyManager.hasSecretKey(secretKeyAlias);
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to check if secret key exists in key manager", ex);
            return false;
        }
    }

    public boolean deleteSecretKey(@NonNull String secretKeyAlias) {
        if (checkIfNoKeyManager()) return false;
        try {
            keyManager.deleteSecretKey(secretKeyAlias);
            return true;
        } catch (KeyStoreException ex) {
            Log.e(TAG, "unable to remove secret key from key manager", ex);
            return false;
        }
    }

    public boolean canAuthenticateWith(@NonNull String secretKeyAlias) {
        return canDoBiometricAuthentication()
                && hasSecretKey(secretKeyAlias);
    }

    private boolean canDoBiometricAuthentication() {
        return biometricManager.canAuthenticate(AUTHENTICATION_TYPE)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    @NonNull
    public BiometricPrompt buildBiometricPrompt(@NonNull FragmentActivity activity,
                                                @NonNull BiometricPrompt.AuthenticationCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        return new BiometricPrompt(activity, executor, callback);
    }

    @NonNull
    public BiometricPrompt.PromptInfo buildBiometricPromptInfo() {
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle(authenticatePromptTitle)
                .setAllowedAuthenticators(AUTHENTICATION_TYPE)
                .setNegativeButtonText(authenticatePromptCancel)
                .build();
    }

    @NonNull
    public BiometricPrompt biometricAuthenticate(@NonNull FragmentActivity activity,
                                                 @NonNull BiometricPrompt.AuthenticationCallback callback) {
        return biometricAuthenticate(activity, callback, null);
    }

    @NonNull
    public BiometricPrompt biometricAuthenticate(@NonNull FragmentActivity activity,
                                                 @NonNull BiometricPrompt.AuthenticationCallback callback,
                                                 @Nullable BiometricPrompt.CryptoObject crypto) {
        BiometricPrompt prompt = buildBiometricPrompt(activity, callback);
        if (crypto != null) {
            prompt.authenticate(buildBiometricPromptInfo(), crypto);
        } else {
            prompt.authenticate(buildBiometricPromptInfo());
        }
        return prompt;
    }

    @NonNull
    public VirtualResponseKey asKey(String secretKeyAlias) {
        return new VirtualResponseKey(this, secretKeyAlias);
    }

    public static class VirtualResponseKey implements YubiKey {
        @NonNull
        private final VirtualChallengeManager virtualChallengeManager;
        @NonNull
        private final String secretKeyAlias;

        public VirtualResponseKey(@NonNull VirtualChallengeManager virtualChallengeManager,
                                  @NonNull String secretKeyAlias) {
            this.virtualChallengeManager = Objects.requireNonNull(virtualChallengeManager);
            this.secretKeyAlias = Objects.requireNonNull(secretKeyAlias);
        }

        @NonNull
        private KeyManager getKeyManager() throws NullPointerException {
            return Objects.requireNonNull(
                    virtualChallengeManager.getKeyManager(),
                    "challenge manager has no key manager"
            );
        }

        @NonNull
        private String getChallengeHashByChallenge(@NonNull byte[] challenge) throws IllegalStateException {
            String challengeHash = VirtualChallengeHelper.calcHashString(challenge);
            if (challengeHash == null) {
                throw new IllegalStateException("unable to calculate challenge hash for challenge");
            }
            return challengeHash;
        }

        @NonNull
        public byte[] encryptResponse(@NonNull byte[] response) throws
                NullPointerException,
                KeyManager.CryptException, KeyStoreException {
            return getKeyManager().encrypt(response, secretKeyAlias);
        }

        public void storeResponseAsChallengeHash(@NonNull String challengeHash, @NonNull byte[] response) throws
                NullPointerException,
                KeyManager.CryptException, KeyStoreException {
            byte[] encryptedChallengeResponse = encryptResponse(response);
            String encryptedChallengeResponseAsText = VirtualChallengeHelper.bytes2String(encryptedChallengeResponse);
            virtualChallengeManager.setEncryptedChallengeResponse(challengeHash, encryptedChallengeResponseAsText);
        }

        public void storeResponseAsChallenge(@NonNull byte[] challenge, @NonNull byte[] response) throws
                NullPointerException, IllegalStateException,
                KeyManager.CryptException, KeyStoreException {
            storeResponseAsChallengeHash(getChallengeHashByChallenge(challenge), response);
        }

        @NonNull
        public byte[] decryptResponse(@NonNull byte[] encryptedResponse) throws
                NullPointerException,
                KeyManager.CryptException, KeyStoreException, BadPaddingException {
            return getKeyManager().decrypt(encryptedResponse, secretKeyAlias);
        }

        @NonNull
        public byte[] responseByChallengeHash(@NonNull String challengeHash) throws
                NullPointerException, IllegalArgumentException,
                KeyManager.CryptException, KeyStoreException, BadPaddingException {
            String encryptedChallengeResponseAsText = virtualChallengeManager
                    .getEncryptedChallengeResponse(challengeHash);
            if (encryptedChallengeResponseAsText == null) {
                throw new IllegalArgumentException("no response for challenge hash: " + challengeHash);
            }
            byte[] encryptedChallengeResponse = VirtualChallengeHelper.string2Bytes(encryptedChallengeResponseAsText);
            return decryptResponse(encryptedChallengeResponse);
        }

        @NonNull
        public byte[] responseByChallenge(@NonNull byte[] challenge) throws
                NullPointerException, IllegalArgumentException, IllegalStateException,
                KeyManager.CryptException, KeyStoreException, BadPaddingException {
            return responseByChallengeHash(getChallengeHashByChallenge(challenge));
        }

        @NonNull
        @Override
        public byte[] challengeResponse(@NonNull Slot slot, @NonNull byte[] challenge)
                throws YubiKeyException {
            try {
                return responseByChallenge(challenge);
            } catch (Exception ex) {
                throw new YubiKeyException(ex);
            }
        }
    }
}
