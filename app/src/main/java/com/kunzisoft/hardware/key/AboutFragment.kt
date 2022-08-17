package com.kunzisoft.hardware.key

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import java.util.*

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.about_licence_text).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = HtmlCompat.fromHtml(getString(R.string.html_about_licence,
                Calendar.getInstance().get(Calendar.YEAR)),
                HtmlCompat.FROM_HTML_MODE_LEGACY)
        }

        view.findViewById<TextView>(R.id.about_privacy_text).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = HtmlCompat.fromHtml(getString(R.string.html_about_privacy),
                HtmlCompat.FROM_HTML_MODE_LEGACY)
        }

        view.findViewById<TextView>(R.id.about_contribution_text).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = HtmlCompat.fromHtml(getString(R.string.html_about_contribution),
                HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }
}