package com.kunzisoft.hardware.key.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.kunzisoft.hardware.key.R;

import java.util.concurrent.Executor;

public class BioManager {
    private static final int AUTHENTICATION_TYPE = BiometricManager.Authenticators.BIOMETRIC_STRONG;

    private final FragmentActivity activity;

    public BioManager(@NonNull FragmentActivity activity) {
        this.activity = activity;
    }

    public static boolean canDoBiometricAuthentication(@NonNull Context context) {
        return BiometricManager.from(context)
                .canAuthenticate(AUTHENTICATION_TYPE) == BiometricManager.BIOMETRIC_SUCCESS;
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
                .setTitle(activity.getString(R.string.authenticate_biometrics))
                .setAllowedAuthenticators(AUTHENTICATION_TYPE)
                .setNegativeButtonText(activity.getString(R.string.authenticate_biometrics_cancel))
                .build();
    }

    @NonNull
    public BiometricPrompt biometricAuthenticate(@NonNull BiometricPrompt.AuthenticationCallback callback) {
        return biometricAuthenticate(callback, null);
    }

    @NonNull
    public BiometricPrompt biometricAuthenticate(@NonNull BiometricPrompt.AuthenticationCallback callback,
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
    public static BiometricPrompt.AuthenticationCallback createAuthCallback(@NonNull OnAuthResult onResult) {
        return createAuthCallback(onResult, null);
    }

    @NonNull
    public static BiometricPrompt.AuthenticationCallback createAuthCallback(@NonNull OnAuthResult onResult,
                                                                            @Nullable Runnable onFail) {
        return new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                onResult.handle(result.getCryptoObject(), result.getAuthenticationType());
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                if (onFail != null) onFail.run();
            }

            @Override
            public void onAuthenticationFailed() {
                if (onFail != null) onFail.run();
            }
        };
    }

    public interface OnAuthResult {
        void handle(@Nullable BiometricPrompt.CryptoObject result, int authType);
    }
}
