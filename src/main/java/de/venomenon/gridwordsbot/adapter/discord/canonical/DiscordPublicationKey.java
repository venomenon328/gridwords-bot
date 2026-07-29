package de.venomenon.gridwordsbot.adapter.discord.canonical;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Encodes the stable publication key into an invisible Discord embed footer. */
final class DiscordPublicationKey {

    private static final char PREFIX = '\u2063';
    private static final char ZERO = '\u200B';
    private static final char ONE = '\u200C';

    private DiscordPublicationKey() {
    }

    static String encode(String publicationKey) {
        byte[] bytes = Objects.requireNonNull(publicationKey).getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(1 + bytes.length * Byte.SIZE).append(PREFIX);
        for (byte value : bytes) {
            int unsigned = Byte.toUnsignedInt(value);
            for (int bit = Byte.SIZE - 1; bit >= 0; bit--) {
                encoded.append(((unsigned >>> bit) & 1) == 0 ? ZERO : ONE);
            }
        }
        return encoded.toString();
    }

    static boolean matches(String publicationKey, String footerText) {
        return publicationKey.equals(footerText) || encode(publicationKey).equals(footerText);
    }
}
