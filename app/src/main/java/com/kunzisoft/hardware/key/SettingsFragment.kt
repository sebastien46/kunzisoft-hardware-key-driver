package com.kunzisoft.hardware.key

import android.content.Context
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var keySoundManager: KeySoundManager

    override fun onAttach(context: Context) {
        super.onAttach(context)
        keySoundManager = KeySoundManager(context)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(getString(R.string.solokey_pref))
            ?.setOnPreferenceClickListener {
                UnderDevelopmentFeatureDialogFragment()
                    .show(requireActivity().supportFragmentManager, "underDevFeatureDialog")
                true
            }

        findPreference<Preference>(getString(R.string.yubikey_pref))
            ?.setOnPreferenceClickListener { _ ->
                findNavController()
                    .navigate(R.id.yubikeySettingsFragment)
            true
        }

        findPreference<Preference>(getString(R.string.sound_pref))
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    keySoundManager.emitSuccessSound()
                }
                true
            }

        findPreference<Preference>(getString(R.string.vibration_pref))
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    keySoundManager.emitSuccessVibration()
                }
                true
            }
    }
}