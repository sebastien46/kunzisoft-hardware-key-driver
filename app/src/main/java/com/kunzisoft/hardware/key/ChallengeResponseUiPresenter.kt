package com.kunzisoft.hardware.key

import androidx.compose.runtime.Composable
import com.kunzisoft.hardware.yubikey.Slot

enum class WaitingConnectionInfo {
    ATTACH_OR_SWIPE_YUBIKEY,
    ATTACH_YUBIKEY,
    SWIPE_YUBIKEY,
    VIRTUAL_KEY_GENERATE,
}

enum class CommunicationInfo {
    PRESS_YUBIKEY_BUTTON,
    PLEASE_WAIT,
}

enum class ErrorRetryInfo {
    YUBIKEY_SLOWLY,
    YUBIKEY_CONFIGURE,
}

enum class SelectedSlot {
    SLOT_1, SLOT_2
}

sealed interface ChallengeResponseUiModel
object InvalidChallenge : ChallengeResponseUiModel
object NoSupportedConnectionMethods : ChallengeResponseUiModel
data class WaitingConnection(
    val waitingConnectionInfo: WaitingConnectionInfo,
    val selectedSlot: SelectedSlot
) : ChallengeResponseUiModel

object UsbPermissionDenied : ChallengeResponseUiModel
data class Communicating(val communicationInfo: CommunicationInfo) : ChallengeResponseUiModel
object ErrorUnplugYubiKey : ChallengeResponseUiModel
data class ErrorRetry(val errorRetryInfo: ErrorRetryInfo) : ChallengeResponseUiModel
data class Done(val shouldNotifySuccess: Boolean) : ChallengeResponseUiModel

@Composable
fun ChallengeResponseUiPresenter(
    isChallengeValid: Boolean,
    slot: Slot,
    connectionState: ConnectionState,
    error: Error?,
    done: Boolean,
): ChallengeResponseUiModel =
    if (!isChallengeValid) {
        InvalidChallenge
    } else if (error != null) {
        when (error) {
            Error.YUBIKEY_SLOWLY -> ErrorRetry(ErrorRetryInfo.YUBIKEY_SLOWLY)
            Error.YUBIKEY_CONFIGURE -> ErrorRetry(ErrorRetryInfo.YUBIKEY_CONFIGURE)
            Error.USB_UNKNOWN_ERROR -> ErrorUnplugYubiKey
        }
    } else {
        when (connectionState) {
            is ConnectionState.NoSupportedConnectionMethods -> NoSupportedConnectionMethods
            is ConnectionState.Waiting -> {
                val connectionMethods = connectionState.connectionMethods
                val selectedSlot: SelectedSlot = if (slot == Slot.CHALLENGE_HMAC_1) {
                    SelectedSlot.SLOT_1
                } else {
                    SelectedSlot.SLOT_2
                }

                if (connectionMethods.isUsbSupported && connectionMethods.isNfcSupported) {
                    WaitingConnection(
                        WaitingConnectionInfo.ATTACH_OR_SWIPE_YUBIKEY,
                        selectedSlot,
                    )
                } else if (connectionMethods.isUsbSupported) {
                    WaitingConnection(WaitingConnectionInfo.ATTACH_YUBIKEY, selectedSlot)
                } else if (connectionMethods.isNfcSupported) {
                    WaitingConnection(WaitingConnectionInfo.SWIPE_YUBIKEY, selectedSlot)
                } else {
                    WaitingConnection(WaitingConnectionInfo.VIRTUAL_KEY_GENERATE, selectedSlot)
                }
            }
            is ConnectionState.UsbPermissionDenied -> UsbPermissionDenied
            is ConnectionState.YubiKeyConnected -> when (connectionState.connectionMethod) {
                ConnectionMethod.USB -> if (done) {
                    Done(shouldNotifySuccess = false)
                } else {
                    Communicating(CommunicationInfo.PRESS_YUBIKEY_BUTTON)
                }
                ConnectionMethod.NFC, ConnectionMethod.VIRTUAL -> if (done) {
                    Done(shouldNotifySuccess = true)
                } else {
                    Communicating(CommunicationInfo.PLEASE_WAIT)
                }
            }
        }
    }
