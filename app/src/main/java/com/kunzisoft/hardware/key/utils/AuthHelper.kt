package com.kunzisoft.hardware.key.utils

import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthHelper(private val bioManager: BioManager) {
    data class AuthResult(val cryptoObject: BiometricPrompt.CryptoObject?)
    data class AuthException(val errorCode: Int?, private val msg: String) : Exception(msg)

    suspend fun authBiometricWithCipher(cipher: Cipher): Cipher {
        val authObj = BiometricPrompt.CryptoObject(cipher)
        return authBiometric(authObj).cryptoObject!!.cipher!!
    }

    suspend fun authBiometric(authObj: BiometricPrompt.CryptoObject?): AuthResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        resetBioPrompt()
                        continuation.resume(AuthResult(result.cryptoObject))
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        resetBioPrompt()
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(
                                AuthException(null, "auth failed")
                            )
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        resetBioPrompt()
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(
                                AuthException(errorCode, errString.toString())
                            )
                        }
                    }
                }

                cancelBioPrompt()
                ongoingBioPrompt = bioManager.biometricAuthenticate(callback, authObj)
                continuation.invokeOnCancellation {
                    cancelBioPrompt()
                }
            }
        }

    companion object {
        private var ongoingBioPrompt: BiometricPrompt? = null

        fun cancelBioPrompt() {
            ongoingBioPrompt?.cancelAuthentication()
            resetBioPrompt()
        }

        private fun resetBioPrompt() {
            ongoingBioPrompt = null
        }
    }
}
