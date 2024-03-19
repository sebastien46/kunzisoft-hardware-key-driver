package com.kunzisoft.hardware.key.virtual

import com.kunzisoft.hardware.yubikey.Slot
import com.kunzisoft.hardware.yubikey.YubiKeyException
import com.kunzisoft.hardware.yubikey.challenge.YubiKey

class VirtualChallengeResponseKey(
    private val virtualChallengeAuth: VirtualChallengeAuth,
) : YubiKey.Suspended {
    override fun isAvailable(challenge: ByteArray): Boolean {
        return virtualChallengeAuth.hasChallenge(challenge)
                && virtualChallengeAuth.doesSecretKeyExist()
    }

    @Throws(YubiKeyException::class)
    override suspend fun challengeResponse(slot: Slot, challenge: ByteArray): ByteArray {
        try {
            return virtualChallengeAuth.challenge2Response(challenge)
        } catch (ex: Exception) {
            throw YubiKeyException(ex)
        }
    }
}
