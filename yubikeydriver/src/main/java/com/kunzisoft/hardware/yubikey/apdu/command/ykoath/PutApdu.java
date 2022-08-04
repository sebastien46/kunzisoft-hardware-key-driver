package com.kunzisoft.hardware.yubikey.apdu.command.ykoath;

import com.kunzisoft.hardware.yubikey.apdu.CommandApdu;
import com.kunzisoft.hardware.yubikey.apdu.response.ykoath.PutResponseApdu;
import com.kunzisoft.hardware.yubikey.Slot;

/**
 * https://developers.yubico.com/OATH/YKOATH_Protocol.html
 */
public class PutApdu extends CommandApdu {
	private final Slot slot;

	public PutApdu(final Slot slot, final byte[] challenge) {
		this.slot = slot;
		this.data = challenge;
	}

	@Override
	protected byte getCommandClass() {
		return (byte) 0x00;
	}

	@Override
	protected byte getInstruction() {
		return (byte) 0x01;
	}

	@Override
	protected byte[] getParameters() {
		return new byte[]{this.slot.getAddress(), (byte) 0x00};
	}

	@Override
	protected byte getExpectedLength() {
		return (byte) 0x00;
	}

	@Override
	public PutResponseApdu parseResponse(final byte[] response) {
		return new PutResponseApdu(response);
	}
}
