package com.kunzisoft.hardware.yubikey

/**
 * Enumeration of feature slots available on a YubiKey. (Taken from Yubico's C driver implementation)
 */
enum class Slot(val address: Byte) {
    DUMMY(0x0.toByte()),
    CONFIG_1(0x1.toByte()),
    NAV(0x2.toByte()),
    CONFIG_2(0x3.toByte()),
    UPDATE_1(0x4.toByte()),
    UPDATE_2(0x5.toByte()),
    SWAP(0x6.toByte()),
    NDEF_1(0x8.toByte()),
    NDEF_2(0x9.toByte()),
    DEVICE_SERIAL(0x10.toByte()),
    DEVICE_CONFIGURATION(0x11.toByte()),
    SCAN_MAP(0x12.toByte()),
    YUBIKEY_4_CAPABILITIES(0x13.toByte()),
    CHALLENGE_OTP_1(0x20.toByte()),
    CHALLENGE_OTP_2(0x28.toByte()),
    CHALLENGE_HMAC_1(0x30.toByte()),
    CHALLENGE_HMAC_2(0x38.toByte());

    /**
     * Checks whether a slot may be used for challenge-response.
     *
     * @return true, if a slot may be used for challenge-response.
     */
    val isChallengeResponseSlot: Boolean
        get() = this == CHALLENGE_HMAC_1 || this == CHALLENGE_HMAC_2

    /**
     * Checks whether a slot may be used for challenge-response and throws an exception if not.
     */
    fun ensureChallengeResponseSlot() {
        if (!isChallengeResponseSlot)
            throw YubiKeyException("Invalid slot")
    }

    companion object {
        val DEFAULT = CHALLENGE_HMAC_2

        fun fromString(slotString: String): Slot {
            return values().find { slotString == it.toString() } ?: DEFAULT
        }

        fun toStringArray(): Array<String> {
            return values().map { it.toString() }.toTypedArray()
        }

        fun toChallengeStringArray(): Array<String> {
            return arrayOf(CHALLENGE_HMAC_1.toString(), CHALLENGE_HMAC_2.toString())
        }
    }
}