package com.kunzisoft.hardware.key

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.kunzisoft.hardware.yubikey.Slot

class YubikeySettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.yubikey_preferences, rootKey)

        val slotPreferenceManager = SlotPreferenceManager(requireContext())

        findPreference<ListPreference>(getString(R.string.default_slot_pref))?.apply {
            val slots = Slot.toChallengeStringArray()
            entries = slots
            entryValues = slots
            setValueIndex(slots.indexOf(slotPreferenceManager.getDefaultSlot().toString()))

            setOnPreferenceChangeListener { _, newValue ->
                slotPreferenceManager.setDefaultSlot(Slot.fromString(newValue.toString()))
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(getString(R.string.virtual_key_pref))?.apply {
            setOnPreferenceClickListener {
                this.isChecked = false
                UnderDevelopmentFeatureDialogFragment()
                    .show(requireActivity().supportFragmentManager, "underDevFeatureDialog")
                false
            }
        }
    }
}