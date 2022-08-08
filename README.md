# Android Key Driver

Key Driver is a **driver application for hardware keys** (SoloKey and Yubikey) that provides an answer to a challenge for applications requiring physical key authentication.

The application allows to use : 

 - The hmac-secret FIDO2 functionnality of SoloKey.

 - The HMAC-SHA1 challenge-response functionality of Yubikey with USB OTG or NFC connection.

## Download

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Get it on Google Play"
	height="80">](https://play.google.com/store/apps/details?id=com.kunzisoft.hardware.key)

## Integration

### Solokey

(In development)

### Yubikey

The client application needs to call a Key Driver activity for a response to be provided. For this, it is needed to call an Intent `android.yubikey.intent.action.CHALLENGE_RESPONSE`with an extra data _byte[]_ challenge. After the user's actions, the Key Driver activity will return an extra __byte[]_ response.


```kotlin

private var getChallengeResponseResultLauncher: ActivityResultLauncher<Intent>? = null

// Request with a challenge  
fun launchChallengeForResponse(seed: ByteArray?) {
    // Send the seed to the driver
    getChallengeResponseResultLauncher?.launch(
        Intent("android.yubikey.intent.action.CHALLENGE_RESPONSE").apply {
            putExtra("challenge", challenge)
        }
    )
}

// Wait the response
fun buildHardwareKeyResponse(onChallengeResponded: (challengeResponse: ByteArray?) -> Unit) {
    val resultCallback = ActivityResultCallback<ActivityResult> { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val challengeResponse: ByteArray? = result.data?.getByteArrayExtra("response")
            onChallengeResponded.invoke(challengeResponse)
        } else {
            onChallengeResponded.invoke(null)
        }
    }

    getChallengeResponseResultLauncher = activity?.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            resultCallback
        )
}
```

## Contributions

* Add features by making a **[merge request](https://gitlab.com/kunzisoft/android-hardware-key-driver/-/merge_requests)**.
* **[Donate](https://www.keepassdx.com/#donation)** or buy the **[Pro version](https://play.google.com/store/apps/details?id=com.kunzisoft.keepass.pro)** 人◕ ‿‿ ◕人 for helping development.

## License

Copyright © 2022 Jeremy Jamet / [Kunzisoft](https://www.kunzisoft.com).

This file is part of KeePassDX.

[KeePassDX](https://www.keepassdx.com) is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

KeePassDX is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.

*This project is a refactoring of [ykDroid](https://github.com/pp3345/ykDroid) by pp3345.*