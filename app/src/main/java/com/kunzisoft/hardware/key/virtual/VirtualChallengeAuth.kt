package com.kunzisoft.hardware.key.virtual

import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kunzisoft.hardware.key.R
import com.kunzisoft.hardware.key.utils.BioManager
import com.kunzisoft.hardware.key.utils.ChallengeManager
import com.kunzisoft.hardware.key.utils.SecretKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStoreException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class VirtualChallengeAuth(
    private val activity: FragmentActivity,
    private val secretKeyAlias: String,
) {
    private val challengeManager = ChallengeManager(activity)
    private val bioManager = BioManager(activity)
    private val secretKeyManager = try {
        SecretKeyManager()
    } catch (ex: KeyStoreException) {
        null
    }

    private fun showFailedAuthMessage() {
        Log.e(TAG, "failed to start authentication")
        Toast.makeText(activity, R.string.error_authenticate_biometrics, Toast.LENGTH_LONG).show()
    }

    private fun showFailedStoreMessage(ex: Exception) {
        Log.e(TAG, "failed to store challenge response", ex)

        val message = String.format(
            activity.getString(R.string.error_store_virtual_challenge),
            (ex.javaClass.simpleName + ": " + ex.localizedMessage)
        )
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    fun hasChallenge(challenge: ByteArray): Boolean {
        return challengeManager.hasChallenge(challenge)
    }

    @Throws(Exception::class)
    suspend fun challenge2Response(challenge: ByteArray): ByteArray {
        if (secretKeyManager == null) {
            throw IllegalStateException("unable to create secret key manager")
        }

        val encryptedResponse = challengeManager.getEncryptedResponseByChallenge(challenge)
        val iv = SecretKeyManager.getIv(encryptedResponse)
        val cipher = secretKeyManager.createDecryptCipher(secretKeyAlias, iv)
        val authObj = BiometricPrompt.CryptoObject(cipher)

        val responseResultFuture = CompletableFuture<Any>()
        withContext(Dispatchers.Main) {
            bioManager.biometricAuthenticate(
                BioManager.createAuthCallback({ authObj, _ ->
                    // On success:
                    activity.lifecycleScope.launch {
                        responseResultFuture.complete(try {
                            val authenticatedCipher = authObj?.cipher!!
                            withContext(Dispatchers.IO) {
                                SecretKeyManager.decrypt(encryptedResponse, authenticatedCipher)
                            }
                        } catch (tr: Throwable) {
                            tr
                        })
                    }
                }, {
                    // On Fail:
                    responseResultFuture.complete(
                        AuthFailException(
                            activity.getString(R.string.error_authenticate_biometrics)
                        )
                    )
                }),
                authObj
            )
        }

        return withContext(Dispatchers.IO) {
            when (val result = responseResultFuture.get()) {
                is ByteArray -> result
                is Exception -> throw result
                else -> throw IllegalStateException("unknown result type")
            }
        }
    }

    suspend fun registerChallengeResponse(challenge: ByteArray, response: ByteArray) {
        if (secretKeyManager == null) return

        try {
            val cipher = secretKeyManager.createEncryptCipher(secretKeyAlias)
            val authObj = BiometricPrompt.CryptoObject(cipher)

            val notifier = CountDownLatch(1)
            withContext(Dispatchers.Main) {
                bioManager.biometricAuthenticate(
                    BioManager.createAuthCallback({ authObj, _ ->
                        // On success:
                        activity.lifecycleScope.launch {
                            try {
                                val authenticatedCipher = authObj?.cipher!!
                                withContext(Dispatchers.IO) {
                                    val encryptedResponse = SecretKeyManager.encrypt(response, authenticatedCipher)
                                    challengeManager.setEncryptedResponseAsChallenge(challenge, encryptedResponse)
                                }
                            } catch (ex: Exception) {
                                withContext(Dispatchers.Main) {
                                    showFailedStoreMessage(ex)
                                }
                            } finally {
                                notifier.countDown()
                            }
                        }
                    }, {
                        // On Fail:
                        notifier.countDown()
                        showFailedAuthMessage()
                    }),
                    authObj
                )
            }
            withContext(Dispatchers.IO) {
                notifier.await()
            }
        } catch (ex: Exception) {
            withContext(Dispatchers.Main) {
                showFailedStoreMessage(ex)
            }
        }
    }

    open class AuthFailException : Exception {
        constructor()
        constructor(message: String?) : super(message)
        constructor(cause: Throwable?) : super(cause)
    }

    companion object {
        val TAG: String = VirtualChallengeAuth::class.java.simpleName


    }
}