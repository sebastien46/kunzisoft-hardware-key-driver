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
import com.kunzisoft.hardware.key.databinding.ActivityFidoBinding
import kotlinx.coroutines.*
import us.q3q.fidok.FIDOkCallbacks
import us.q3q.fidok.PureJVMCryptoProvider
import us.q3q.fidok.ctap.*
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.nfc.AndroidNFCDevice
import us.q3q.fidok.usb.AndroidUSBHIDDevice
import us.q3q.fidok.webauthn.AuthenticatorSelectionCriteria
import us.q3q.fidok.webauthn.PublicKeyCredentialCreationOptions
import us.q3q.fidok.webauthn.PublicKeyCredentialRequestOptions
import kotlin.random.Random


/**
 * May be invoked by Android apps using the
 * `"android.fido.intent.action.HMAC_SECRET_CHALLENGE_RESPONSE"` intent to send a challenge
 * to a FIDO2 Authenticator and receive the response.
 *
 *
 * The challenge must be passed in an extra `byte[] challenge`. The Relying Party ID must be passed in
 * an extra `String rpId`. The number of different credentials provided (default 0) is in `int numCredentials`,
 * and each credential is sequentially in a `byte[] credential_#` starting from `credential_0`.
 * Upon successful completion, the activity returns an extra `byte[] response` in the result intent.
 */
class FIDOHMACActivity : AppCompatActivity(),
    ConnectionManager.FidoConnectReceiver,
    ConnectionManager.FidoDisconnectReceiver,
    ConnectionManager.UsbPermissionDeniedReceiver {

    private lateinit var binding: ActivityFidoBinding

    private lateinit var connectionManager: ConnectionManager
    private lateinit var slotPreferenceManager: SlotPreferenceManager

    private var rpId: String? = null
    private var challenge_1: ByteArray? = null
    private var challenge_2: ByteArray? = null
    private var fidoClientData: ByteArray? = null
    private var credentials: List<ByteArray>? = null
    private var activityType: HmacActivityType  = HmacActivityType.CREATE

    private var newIntentReceive: Intent? = null

    private lateinit var keySoundManager: KeySoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        binding = ActivityFidoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        activityType = when (this.intent.action) {
            "android.fido.intent.action.HMAC_SECRET_CHALLENGE_RESPONSE" ->
                HmacActivityType.GET
            "android.fido.intent.action.HMAC_SECRET_CREATE" ->
                HmacActivityType.CREATE
            else -> {
                setText(R.string.invalid_intent, true)
                return
            }
        }

        rpId = this.intent.getStringExtra(RP_ID_TAG)

        fidoClientData = this.intent.getByteArrayExtra(FIDO_CLIENT_DATA_TAG) ?: Random.nextBytes(32)

        if (activityType == HmacActivityType.GET) {
            val chal1 = this.intent.getByteArrayExtra(CHALLENGE_1_TAG)
            val chal2 = this.intent.getByteArrayExtra(CHALLENGE_2_TAG)

            val numCredentials = this.intent.getIntExtra(NUM_CREDENTIALS_TAG, 0)
            credentials = (0..<numCredentials).mapNotNull {
                this.intent.getByteArrayExtra("${CREDENTIAL_ID_TAG_PREFIX}${it}")
            }

            if (chal1 == null || chal1.size != 32 || rpId == null || (chal2 != null && chal2.size != 32)) {
                setText(R.string.invalid_challenge, true)
                return
            }

            challenge_1 = chal1
            challenge_2 = chal2

            binding.rpIdView.text = getString(R.string.rp_id_get_display, rpId)
        } else {
            if (rpId == null) {
                setText(R.string.invalid_challenge, true)
                return
            }

            binding.rpIdView.text = getString(R.string.rp_id_create_display, rpId)
        }

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

        binding.retryButton.setOnClickListener {
            recreate()
        }

        binding.pinEntry.requestFocus()

        connectionManager.waitForFIDO(this)
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

    private suspend fun doHmacGet(library: FIDOkLibrary): HmacResult? {
        val chal1 = challenge_1
        val chal2 = challenge_1
        if (chal1 == null) {
            // binding.retryButton.visibility = View.VISIBLE
            return null
        }

        val hmacExtensionValue = hashMapOf<String, ByteArray>()
        hmacExtensionValue["salt1"] = chal1
        if (chal2 != null) {
            hmacExtensionValue["salt2"] = chal2
        }

        val r = library.webauthn().get(
            PublicKeyCredentialRequestOptions(
                challenge = fidoClientData!!,
                rpId = rpId,
                allowCredentials = credentials?.map {
                    PublicKeyCredentialDescriptor(it)
                } ?: listOf(),
                userVerification = "required",
                extensions = mapOf(
                    "hmacGetSecret" to hmacExtensionValue
                )
            )
        )

        val hmacGetResult = r.clientExtensionResults["hmacGetSecret"] as Map<String, ByteArray>
        val res1 = hmacGetResult["output1"]
        val res2 = hmacGetResult["output2"]

        return HmacResult(
            result1 = res1,
            result2 = res2,
            credentialId = r.rawId
        )
    }

    private suspend fun doHmacCreate(library: FIDOkLibrary): HmacResult? {
        val r = library.webauthn().create(
            PublicKeyCredentialCreationOptions(
                challenge = fidoClientData!!,
                rp = PublicKeyCredentialRpEntity(
                    id = rpId,
                    name = rpId
                ),
                user = PublicKeyCredentialUserEntity(
                    id = Random.nextBytes(32),
                    name = "android-hardware-key-user",
                    displayName = "android-hardware-key-user"
                ),
                extensions = mapOf(
                    "hmacCreateSecret" to true,
                    "credentialProtectionPolicy" to "userVerificationRequired"
                ),
                authenticatorSelectionCriteria = AuthenticatorSelectionCriteria(
                    userVerification = "preferred"
                )
            )
        )

        if (r.clientExtensionResults["hmacCreateSecret"] != true) {
            setText(R.string.no_hmac_created, true)
            return null
        }

        return HmacResult(
            result1 = null,
            result2 = null,
            credentialId = r.rawId,
        )
    }

    override fun onAuthenticatorConnected(authenticator: AuthenticatorDevice) {
        if (authenticator is AndroidUSBHIDDevice)
            binding.info.setText(R.string.press_button)

         val pin = binding.pinEntry.text.toString()
         binding.pinEntry.text.clear()
         binding.pinEntryGroup.visibility = View.INVISIBLE

         lifecycleScope.launch {
             withContext(Dispatchers.IO) {
                 if (pin.isEmpty()) {
                     setText(R.string.pin_missing, true)
                     return@withContext
                 }

                 val fidoLibrary = FIDOkLibrary.init(
                     cryptoProvider = PureJVMCryptoProvider(),
                     authenticatorAccessors = listOf(
                         object : AuthenticatorListing {
                             override fun listDevices(): List<AuthenticatorDevice> {
                                 return listOf(authenticator)
                             }
                         },
                     ),
                     callbacks = object : FIDOkCallbacks {
                         override suspend fun collectPin(client: CTAPClient?): String? {
                             return pin
                         }
                     }
                 )

                 val asyncResult: Deferred<HmacResult?> = async {
                     try {
                         when (activityType) {
                             HmacActivityType.GET -> {
                                 doHmacGet(fidoLibrary)
                             }
                             HmacActivityType.CREATE -> {
                                 doHmacCreate(fidoLibrary)
                             }
                         }
                     } catch (e: Exception) {
                         withContext(Dispatchers.Main) {
                             Log.e(TAG, "Error during FIDO2 request", e)
                             if (e is CTAPError) {
                                 setText(e.message ?: "CTAP Error", true)
                             } else {
                                 when (authenticator) {
                                     is AndroidUSBHIDDevice -> {
                                         connectionManager.waitForFIDOAuthenticatorUnplug(
                                             this@FIDOHMACActivity,
                                             this@FIDOHMACActivity
                                         )
                                         setText(R.string.error_unplug_yubikey, true)
                                     }
                                     is AndroidNFCDevice -> {
                                         if (e.cause is TagLostException) {
                                             setText(R.string.error_yubikey_slowly, true)
                                         } else {
                                             setText(R.string.error_yubikey_configure, true)
                                         }
                                     }
                                     else -> {}
                                 }
                             }
                         }
                         null
                     }
                 }
                 withContext(Dispatchers.Main) {
                     val response = asyncResult.await()
                     if (response != null) {
                         if (authenticator is AndroidNFCDevice) {
                             keySoundManager.notifySuccess()
                         }
                         val result = Intent()
                         if (response.result1 != null) {
                             result.putExtra(RESPONSE_1_TAG, response.result1)
                         }
                         if (response.result2 != null) {
                             result.putExtra(RESPONSE_2_TAG, response.result2)
                         }
                         result.putExtra(RESPONSE_CREDENTIAL_TAG, response.credentialId)
                         this@FIDOHMACActivity.setResult(RESULT_OK, result)
                         finish()
                     }
                 }
             }
        }
    }

    override fun onAuthenticatorDisconnected() {
        recreate()
    }

    override fun onUsbPermissionDenied() {
        connectionManager.waitForFIDOAuthenticatorUnplug(
            this@FIDOHMACActivity,
            this@FIDOHMACActivity
        )
        setText(R.string.usb_permission_denied, true)
    }

    private fun setText(@StringRes stringRes: Int,
                        error: Boolean = false) {
        binding.info.setText(stringRes)
        if (error) {
            binding.waiting.visibility = View.INVISIBLE
            binding.failure.visibility = View.VISIBLE
            binding.retryButton.visibility = View.VISIBLE
            // hidePinEntry()
        }
    }

    private fun setText(string: String,
                        error: Boolean = false) {
        binding.info.text = string
        if (error) {
            binding.waiting.visibility = View.INVISIBLE
            binding.failure.visibility = View.VISIBLE
            binding.retryButton.visibility = View.VISIBLE
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // To call broadcast receiver in onResume
        if (intent != null) {
            newIntentReceive = intent
        }
    }

    internal data class HmacResult(
        val result1: ByteArray?,
        val result2: ByteArray?,
        val credentialId: ByteArray
    )

    internal enum class HmacActivityType {
        CREATE,
        GET
    }

    companion object {
        val TAG: String = FIDOHMACActivity::class.java.simpleName

        const val FIDO_CLIENT_DATA_TAG = "clientData"

        const val CHALLENGE_1_TAG = "challenge"
        const val CHALLENGE_2_TAG = "challenge_2"
        const val RP_ID_TAG = "rpId"
        const val CREDENTIAL_ID_TAG_PREFIX = "credential_"
        const val NUM_CREDENTIALS_TAG = "numCredentials"

        const val RESPONSE_1_TAG = "response"
        const val RESPONSE_2_TAG = "response_2"
        const val RESPONSE_CREDENTIAL_TAG = "credentialId"
    }
}
