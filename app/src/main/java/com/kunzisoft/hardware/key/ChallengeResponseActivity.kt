package com.kunzisoft.hardware.key

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.SoundPool
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.view.Window
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kunzisoft.hardware.key.databinding.ActivityChallengeBinding
import com.kunzisoft.hardware.yubikey.Slot
import com.kunzisoft.hardware.yubikey.challenge.UsbYubiKey
import com.kunzisoft.hardware.yubikey.challenge.YubiKey
import kotlinx.coroutines.*
import kotlin.experimental.or


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
    ConnectionManager.YubiKeyUsbUnplugReceiver {

    private lateinit var binding: ActivityChallengeBinding

    private lateinit var connectionManager: ConnectionManager
    private lateinit var slotPreferenceManager: SlotPreferenceManager

    private var selectedSlot: Slot = Slot.CHALLENGE_HMAC_2
    private var purpose: String? = null
    private var challenge: ByteArray? = null

    private var newIntentReceive: Intent? = null

    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private lateinit var soundPool: SoundPool
    private var endSoundID: Int = 0

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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        soundPool = SoundPool.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
            )
            .build()
        endSoundID = soundPool.load(this, R.raw.end, 1)

        connectionManager = ConnectionManager(this)
        slotPreferenceManager = SlotPreferenceManager(this)
        val connectionMethods = connectionManager.getSupportedConnectionMethods(this)
        if (connectionMethods.isUsbSupported && connectionMethods.isNfcSupported) {
            setText(R.string.attach_or_swipe_yubikey)
        } else if (connectionMethods.isUsbSupported) {
            setText(R.string.attach_yubikey)
        } else if (connectionMethods.isNfcSupported) {
            setText(R.string.swipe_yubikey)
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

        connectionManager.waitForYubiKey(this)
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
        binding.slotChipGroup.visibility = View.GONE

         lifecycleScope.launch {
             withContext(Dispatchers.IO) {
                 val asyncResult: Deferred<ByteArray?> = async {
                     try {
                         yubiKey.challengeResponse(
                             selectedSlot,
                             challenge!!
                         )
                     } catch (e: Exception) {
                         Log.e(TAG, "Error during challenge-response request", e)
                         null
                     }
                 }
                 withContext(Dispatchers.Main) {
                     val response = asyncResult.await()
                     if (response != null) {
                         notifySuccess()
                         slotPreferenceManager.setPreferredSlot(
                             purpose,
                             selectedSlot
                         )
                         val result = Intent()
                         result.putExtra(RESPONSE_TAG, response)
                         this@ChallengeResponseActivity.setResult(RESULT_OK, result)
                         finish()
                     } else {
                         connectionManager.waitForYubiKeyUnplug(
                             this@ChallengeResponseActivity,
                             this@ChallengeResponseActivity
                         )
                         setText(R.string.unplug_yubikey, true)
                     }
                     binding.slotChipGroup.visibility = View.GONE
                 }
             }
        }
    }

    override fun onYubiKeyUnplugged() {
        recreate()
    }

    private fun notifySuccess() {
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> soundPool.play(endSoundID, 1f, 1f, 0, 0, 1f)
            AudioManager.RINGER_MODE_VIBRATE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        200,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    }

    private fun setText(@StringRes stringRes: Int,
                        error: Boolean = false) {
        if (error) {
            binding.waiting.visibility = View.GONE
            binding.failure.visibility = View.VISIBLE
            binding.slotChipGroup.visibility = View.GONE
        }
        binding.info.setText(stringRes)
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
