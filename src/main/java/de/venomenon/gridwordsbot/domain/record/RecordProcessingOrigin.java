package de.venomenon.gridwordsbot.domain.record;

/** Ursprung einer Rekordauswertung und dessen grundsätzliche öffentliche Meldungsfähigkeit. */
public enum RecordProcessingOrigin {
    LIVE_SUBMISSION(true),
    NORMAL_CORRECTION(true),
    DAY_CLOSE(true),
    BOOTSTRAP(false),
    REPLAY(false),
    IMPORT(false),
    BACKFILL(false),
    ADMINISTRATIVE_REPAIR(false);

    private final boolean publicAnnouncementEligible;

    RecordProcessingOrigin(boolean publicAnnouncementEligible) {
        this.publicAnnouncementEligible = publicAnnouncementEligible;
    }

    public boolean publicAnnouncementEligible() {
        return publicAnnouncementEligible;
    }
}
