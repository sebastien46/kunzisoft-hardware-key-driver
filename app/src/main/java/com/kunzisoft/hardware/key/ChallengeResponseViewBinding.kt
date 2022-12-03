package com.kunzisoft.hardware.key

import android.view.View
import androidx.annotation.StringRes
import com.kunzisoft.hardware.key.databinding.ActivityChallengeBinding
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

fun ActivityChallengeBinding.events() = callbackFlow {
    slot1.setOnClickListener {
        slot1.isChecked = true
        trySend(SelectSlot(SelectedSlot.SLOT_1))
    }
    slot2.setOnClickListener {
        slot2.isChecked = true
        trySend(SelectSlot(SelectedSlot.SLOT_2))
    }
    retryButton.setOnClickListener { trySend(Retry) }

    awaitClose()
}

fun ActivityChallengeBinding.bind(challengeResponseUiModel: ChallengeResponseUiModel) {
    when (challengeResponseUiModel) {
        is InvalidChallenge -> {
            info.setText(R.string.invalid_challenge)
            waiting.visibility = View.INVISIBLE
            slotChipGroup.visibility = View.INVISIBLE
            failure.visibility = View.VISIBLE
            retryButton.visibility = View.INVISIBLE
        }
        is NoSupportedConnectionMethods -> {
            info.setText(R.string.no_supported_connection_method)
            waiting.visibility = View.INVISIBLE
            slotChipGroup.visibility = View.INVISIBLE
            failure.visibility = View.VISIBLE
            retryButton.visibility = View.INVISIBLE
        }
        is WaitingConnection -> {
            @StringRes
            val infoRes: Int = when (challengeResponseUiModel.waitingConnectionInfo) {
                WaitingConnectionInfo.ATTACH_OR_SWIPE_YUBIKEY -> R.string.attach_or_swipe_yubikey
                WaitingConnectionInfo.ATTACH_YUBIKEY -> R.string.attach_yubikey
                WaitingConnectionInfo.SWIPE_YUBIKEY -> R.string.swipe_yubikey
                WaitingConnectionInfo.VIRTUAL_KEY_GENERATE -> R.string.virtual_key_generate
            }
            info.setText(infoRes)
            waiting.visibility = View.INVISIBLE
            slotChipGroup.visibility = View.VISIBLE
            when (challengeResponseUiModel.selectedSlot) {
                SelectedSlot.SLOT_1 -> {
                    slot1.isChecked = true
                    slot2.isChecked = false
                }
                SelectedSlot.SLOT_2 -> {
                    slot1.isChecked = false
                    slot2.isChecked = true
                }
            }
            failure.visibility = View.INVISIBLE
            retryButton.visibility = View.INVISIBLE
        }
        is UsbPermissionDenied -> {
            info.setText(R.string.usb_permission_denied)
            waiting.visibility = View.INVISIBLE
            slotChipGroup.visibility = View.INVISIBLE
            failure.visibility = View.VISIBLE
            retryButton.visibility = View.INVISIBLE
        }
        is Communicating -> {
            @StringRes
            val infoRes: Int = when (challengeResponseUiModel.communicationInfo) {
                CommunicationInfo.PRESS_YUBIKEY_BUTTON -> R.string.press_button
                CommunicationInfo.PLEASE_WAIT -> R.string.please_wait
            }
            info.setText(infoRes)
            waiting.visibility = View.VISIBLE
            slotChipGroup.visibility = View.INVISIBLE
            failure.visibility = View.INVISIBLE
            retryButton.visibility = View.INVISIBLE
        }
        is ErrorUnplugYubiKey -> {
            info.setText(R.string.error_unplug_yubikey)
            waiting.visibility = View.INVISIBLE
            slotChipGroup.visibility = View.INVISIBLE
            failure.visibility = View.VISIBLE
            retryButton.visibility = View.INVISIBLE
        }
        is ErrorRetry -> {
            @StringRes
            val infoRes: Int = when (challengeResponseUiModel.errorRetryInfo) {
                ErrorRetryInfo.YUBIKEY_SLOWLY -> R.string.error_yubikey_slowly
                ErrorRetryInfo.YUBIKEY_CONFIGURE -> R.string.error_yubikey_configure
            }
            info.setText(infoRes)
            waiting.visibility = View.INVISIBLE
            slotChipGroup.visibility = View.INVISIBLE
            failure.visibility = View.INVISIBLE
            retryButton.visibility = View.VISIBLE
        }
        is Done -> {}
    }
}
