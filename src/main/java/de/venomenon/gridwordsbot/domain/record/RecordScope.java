package de.venomenon.gridwordsbot.domain.record;

/** Konkreter Vergleichsraum eines späteren Rekordzustands. */
public sealed interface RecordScope permits RecordScope.Personal, RecordScope.ServerIndividual, RecordScope.Shared {
    RecordScopeType type();

    record Personal(long playerId) implements RecordScope {
        public Personal {
            if (playerId <= 0) {
                throw new IllegalArgumentException("playerId must be positive");
            }
        }

        @Override
        public RecordScopeType type() {
            return RecordScopeType.PERSONAL;
        }
    }

    record ServerIndividual() implements RecordScope {
        @Override
        public RecordScopeType type() {
            return RecordScopeType.SERVER_INDIVIDUAL;
        }
    }

    record Shared() implements RecordScope {
        @Override
        public RecordScopeType type() {
            return RecordScopeType.SHARED;
        }
    }
}
