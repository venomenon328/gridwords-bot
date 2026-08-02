package de.venomenon.gridwordsbot.port.out;

/** Read-only fence preventing re-publication after a canonical result message was retired. */
@FunctionalInterface
public interface CanonicalPublicationRetirementQuery {
    boolean isCanonicalPublicationAllowed(long resultId);

    static CanonicalPublicationRetirementQuery allowAll() {
        return ignored -> true;
    }
}
