package de.venomenon.gridwordsbot.port.out;

/** A desired projection cannot be replaced while a different worker owns its delivery claim. */
public final class RecordAnnouncementClaimConflictException extends RuntimeException {
    public RecordAnnouncementClaimConflictException() {
        super("record announcement is actively claimed");
    }
}
