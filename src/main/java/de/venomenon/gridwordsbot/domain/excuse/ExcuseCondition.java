package de.venomenon.gridwordsbot.domain.excuse;

import java.util.Locale;
import java.util.Optional;

/** A named fact that may be required or excluded by an excuse template. */
public sealed interface ExcuseCondition permits ExcuseReason, ExcuseFact {

    String key();

    static Optional<ExcuseCondition> fromKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String key = rawKey.trim().toUpperCase(Locale.ROOT);
        for (ExcuseReason reason : ExcuseReason.values()) {
            if (reason.key().equals(key)) {
                return Optional.of(reason);
            }
        }
        for (ExcuseFact fact : ExcuseFact.values()) {
            if (fact.key().equals(key)) {
                return Optional.of(fact);
            }
        }
        return Optional.empty();
    }
}
