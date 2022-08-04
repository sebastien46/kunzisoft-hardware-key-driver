package com.kunzisoft.hardware.key

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.kunzisoft.hardware.yubikey.Slot

/**
 * Manages the user's preferences for YubiKey slots based upon the called activity and a string that
 * uniquely identifies the purpose of the requested action set by the calling activity.
 */
internal class SlotPreferenceManager(activity: Activity) {
    private val slotPreferences: SharedPreferences

    /**
     * Gets the preferred slot for a given unique purpose identifier.
     *
     * @param identifier  Uniquely identifies the purpose of the request. Should be set by
     * the invoking activity. If null or "" is passed, the default identifier will
     * be used.
     * @param defaultSlot The default slot to return in case no previous preferred slot is saved for
     * the given purpose.
     * @return Returns the slot that should be pre-selected for the given purpose.
     */
    fun getPreferredSlot(identifier: String?, defaultSlot: Slot): Slot {
        if (isEmptyIdentifier(identifier)) {
            return getPreferredSlot(DEFAULT_IDENTIFIER, defaultSlot)
        }
        val preference = slotPreferences.getInt(identifier, -1)
        if (preference == -1) {
            return defaultSlot
        }

        // We could do a binary search here, but since we only have a very small amount of slots...
        for (slot in Slot.values()) {
            if (slot.address.toInt() == preference) return slot
        }
        throw IllegalStateException()
    }

    /**
     * Updates the preferred slot for a given unique purpose identifier. Should be called after and
     * only if the requested YubiKey transaction was completed successfully. This method updates the
     * preference asynchronously and can thus be safely called from the UI thread.
     *
     * @param identifier Uniquely identifies the purpose of the request. Should be set by
     * the invoking activity. If null or "" is passed, the default identifier will
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

    private fun isEmptyIdentifier(identifier: String?): Boolean {
        return identifier == null || identifier == ""
    }

    companion object {
        private const val SLOT_PREFERENCES_FILE_NAME = "slot_preferences"
        private const val DEFAULT_IDENTIFIER = "default"
    }

    /**
     * Should be instantiated exactly once by an activity that wants to store a slot preference.
     *
     * @param activity Required to store slot preferences are stored for each distinct activity.
     */
    init {
        slotPreferences = activity.getSharedPreferences(
            activity.localClassName + "_" + SLOT_PREFERENCES_FILE_NAME,
            Context.MODE_PRIVATE
        )
    }
}