package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Persistence boundary for the one result identified by player, game and game date. */
public interface GameResultStore {
    StoredGameResult upsert(GameResultUpsert request);
    Optional<StoredGameResult> find(long playerId, de.venomenon.gridwordsbot.domain.model.GameType gameType, java.time.LocalDate gameDate);
    StoredGameResult setCanonicalMessageId(long resultId, long canonicalMessageId);
    default Optional<StoredGameResult> findById(long resultId) { throw new UnsupportedOperationException("findById is not available"); }
    default List<StoredGameResult> findAll() { throw new UnsupportedOperationException("findAll is not available"); }
    default Optional<PublicationClaim> claimCanonicalPublication(long resultId, Instant leaseUntil) { throw new UnsupportedOperationException("publication claims are not available"); }
    default void releaseCanonicalPublicationClaim(long resultId, UUID claimToken) { throw new UnsupportedOperationException("publication claims are not available"); }
    record PublicationClaim(UUID token, Instant leaseUntil) { public PublicationClaim { Objects.requireNonNull(token); Objects.requireNonNull(leaseUntil); } }
    default StoredGameResult persistCanonicalPublication(long resultId, long canonicalMessageId) { return setCanonicalMessageId(resultId, canonicalMessageId); }

    record GameResultUpsert(long playerId, ParsedGameResult parsedResult, String rawShareText, String parserVersion) {
        public GameResultUpsert {
            if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
            Objects.requireNonNull(parsedResult, "parsedResult");
            if (parsedResult.duration().getNano() != 0) throw new IllegalArgumentException("duration must have full-second precision");
            Objects.requireNonNull(rawShareText, "rawShareText");
            Objects.requireNonNull(parserVersion, "parserVersion");
        }
    }

    record StoredGameResult(long id, long playerId, ParsedGameResult parsedResult, String rawShareText,
                            String parserVersion, OptionalLong canonicalMessageId, Instant createdAt, Instant updatedAt) {
        public StoredGameResult {
            if (id <= 0 || playerId <= 0) throw new IllegalArgumentException("IDs must be positive");
            Objects.requireNonNull(parsedResult, "parsedResult");
            Objects.requireNonNull(rawShareText, "rawShareText");
            Objects.requireNonNull(parserVersion, "parserVersion");
            Objects.requireNonNull(canonicalMessageId, "canonicalMessageId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }
}
