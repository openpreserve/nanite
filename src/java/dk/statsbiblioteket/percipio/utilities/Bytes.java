package dk.statsbiblioteket.percipio.utilities;

import java.io.IOException;

/**
 * Utillity class for working with bytes, bytearrays, and strings.
 */
public class Bytes {
    /**
     * The hexidecimal characters in numeric order from {@code 0-f}
     */
    public static final char[] HEX_DIGITS =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f'};

    /** The number of bits in a nibble (used for shifting). */
    private static final byte BITS_IN_NIBBLE = 4;
    /** A bitmask for a nibble (used for "and'ing" out the bits. */
    private static final byte BITMASK_FOR_NIBBLE = 0x0f;

    /** Utility class, don't initialise. */
    private Bytes() {
    }

    /**
     * Converts a byte array to a hex-string.
     *
     * @param ba the bytearray to be converted
     * @return ba the byte array to convert to a hex-string
     */
    public static String toHex(final byte[] ba) {
        StringBuilder sb = new StringBuilder(ba.length * 2);
        try {
            toHex(sb, ba);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unexpected IOException while appending to StringBuilder");
        }

        return sb.toString();
    }

    /**
     * Converts a byte array to a hex-string and write the result to an
     * {@code Appendable} (such as a {@code StringBuilder}).
     *
     * @param buf the appendable to write to
     * @param ba the byte array to convert to a hex-string
     * @return always returns {@code buf}
     * @throws IOException upon errors writing to {@code buf}
     */
    public static Appendable toHex(Appendable buf, byte[] ba)
                                                            throws IOException {
        int baLen = ba.length;
        for (int i = 0; i < baLen; i++) {
            buf.append(
                    HEX_DIGITS[(ba[i] >> BITS_IN_NIBBLE) & BITMASK_FOR_NIBBLE]);
            buf.append(HEX_DIGITS[ba[i] & BITMASK_FOR_NIBBLE]);
        }

        return buf;
    }


    public static byte[] hexStringToByteArray(String s) {
       byte[] b = new byte[s.length() / 2];
       for (int i = 0; i < b.length; i++){
         int index = i * 2;
         int v = Integer.parseInt(s.substring(index, index + 2), 16);
         b[i] = (byte)v;
       }
       return b;
   }
}
