package com.kunzisoft.hardware.key

import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.kunzisoft.hardware.yubikey.Slot

class YubikeySettingsFragment : PreferenceFragmentCompat() {
    private lateinit var virtualChallengeManager: VirtualChallengeManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.yubikey_preferences, rootKey)
        virtualChallengeManager = VirtualChallengeManager(requireContext())

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

        val secretKeyAlias = VirtualChallengeManager.YUBICO_SECRET_KEY_ALIAS
        val clearVirtualChallengePref = findPreference<Preference>(getString(R.string.clear_virtual_challenges_pref))?.apply {
            isEnabled = virtualChallengeManager.hasSecretKey(secretKeyAlias)

            setOnPreferenceClickListener {
                if (clearVirtualChallenge()) {
                    initVirtualChallenge()
                }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(getString(R.string.virtual_challenge_pref))?.apply {
            isChecked = clearVirtualChallengePref?.isEnabled ?: false

            setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    if (initVirtualChallenge()) {
                        clearVirtualChallengePref?.isEnabled = true
                    }
                } else {
                    if (clearVirtualChallenge()) {
                        clearVirtualChallengePref?.isEnabled = false
                    }
                }
                true
            }
        }
    }

    private fun initVirtualChallenge(): Boolean {
        virtualChallengeManager.clearAllChallenges()
        val success = virtualChallengeManager.createSecretKey(VirtualChallengeManager.YUBICO_SECRET_KEY_ALIAS)

        if (!success) {
            Toast.makeText(requireContext(), R.string.error_init_virtual_challenge_key,
                Toast.LENGTH_LONG).show()
        }
        return success
    }

    private fun clearVirtualChallenge(): Boolean {
        virtualChallengeManager.clearAllChallenges()
        val success = virtualChallengeManager.deleteSecretKey(VirtualChallengeManager.YUBICO_SECRET_KEY_ALIAS)

        if (!success) {
            Toast.makeText(requireContext(), R.string.error_clear_virtual_challenge_key,
                Toast.LENGTH_LONG).show()
        }
        return success
    }
}