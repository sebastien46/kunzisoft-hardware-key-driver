package com.kunzisoft.hardware.yubikey.apdu.response.ykoath;

import com.kunzisoft.hardware.yubikey.challenge.YubiKey;
import com.kunzisoft.hardware.yubikey.apdu.ResponseApdu;

public class PutResponseApdu extends ResponseApdu {
	public PutResponseApdu(final byte[] response) {
		super(response);
	}

	public byte[] getResult() {
		final byte[] result = new byte[YubiKey.CHALLENGE_RESPONSE_LENGTH];

		System.arraycopy(this.response, 0, result, 0, YubiKey.CHALLENGE_RESPONSE_LENGTH);

		return result;
	}
}
