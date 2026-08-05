package de.venomenon.gridwordsbot.domain.record;

public record RecordAnnouncementMessage(int position, long messageId) {
    public RecordAnnouncementMessage {
        if (position < 0 || messageId <= 0) throw new IllegalArgumentException("message position and id are invalid");
    }
}
