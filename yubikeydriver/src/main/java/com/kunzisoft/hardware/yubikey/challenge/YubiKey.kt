package com.kunzisoft.hardware.yubikey.challenge

import kotlin.Throws
import com.kunzisoft.hardware.yubikey.YubiKeyException
import com.kunzisoft.hardware.yubikey.Slot

/**
 * Interface that defines the YubiKey features that must be supported by the different driver
 * implementations
 */
interface YubiKey {
    /**
     * Sends a challenge to the YubiKey and returns the response received. May wait for the user to
     * press the button on the YubiKey, depending on its configuration. Thus, this method should
     * not be called on the UI thread to ensure good user experience.
     *
     * @param slot      The YubiKey feature slot to use. Must be either
     * [Slot.CHALLENGE_HMAC_1] or [Slot.CHALLENGE_HMAC_2].
     * @param challenge Challenge bytes to send to the YubiKey.
     * @return The response from the YubiKey.
     * @throws YubiKeyException     Depending on the driver implementation, additional exceptions may
     * be thrown.
     */
    @Throws(YubiKeyException::class)
    fun challengeResponse(slot: Slot, challenge: ByteArray): ByteArray

    companion object {
        /**
         * Length of a response to a challenge-response request (in bytes)
         */
        const val CHALLENGE_RESPONSE_LENGTH = 20
    }
}