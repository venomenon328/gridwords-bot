package de.venomenon.gridwordsbot.port.out;

/** Safe classification of Discord delivery failures for durable retry state. */
public final class DiscordDeliveryException extends RuntimeException {
    private final boolean permanent;

    private DiscordDeliveryException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public static DiscordDeliveryException retryable(String message, Throwable cause) {
        return new DiscordDeliveryException(message, false, cause);
    }

    public static DiscordDeliveryException permanent(String message, Throwable cause) {
        return new DiscordDeliveryException(message, true, cause);
    }

    public boolean permanent() {
        return permanent;
    }
}