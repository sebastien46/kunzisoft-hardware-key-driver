package com.kunzisoft.hardware.key

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.kunzisoft.hardware.key.utils.BioManager
import com.kunzisoft.hardware.key.utils.ChallengeManager
import com.kunzisoft.hardware.key.utils.SecretKeyHelper
import com.kunzisoft.hardware.yubikey.Slot

class YubikeySettingsFragment : PreferenceFragmentCompat() {
    private lateinit var secretKeyManager: SecretKeyHelper
    private lateinit var challengeManager: ChallengeManager
    private lateinit var secretKeyAlias: String

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.yubikey_preferences, rootKey)

        secretKeyManager = SecretKeyHelper()
        challengeManager = ChallengeManager(requireContext())
        secretKeyAlias = ConnectionManager.YUBICO_SECRET_KEY_ALIAS

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

        // Virtual challenge response:
        val hasAtLeastSdkVersion23 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        val hasBiometricAuth = BioManager.canDoBiometricAuthentication(requireContext())
        val canUseVirtualChallengeResponse = hasAtLeastSdkVersion23 && hasBiometricAuth

        val clearVirtualChallengePref = findPreference<Preference>(getString(R.string.clear_virtual_challenges_pref))?.apply {
            isEnabled = canUseVirtualChallengeResponse && secretKeyManager.hasSecretKey(secretKeyAlias)
            isVisible = canUseVirtualChallengeResponse

            setOnPreferenceClickListener {
                if (clearVirtualChallenge()) {
                    initVirtualChallenge()
                }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(getString(R.string.virtual_challenge_pref))?.apply {
            isChecked = clearVirtualChallengePref?.isEnabled ?: false
            isEnabled = canUseVirtualChallengeResponse

            if (!hasAtLeastSdkVersion23) {
                setSummary(R.string.virtual_challenge_description_fail_sdk_version_23)
            } else if (!hasBiometricAuth) {
                setSummary(R.string.virtual_challenge_description_fail_no_biometrics)
            }

            setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    if (initVirtualChallenge()) {
                        clearVirtualChallengePref?.isEnabled = true
                        true
                    } else {
                        false
                    }
                } else {
                    if (clearVirtualChallenge()) {
                        clearVirtualChallengePref?.isEnabled = false
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun initVirtualChallenge(): Boolean {
        challengeManager.clearAllChallenges()
        val success = secretKeyManager.createSecretKey(secretKeyAlias)

        if (!success) {
            Toast.makeText(requireContext(), R.string.error_init_virtual_challenge_key,
                Toast.LENGTH_LONG).show()
        }
        return success
    }

    private fun clearVirtualChallenge(): Boolean {
        challengeManager.clearAllChallenges()
        val success = secretKeyManager.deleteSecretKey(secretKeyAlias)

        if (!success) {
            Toast.makeText(requireContext(), R.string.error_clear_virtual_challenge_key,
                Toast.LENGTH_LONG).show()
        }
        return success
    }
}