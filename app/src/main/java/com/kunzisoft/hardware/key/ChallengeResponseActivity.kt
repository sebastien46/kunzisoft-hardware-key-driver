package com.kunzisoft.hardware.key

import android.content.Intent
import android.nfc.TagLostException
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kunzisoft.hardware.key.databinding.ActivityChallengeBinding
import com.kunzisoft.hardware.yubikey.Slot
import com.kunzisoft.hardware.yubikey.challenge.NfcYubiKey
import com.kunzisoft.hardware.yubikey.challenge.UsbYubiKey
import com.kunzisoft.hardware.yubikey.challenge.YubiKey
import kotlinx.coroutines.*


/**
 * May be invoked by Android apps using the
 * `"android.yubikey.intent.action.CHALLENGE_RESPONSE"` intent to send a challenge
 * to a YubiKey and receive the response.
 *
 *
 * The challenge must be passed in an extra `byte[] challenge`. Upon successful completion,
 * the activity returns an extra `byte[] response` in the result intent. Optionally,
 * an extra `String purpose` may be passed in the intent to identify the purpose of the
 * challenge. The app will use this identifier to remember and pre-select the slot used for each
 * purpose.
 *
 */
class ChallengeResponseActivity : AppCompatActivity(),
    ConnectionManager.YubiKeyConnectReceiver,
    ConnectionManager.YubiKeyUsbUnplugReceiver,
    ConnectionManager.UsbPermissionDeniedReceiver {

    private lateinit var binding: ActivityChallengeBinding

    private lateinit var connectionManager: ConnectionManager
    private lateinit var slotPreferenceManager: SlotPreferenceManager

    private var selectedSlot: Slot = Slot.CHALLENGE_HMAC_2
    private var purpose: String? = null
    private var challenge: ByteArray? = null

    private var newIntentReceive: Intent? = null

    private lateinit var keySoundManager: KeySoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        binding = ActivityChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        challenge = this.intent.getByteArrayExtra(CHALLENGE_TAG)
        if (challenge == null || challenge!!.isEmpty()) {
            setText(R.string.invalid_challenge, true)
            return
        }

        purpose = this.intent.getStringExtra(SLOT_TAG)

        keySoundManager = KeySoundManager(this)

        connectionManager = ConnectionManager(this)
        slotPreferenceManager = SlotPreferenceManager(this)
        val connectionMethods = connectionManager.getSupportedConnectionMethods(this)
        if (connectionMethods.isUsbSupported && connectionMethods.isNfcSupported) {
            setText(R.string.attach_or_swipe_yubikey)
        } else if (connectionMethods.isUsbSupported) {
            setText(R.string.attach_yubikey)
        } else if (connectionMethods.isNfcSupported) {
            setText(R.string.swipe_yubikey)
        } else if (connectionMethods.isVirtualKeyConfigured) {
            setText(R.string.virtual_key_generate)
        } else {
            setText(R.string.no_supported_connection_method, true)
            return
        }

        selectedSlot = slotPreferenceManager.getPreferredSlot(purpose)
        selectSlot(selectedSlot)
        binding.slot1.setOnCheckedChangeListener { _, b ->
            if (b)
                selectSlot(Slot.CHALLENGE_HMAC_1)
        }
        binding.slot2.setOnCheckedChangeListener { _, b ->
            if (b)
                selectSlot(Slot.CHALLENGE_HMAC_2)
        }

        binding.retryButton.setOnClickListener {
            recreate()
        }

        connectionManager.waitForYubiKey(this)
        connectionManager.registerUsbPermissionDeniedReceiver(this)
    }

    override fun onResume() {
        super.onResume()
        // Call connection manager broadcast response
        newIntentReceive?.let {
            connectionManager.onReceive(this, it)
        }
        newIntentReceive = null
    }

    private fun selectSlot(slot: Slot) {
        selectedSlot = slot
        when (selectedSlot) {
            Slot.CHALLENGE_HMAC_1 -> {
                binding.slot1.isChecked = true
                binding.slot2.isChecked = false
            }
            Slot.CHALLENGE_HMAC_2 -> {
                binding.slot1.isChecked = false
                binding.slot2.isChecked = true
            }
            else -> {}
        }
    }

    override fun onYubiKeyConnected(yubiKey: YubiKey) {
        if (yubiKey is UsbYubiKey)
            binding.info.setText(R.string.press_button)
        hideSlotSelection()

         lifecycleScope.launch {
             withContext(Dispatchers.IO) {
                 val asyncResult: Deferred<ByteArray?> = async {
                     try {
                         yubiKey.challengeResponse(
                             selectedSlot,
                             challenge!!
                         )
                     } catch (e: Exception) {
                         withContext(Dispatchers.Main) {
                             Log.e(TAG, "Error during challenge-response request", e)
                             if (yubiKey is UsbYubiKey) {
                                 connectionManager.waitForYubiKeyUnplug(
                                     this@ChallengeResponseActivity,
                                     this@ChallengeResponseActivity
                                 )
                                 setText(R.string.error_unplug_yubikey, true)
                             }
                             if (yubiKey is NfcYubiKey) {
                                 if (e.cause is TagLostException) {
                                     setText(R.string.error_yubikey_slowly, true)
                                 } else {
                                     setText(R.string.error_yubikey_configure, true)
                                 }
                                 binding.retryButton.visibility = View.VISIBLE
                             }
                         }
                         null
                     }
                 }
                 withContext(Dispatchers.Main) {
                     val response = asyncResult.await()
                     if (response != null) {
                         keySoundManager.notifySuccess()
                         if (yubiKey is NfcYubiKey) {
                             askToUnpluggedNfc(response)
                         } else {
                             returnResponse(response)
                         }
                     }
                     hideSlotSelection()
                 }
             }
        }
    }

    private fun askToUnpluggedNfc(response: ByteArray?) {
        setText(R.string.success_unplug_nfc_key, false)
        binding.waiting.visibility = View.INVISIBLE
        hideSlotSelection()
        binding.okButton.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                visibility = View.GONE
                returnResponse(response)
            }
        }
    }

    private fun returnResponse(response: ByteArray?) {
        slotPreferenceManager.setPreferredSlot(
            purpose,
            selectedSlot
        )
        val result = Intent()
        result.putExtra(RESPONSE_TAG, response)
        this@ChallengeResponseActivity.setResult(RESULT_OK, result)
        finish()
    }

    override fun onYubiKeyUnplugged() {
        recreate()
    }

    override fun onUsbPermissionDenied() {
        connectionManager.waitForYubiKeyUnplug(
            this@ChallengeResponseActivity,
            this@ChallengeResponseActivity
        )
        setText(R.string.usb_permission_denied, true)
    }


    private fun hideSlotSelection() {
        binding.slotChipGroup.visibility = View.INVISIBLE
    }

    private fun setText(@StringRes stringRes: Int,
                        error: Boolean = false) {
        binding.info.setText(stringRes)
        if (error) {
            binding.waiting.visibility = View.INVISIBLE
            binding.failure.visibility = View.VISIBLE
            hideSlotSelection()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // To call broadcast receiver in onResume
        if (intent != null) {
            newIntentReceive = intent
        }
    }

    companion object {
        val TAG: String = ChallengeResponseActivity::class.java.simpleName

        const val CHALLENGE_TAG = "challenge"
        const val SLOT_TAG = "purpose"
        const val RESPONSE_TAG = "response"
    }
}
