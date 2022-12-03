package com.kunzisoft.hardware.key

import android.content.Context
import androidx.preference.PreferenceManager
import com.kunzisoft.hardware.yubikey.Slot

/**
 * Manages the user's preferences for YubiKey slots and a string that
 * uniquely identifies the purpose of the requested action.
 */
internal class SlotPreferenceManager(context: Context) {
    private val slotPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Gets the preferred slot for a given unique purpose identifier.
     *
     * @param identifier  Uniquely identifies the purpose of the request.
     * If null or "" is passed, the default identifier will
     * be used.
     * @return Returns the slot that should be pre-selected for the given purpose.
     */
    fun getPreferredSlot(identifier: String?): Slot {
        if (isEmptyIdentifier(identifier)) {
            return getPreferredSlot(DEFAULT_IDENTIFIER)
        }
        val preference = slotPreferences.getInt(identifier, -1)
        if (preference == -1) {
            return Slot.CHALLENGE_HMAC_2
        }

        // We could do a binary search here, but since we only have a very small amount of slots...
        for (slot in Slot.values()) {
            if (slot.address.toInt() == preference)
                return slot
        }
        throw IllegalStateException()
    }

    /**
     * Updates the preferred slot for a given unique purpose identifier. Should be called after and
     * only if the requested YubiKey transaction was completed successfully. This method updates the
     * preference asynchronously and can thus be safely called from the UI thread.
     *
     * @param identifier Uniquely identifies the purpose of the request.
     * If null or "" is passed, the default identifier will
     * be used.
     * @param slot       The slot to set as new preference for the given purpose.
     */
    fun setPreferredSlot(identifier: String?, slot: Slot) {
        if (isEmptyIdentifier(identifier)) {
            setPreferredSlot(DEFAULT_IDENTIFIER, slot)
            return
        }
        val editor = slotPreferences.edit()
        editor.putInt(identifier, slot.address.toInt())
        editor.apply()
    }

    fun getDefaultSlot(): Slot {
        return getPreferredSlot(DEFAULT_IDENTIFIER)
    }

    fun setDefaultSlot(slot: Slot) {
        setPreferredSlot(null, slot)
    }

    private fun isEmptyIdentifier(identifier: String?): Boolean {
        return identifier == null || identifier == ""
    }

    companion object {
        private const val DEFAULT_IDENTIFIER = "default"
    }
}