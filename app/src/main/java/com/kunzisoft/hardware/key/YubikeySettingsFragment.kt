package com.kunzisoft.hardware.key

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class YubikeySettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.yubikey_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO Set preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        //sharedPreferences.getString(getString(R.string.yubikey_pref))
    }
}