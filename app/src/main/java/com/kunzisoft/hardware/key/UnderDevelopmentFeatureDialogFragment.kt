package com.kunzisoft.hardware.key

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment

/**
 * Custom Dialog that asks the user to download the pro version or make a donation.
 */
class UnderDevelopmentFeatureDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        activity?.let { activity ->
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(activity)
            val stringBuilder = SpannableStringBuilder()
            stringBuilder.append(HtmlCompat.fromHtml(getString(R.string.html_text_dev_feature), HtmlCompat.FROM_HTML_MODE_LEGACY)).append("\n\n")
                .append(HtmlCompat.fromHtml(getString(R.string.html_text_dev_feature_contibute), HtmlCompat.FROM_HTML_MODE_LEGACY)).append(" ")
                .append(HtmlCompat.fromHtml(getString(R.string.html_text_dev_feature_encourage), HtmlCompat.FROM_HTML_MODE_LEGACY))
            builder.setPositiveButton(R.string.contribute) { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.keepro_url))
                        )
                    )
                } catch (e: Exception) {
                    Log.e("Link", "Unable to open link", e)
                }
            }
            builder.setMessage(stringBuilder)
            // Create the AlertDialog object and return it
            return builder.create()
        }
        return super.onCreateDialog(savedInstanceState)
    }
}