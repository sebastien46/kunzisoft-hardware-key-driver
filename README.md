# Android Key Driver

Key Driver is a **driver application for hardware keys** (SoloKey and Yubikey) that provides an answer to a challenge for applications requiring physical key authentication.

The application allows use of: 

 - The hmac-secret FIDO2 functionality of a SoloKey, YubiKey, or other FIDO2 Authenticator (over USB or NFC).

 - The HMAC-SHA1 challenge-response functionality of a Yubikey with USB OTG or NFC connection.

## Download

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Get it on Google Play"
	height="80">](https://play.google.com/store/apps/details?id=com.kunzisoft.hardware.key)
[<img src="https://gitlab.com/kunzisoft/gitlab-badge/-/raw/master/get-it-on-gitlab.png"
      alt="Get it on GitLab"
	height="80">](https://gitlab.com/kunzisoft/android-hardware-key-driver/-/releases)

## Integration

### Solokey or other FIDO2 Authenticator

This driver supports NFC or USB FIDO2 Authenticators that implement both the FIDO2 `hmac-secret` extension and either `clientPin`
or onboard user verification (or both).

#### Creating a Credential

The client application needs to call a Key Driver activity for a response to be provided. For this, one must send an Intent `android.fido.intent.action.HMAC_SECRET_CREATE` with extra data:

- `String rpId`: The FIDO2 Relying Party ID for which the Credential is being created. This will be displayed to the user.

After the user's action, the Key Driver activity will return an extra:

- `byte[] credentialId`: The FIDO Credential ID that was created. This Credential has support for the `hmac-secret` extension and can be used later.

#### Getting a response for an existing Credential

The client application needs to call a Key Driver activity for a response to be provided. For this, one must send an Intent `android.fido.intent.action.HMAC_SECRET_CHALLENGE_RESPONSE` with extra data:

- `byte[] challenge`: The challenge for which a response is to be provided. This must be either 32 bytes long.
- `byte[] challenge_2`: An optional second challenge, also 32 bytes long if provided.
- `String rpId`: The FIDO2 Relying Party ID for which the challenge is being made. This will be displayed to the user, and must match the provided Credential(s) (or a Discoverable
   Credential stored on the user's Authenticator).
- `int numCredentials`: The number of credentials being provided in the Intent. May be zero, or omitted (treated the same as zero). If zero, the Authenticator will try to respond using
  a FIDO2 Discoverable Credential (aka a Resident Key).
- `byte[] credential_0`, `byte[] credential_1`,  through `byte[] credential_<numCredentials>`: FIDO Credential IDs, as provided by the Authenticator being used. The response
  will pertain to one of these credentials if any are provided.

After the user's action, the Key Driver activity will return extras:

- `byte[] response`: Response to `challenge`.
- `byte[] response_2`: Response to `challenge_2`. Only present if `challenge_2` was provided.
- `byte[] credentialId`: The FIDO Credential ID that was used to generate the responses. May be one of the Credentials provided in the input, or will be a Discoverable Credential's ID
   if no input Credentials were provided.

```kotlin

private var getHMACResultLauncher: ActivityResultLauncher<Intent>? = null

// Request with a challenge
fun launchChallengeForResponse(seed1: ByteArray, seed2: ByteArray?, credentials: List<ByteArray>) {
    // Send the seed(s) and credentials to the driver
    getHMACResultLauncher?.launch(
        Intent("android.fido.intent.action.HMAC_SECRET_CHALLENGE_RESPONSE").apply {
            putExtra("rpId", "relyingparty.for.example")
            putExtra("challenge", seed1)
            putExtra("challenge_2", seed2)
            putExtra("numCredentials", credentials.size)
            for (i in credentials.indices) {
                putExtra("credential_${i}", credentials[i])
            }
        }
    )
}

// Wait for the response
fun buildHardwareKeyResponse(onChallengeResponded: (challengeResponse: ByteArray?) -> Unit) {
    val resultCallback = ActivityResultCallback<ActivityResult> { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val challengeResponse1: ByteArray? = result.data?.getByteArrayExtra("response")
            val challengeResponse2: ByteArray? = result.data?.getByteArrayExtra("response_2")
            onChallengeResponded.invoke(challengeResponse1, challengeResponse2)
        } else {
            onChallengeResponded.invoke(null, null)
        }
    }

    getHMACResultLauncher = activity?.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        resultCallback
    )
}
```

### Yubikey

The client application needs to call a Key Driver activity for a response to be provided. For this, one must send an Intent `android.yubikey.intent.action.CHALLENGE_RESPONSE` with extra data `byte[] challenge`. After the user's action, the Key Driver activity will return an extra `byte[] response`.


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

// Wait for the response
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
