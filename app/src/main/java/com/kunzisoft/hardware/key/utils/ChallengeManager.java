package com.kunzisoft.hardware.key.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ChallengeManager {
    private static final String CHALLENGE_STORAGE_NAME = "virtual_challenge_results";

    @NonNull
    private final SharedPreferences challengeStorage;

    public ChallengeManager(@NonNull Context context) {
        this(context.getSharedPreferences(
                CHALLENGE_STORAGE_NAME, Context.MODE_PRIVATE));
    }

    public ChallengeManager(@NonNull SharedPreferences challengeStorage) {
        this.challengeStorage = challengeStorage;
    }

    public void clearAllChallenges() {
        challengeStorage.edit()
                .clear()
                .apply();
    }

    public boolean hasChallenge(@Nullable byte[] challenge) throws IllegalStateException {
        return challenge != null
                && hasChallenge(getChallengeHashByChallenge(challenge));
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

    @NonNull
    public static String getChallengeHashByChallenge(@NonNull byte[] challenge) throws IllegalStateException {
        String challengeHash = ByteHelper.calcHashString(challenge);
        if (challengeHash == null) {
            throw new IllegalStateException("unable to calculate challenge hash for challenge");
        }
        return challengeHash;
    }

    public void setEncryptedResponseAsChallenge(@NonNull byte[] challenge,
                                                @NonNull byte[] encryptedResponse)
            throws IllegalStateException {
        setEncryptedResponseAsChallengeHash(getChallengeHashByChallenge(challenge), encryptedResponse);
    }

    public void setEncryptedResponseAsChallengeHash(@NonNull String challengeHash,
                                                    @NonNull byte[] encryptedResponse) {
        String encryptedResponseAsText = ByteHelper.bytes2String(encryptedResponse);
        setEncryptedChallengeResponse(challengeHash, encryptedResponseAsText);
    }

    @NonNull
    public byte[] getEncryptedResponseByChallengeHash(@NonNull String challengeHash) throws
            IllegalArgumentException {
        String encryptedResponseAsText = getEncryptedChallengeResponse(challengeHash);
        if (encryptedResponseAsText == null) {
            throw new IllegalArgumentException("no response for challenge hash: " + challengeHash);
        }
        return ByteHelper.string2Bytes(encryptedResponseAsText);
    }

    @NonNull
    public byte[] getEncryptedResponseByChallenge(@NonNull byte[] challenge) throws
            IllegalArgumentException, IllegalStateException {
        return getEncryptedResponseByChallengeHash(getChallengeHashByChallenge(challenge));
    }
}
