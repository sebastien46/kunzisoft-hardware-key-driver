package com.kunzisoft.hardware.yubikey.challenge

import android.nfc.tech.IsoDep
import com.kunzisoft.hardware.yubikey.Slot
import com.kunzisoft.hardware.yubikey.YubiKeyException
import com.kunzisoft.hardware.yubikey.apdu.command.iso.SelectFileApdu
import com.kunzisoft.hardware.yubikey.apdu.command.ykoath.PutApdu
import java.io.IOException

/**
 * NFC YubiKey driver implementation.
 */
class NfcYubiKey
/**
 * Should only be instantiated by the [com.kunzisoft.hardware.key.ConnectionManager].
 *
 * @param tag YubiKey NEOs provide the functionality of ISO-DEP (14443-4) tags.
 */(private val tag: IsoDep) : YubiKey {
    @Throws(IOException::class)
    private fun ensureConnected() {
        if (!tag.isConnected) {
            tag.connect()
            tag.timeout = 10000
        }
    }

    @Throws(YubiKeyException::class)
    override fun challengeResponse(slot: Slot, challenge: ByteArray): ByteArray {
        slot.ensureChallengeResponseSlot()
        return try {
            ensureConnected()
            val selectFileApdu = SelectFileApdu(
                SelectFileApdu.SelectionControl.DF_NAME_DIRECT,
                SelectFileApdu.RecordOffset.FIRST_RECORD,
                CHALLENGE_AID
            )
            if (!selectFileApdu.parseResponse(tag.transceive(selectFileApdu.build())).isSuccess) {
                throw YubiKeyException("Failed operation")
            }
            val putApdu = PutApdu(slot, challenge)
            val putResponseApdu = putApdu.parseResponse(tag.transceive(putApdu.build()))
            if (!putResponseApdu.isSuccess) {
                throw YubiKeyException("Failed operation")
            }
            tag.close()
            putResponseApdu.result
        } catch (e: IOException) {
            throw YubiKeyException(e)
        }
    }

    companion object {
        /**
         * The scheme of the URI passed in the initial NDEF messages sent by YubiKey NEOs.
         */
        const val YUBIKEY_NEO_NDEF_SCHEME = "https"

        /**
         * The host name of the URI passed in the initial NDEF messages sent by YubiKey NEOs.
         */
        const val YUBIKEY_NEO_NDEF_HOST = "my.yubico.com"

        /**
         * ISO 7816 Application ID for the challenge-response feature of YubiKeys.
         */
        private val CHALLENGE_AID = byteArrayOf(0xa0.toByte(), 0x00, 0x00, 0x05, 0x27, 0x20, 0x01)
    }
}