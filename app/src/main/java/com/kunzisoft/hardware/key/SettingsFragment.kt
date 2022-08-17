package com.kunzisoft.hardware.key

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(getString(R.string.yubikey_pref))
            ?.setOnPreferenceClickListener { _ ->
                findNavController()
                    .navigate(R.id.yubikeySettingsFragment)
            true
        }
    }
}