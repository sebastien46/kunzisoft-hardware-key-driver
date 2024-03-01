package com.kunzisoft.hardware.key

import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kunzisoft.hardware.key.databinding.ActivityChallengeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject


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
@AndroidEntryPoint
class ChallengeResponseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChallengeBinding

    @Inject
    lateinit var factory: ChallengeResponseViewModel.Factory
    private val viewModel: ChallengeResponseViewModel by viewModels {
        LambdaFactory {
            factory.build(this.intent, binding.events(), ConnectionManager(this))
        }
    }

    private var newIntentReceive: Intent? = null

    private lateinit var keySoundManager: KeySoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        binding = ActivityChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keySoundManager = KeySoundManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiModel.collect { uiModel ->
                    binding.bind(uiModel)

                    if (uiModel is Done) {
                        if (uiModel.shouldNotifySuccess) {
                            keySoundManager.notifySuccess()
                        }

                        val result = Intent()
                        result.putExtra(RESPONSE_TAG, viewModel.response)
                        this@ChallengeResponseActivity.setResult(RESULT_OK, result)
                        finish()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Call connection manager broadcast response
        newIntentReceive?.let {
            viewModel.handleNewReceivedIntent(this, it)
        }
        newIntentReceive = null
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // To call broadcast receiver in onResume
        if (intent != null) {
            newIntentReceive = intent
        }
    }

    companion object {
        const val CHALLENGE_TAG = "challenge"
        const val SLOT_TAG = "purpose"
        const val RESPONSE_TAG = "response"
    }
}
