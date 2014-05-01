package fi.aalto.cse.msp14.carplatforms.obdlink;

import java.util.ArrayList;

import android.util.Log;

public class OBDCapabilityBitSet {
	private ArrayList<Boolean> m_bits = new ArrayList<Boolean>();

	@SuppressWarnings("serial")
	public class InvalidBitSetException extends Exception {
		public InvalidBitSetException(final String message) {
			super(message);
		}
	};

	public void clear() {
		// do not mutate existing list
		m_bits = new ArrayList<Boolean>();
	}
	
	public void setValue(final byte[] bits[]) throws InvalidBitSetException {
		for (int i = 0; i < bits.length; ++i)
			if (bits[i].length != 4)
				throw new InvalidBitSetException("Expected 4 bytes, got " + Integer.toString(bits[i].length));

		final ArrayList<Boolean> newBits = new ArrayList<Boolean>(bits.length * 4 * 8 + 1);

		// pid 0 is always supported
		newBits.add(true);
		
		// per field, per field element byte, per bit (in MBS -> LSB order)
		for (int i = 0; i < bits.length; ++i)
			for (int e = 0; e < 4; ++e)
				for (int b = 0; b < 8; ++b)
					newBits.add((bits[i][e] & (0x80 >> b)) != 0x00);

		// atomic
		m_bits = newBits;
	}

	public boolean queryBit(final int bitIndex) {
		final ArrayList<Boolean> bits = m_bits;

		if (bitIndex < 0 || bitIndex >= bits.size())
			return false;
		return bits.get(bitIndex);
	}

	public int getNumBitsSet() {
		final ArrayList<Boolean> bits = m_bits;

		int numBitsSet = 0;
		for (int i = 0; i < bits.size(); ++i)
			if (bits.get(i))
				++numBitsSet;

		return numBitsSet;
	}
	
	public void logSupportedPIDs (final String tag) {
		final ArrayList<Boolean> bits = m_bits;
		
		for (int i = 0; i < bits.size(); ++i)
			if (bits.get(i))
				Log.i(tag, " -> id " + i + ", hex 0x" + Integer.toHexString(i));
	}
}
