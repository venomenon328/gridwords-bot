package de.venomenon.gridwordsbot.port.out;

/** Reads whether contextual complete/perfect lines were established by any submission of one mutable result. */
@FunctionalInterface
public interface CanonicalPublicationContextStore {

    HistoricalContext findForResult(long gameResultId);

    static CanonicalPublicationContextStore none() {
        return ignored -> HistoricalContext.none();
    }

    record HistoricalContext(
            boolean personalCompleteEstablished,
            boolean personalPerfectEstablished,
            boolean sharedCompleteEstablished,
            boolean sharedPerfectEstablished) {

        public static HistoricalContext none() {
            return new HistoricalContext(false, false, false, false);
        }
    }
}
