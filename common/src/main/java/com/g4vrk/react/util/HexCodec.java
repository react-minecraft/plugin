package com.g4vrk.react.util;

import org.jetbrains.annotations.NotNull;

/**
 * Encodes and decodes lowercase hexadecimal data without requiring a newer JDK API.
 */
public final class HexCodec {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private HexCodec() {

    }

    /**
     * Encodes bytes as lowercase hexadecimal.
     *
     * @param bytes bytes to encode
     * @return lowercase hexadecimal text
     */
    public static @NotNull String encode(final byte @NotNull [] bytes) {

        final char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            final int value = Byte.toUnsignedInt(bytes[index]);
            encoded[index * 2] = DIGITS[value >>> 4];
            encoded[index * 2 + 1] = DIGITS[value & 0x0F];
        }
        return new String(encoded);

    }

    /**
     * Decodes hexadecimal text.
     *
     * @param encoded hexadecimal text
     * @return decoded bytes
     * @throws IllegalArgumentException when the text is not hexadecimal or has odd length
     */
    public static byte @NotNull [] decode(final @NotNull String encoded) {

        if ((encoded.length() & 1) != 0) {
            throw new IllegalArgumentException("Hexadecimal text must have an even length");
        }
        final byte[] decoded = new byte[encoded.length() / 2];
        for (int index = 0; index < decoded.length; index++) {
            final int high = digit(encoded.charAt(index * 2));
            final int low = digit(encoded.charAt(index * 2 + 1));
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal text");
            }
            decoded[index] = (byte) ((high << 4) | low);
        }
        return decoded;

    }

    private static int digit(final char character) {

        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return -1;

    }

}
