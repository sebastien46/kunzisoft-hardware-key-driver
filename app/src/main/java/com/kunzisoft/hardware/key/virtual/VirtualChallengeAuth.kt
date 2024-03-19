package com.kunzisoft.hardware.key.virtual

import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.kunzisoft.hardware.key.R
import com.kunzisoft.hardware.key.utils.AuthHelper
import com.kunzisoft.hardware.key.utils.BioManager
import com.kunzisoft.hardware.key.utils.ChallengeManager
import com.kunzisoft.hardware.key.utils.SecretKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStoreException

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

    private fun showFailedAuthMessage(ex: Exception) {
        Log.e(TAG, "failed to start authentication", ex)
        Toast.makeText(activity, R.string.error_authenticate_biometrics, Toast.LENGTH_LONG).show()
    }

    private fun showFailedStoreMessage(ex: Exception) {
        Log.e(TAG, "failed to store challenge response", ex)

        val message = ex.localizedMessage?.let { message ->
            String.format(
                activity.getString(R.string.error_store_virtual_challenge),
                message.replaceFirstChar { firstChar -> firstChar.uppercase() }
            )
        }
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
        var cipher = secretKeyManager.createDecryptCipher(secretKeyAlias, iv)

        cipher = AuthHelper(bioManager).authBiometricWithCipher(cipher)

        return SecretKeyManager.decrypt(encryptedResponse, cipher)
    }

    suspend fun registerChallengeResponse(challenge: ByteArray, response: ByteArray) {
        if (secretKeyManager == null) return

        try {
            var cipher = secretKeyManager.createEncryptCipher(secretKeyAlias)

            cipher = AuthHelper(bioManager).authBiometricWithCipher(cipher)

            val encryptedResponse = SecretKeyManager.encrypt(response, cipher)
            challengeManager.setEncryptedResponseAsChallenge(challenge, encryptedResponse)
        } catch (ex: AuthHelper.AuthException) {
            withContext(Dispatchers.Main) {
                showFailedAuthMessage(ex)
            }
        } catch (ex: Exception) {
            withContext(Dispatchers.Main) {
                showFailedStoreMessage(ex)
            }
        }
    }

    companion object {
        val TAG: String = VirtualChallengeAuth::class.java.simpleName
    }
}
