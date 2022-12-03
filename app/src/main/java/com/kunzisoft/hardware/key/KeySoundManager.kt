package com.kunzisoft.hardware.key

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.preference.PreferenceManager

class KeySoundManager(private val context: Context) {

    private var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private var soundPool: SoundPool = SoundPool.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
        )
        .build()
    private var endSoundID: Int = 0

    init {
        endSoundID = soundPool.load(context, R.raw.end, 1)
    }

    fun emitSuccessSound() {
        soundPool.play(endSoundID, 1f, 1f, 0, 0, 1f)
    }

    fun emitSuccessVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    fun notifySuccess() {
        if (sharedPreferences.getBoolean(
                context.getString(R.string.default_feedback_pref),
                context.resources.getBoolean(R.bool.default_feedback_default)
            )
        ) {
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> emitSuccessSound()
                AudioManager.RINGER_MODE_VIBRATE -> emitSuccessVibration()
            }
        } else {
            if (sharedPreferences.getBoolean(
                    context.getString(R.string.sound_pref),
                    context.resources.getBoolean(R.bool.sound_default)
                )
            ){
                emitSuccessSound()
            }
            if (sharedPreferences.getBoolean(
                    context.getString(R.string.vibration_pref),
                    context.resources.getBoolean(R.bool.vibration_default)
                )
            ) {
                emitSuccessVibration()
            }
        }
    }
}