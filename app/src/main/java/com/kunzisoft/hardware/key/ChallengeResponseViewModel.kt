package com.kunzisoft.hardware.key

import android.content.Context
import android.content.Intent
import android.nfc.TagLostException
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.AndroidUiDispatcher
import app.cash.molecule.RecompositionClock
import app.cash.molecule.launchMolecule
import com.kunzisoft.hardware.yubikey.Slot
import com.kunzisoft.hardware.yubikey.challenge.NfcYubiKey
import com.kunzisoft.hardware.yubikey.challenge.UsbYubiKey
import com.kunzisoft.hardware.yubikey.challenge.YubiKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ChallengeResponseUiEvent
data class SelectSlot(val selectedSlot: SelectedSlot) : ChallengeResponseUiEvent
object Retry : ChallengeResponseUiEvent

enum class ConnectionMethod {
    USB, NFC, VIRTUAL
}

sealed interface ConnectionState {
    object NoSupportedConnectionMethods : ConnectionState
    data class Waiting(val connectionMethods: ConnectionMethods) : ConnectionState
    object UsbPermissionDenied : ConnectionState
    data class YubiKeyConnected(val connectionMethod: ConnectionMethod) : ConnectionState
}

enum class Error {
    YUBIKEY_SLOWLY,
    YUBIKEY_CONFIGURE,
    USB_UNKNOWN_ERROR,
}

class ChallengeResponseViewModel @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    @Assisted intent: Intent,
    @Assisted events: Flow<ChallengeResponseUiEvent>,
    @Assisted private val connectionManager: ConnectionManager,
    private val slotPreferenceManager: SlotPreferenceManager,
) : ViewModel(),
    ConnectionManager.YubiKeyConnectReceiver,
    ConnectionManager.YubiKeyUsbUnplugReceiver,
    ConnectionManager.UsbPermissionDeniedReceiver {

    @AssistedFactory
    interface Factory {
        fun build(
            intent: Intent,
            events: Flow<ChallengeResponseUiEvent>,
            connectionManager: ConnectionManager,
        ): ChallengeResponseViewModel
    }

    private val challenge: ByteArray? =
        intent.getByteArrayExtra(ChallengeResponseActivity.CHALLENGE_TAG)
    private val purpose: String? = intent.getStringExtra(ChallengeResponseActivity.SLOT_TAG)

    private val isChallengeValid: Boolean = challenge != null && challenge.isNotEmpty()
    private var slot: Slot by mutableStateOf(slotPreferenceManager.getPreferredSlot(purpose))
    private var connectionState: ConnectionState by mutableStateOf(ConnectionState.NoSupportedConnectionMethods)
    private var error: Error? by mutableStateOf(null)
    private var done: Boolean by mutableStateOf(false)

    private val moleculeScope =
        CoroutineScope(viewModelScope.coroutineContext + AndroidUiDispatcher.Main)
    val uiModel: StateFlow<ChallengeResponseUiModel> = moleculeScope.launchMolecule(
        clock = RecompositionClock.ContextClock
    ) {
        ChallengeResponseUiPresenter(
            isChallengeValid,
            slot,
            connectionState,
            error,
            done,
        )
    }

    var response: ByteArray? = null
        private set

    init {
        if (isChallengeValid) {
            viewModelScope.launch {
                events.collect { event ->
                    when (event) {
                        is SelectSlot -> {
                            slot = when (event.selectedSlot) {
                                SelectedSlot.SLOT_1 -> Slot.CHALLENGE_HMAC_1
                                SelectedSlot.SLOT_2 -> Slot.CHALLENGE_HMAC_2
                            }
                        }
                        is Retry -> retry()
                    }
                }
            }

            connectionState =
                ConnectionState.Waiting(connectionManager.getSupportedConnectionMethods(context))
            connectionManager.waitForYubiKey(this)
            connectionManager.registerUsbPermissionDeniedReceiver(this)
        }
    }

    fun handleNewReceivedIntent(context: Context, intent: Intent) {
        connectionManager.onReceive(context, intent)
    }

    override fun onYubiKeyConnected(yubiKey: YubiKey) {
        val connectionMethod = if (yubiKey is UsbYubiKey) {
            ConnectionMethod.USB
        } else if (yubiKey is NfcYubiKey) {
            ConnectionMethod.NFC
        } else {
            ConnectionMethod.VIRTUAL
        }
        connectionState = ConnectionState.YubiKeyConnected(connectionMethod)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val asyncResult: Deferred<ByteArray?> = async {
                    try {
                        yubiKey.challengeResponse(
                            slot,
                            challenge!!
                        )
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Log.e(TAG, "Error during challenge-response request", e)
                            if (yubiKey is UsbYubiKey) {
                                error = Error.USB_UNKNOWN_ERROR
                                connectionManager.waitForYubiKeyUnplug(
                                    context,
                                    this@ChallengeResponseViewModel
                                )
                            } else {
                                error = if (e.cause is TagLostException) {
                                    Error.YUBIKEY_SLOWLY
                                } else {
                                    Error.YUBIKEY_CONFIGURE
                                }
                            }
                        }
                        null
                    }
                }
                withContext(Dispatchers.Main) {
                    response = asyncResult.await()
                    if (response != null) {
                        slotPreferenceManager.setPreferredSlot(
                            purpose,
                            slot
                        )
                        done = true
                    }
                }
            }
        }
    }

    override fun onYubiKeyUnplugged() {
        retry()
    }

    override fun onUsbPermissionDenied() {
        connectionManager.waitForYubiKeyUnplug(context, this)
        connectionState = ConnectionState.UsbPermissionDenied
    }

    private fun retry() {
        error = null
        connectionState =
            ConnectionState.Waiting(connectionManager.getSupportedConnectionMethods(context))
        connectionManager.waitForYubiKey(this)
        connectionManager.registerUsbPermissionDeniedReceiver(this)
        connectionManager.initConnection()
    }

    companion object {
        val TAG: String = ChallengeResponseViewModel::class.java.simpleName
    }
}
