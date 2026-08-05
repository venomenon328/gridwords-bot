package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Subject is stored as a non-null stable key to avoid nullable unique-key ambiguity. */
public record RecordAnnouncementSubject(Type type, String key) {
    public enum Type { PLAYER, SHARED }
    public RecordAnnouncementSubject {
        Objects.requireNonNull(type, "type"); Objects.requireNonNull(key, "key");
        if (key.isBlank() || key.length() > 128) throw new IllegalArgumentException("subject key is invalid");
        if (type == Type.PLAYER && (!key.startsWith("player:") || key.length() == "player:".length())) {
            throw new IllegalArgumentException("player subject key must be stable");
        }
        if (type == Type.SHARED && !key.equals("shared")) throw new IllegalArgumentException("shared subject key must be shared");
    }
    public static RecordAnnouncementSubject player(long playerId) {
        if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
        return new RecordAnnouncementSubject(Type.PLAYER, "player:" + playerId);
    }
    public static RecordAnnouncementSubject shared() { return new RecordAnnouncementSubject(Type.SHARED, "shared"); }
}
