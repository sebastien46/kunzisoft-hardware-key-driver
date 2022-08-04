package com.kunzisoft.hardware.yubikey.challenge

import android.content.Context
import com.kunzisoft.hardware.yubikey.Slot
import com.kunzisoft.hardware.yubikey.YubiKeyException
import java.io.IOException

/**
 * Manual Recovery YubiKey implementation.
 */
class DummyYubiKey(private val mContext: Context) : YubiKey {

    @Throws(YubiKeyException::class)
    override fun challengeResponse(slot: Slot, challenge: ByteArray): ByteArray {
        return try {
            // Wait for the user to tap the virtual button
            Thread.sleep(2000)
            mContext.assets.open("response.bin").readBytes()

        } catch (e: IOException) {
            ByteArray(20)
        }
    }
}