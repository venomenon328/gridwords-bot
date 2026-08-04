package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Optional;

/** Fachliche Metrik einer Rekorddefinition. */
public sealed interface RecordMetric permits ResultRecordMetric, StreakRecordMetric {
    String slug();

    RecordPolarity polarity();

    RecordValueKind valueKind();

    RecordSourceType sourceType();

    Optional<GameType> fixedGame();

    boolean sharedScopeAllowed();
}
