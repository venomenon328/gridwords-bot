package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Optional;

public enum ResultRecordMetric implements RecordMetric {
    FEWEST_ATTEMPTS("fewest-attempts", RecordPolarity.POSITIVE, RecordValueKind.ATTEMPTS_AND_DURATION),
    FASTEST_SOLUTION("fastest-solution", RecordPolarity.POSITIVE, RecordValueKind.DURATION),
    SLOWEST_SUCCESSFUL_SOLUTION("slowest-successful-solution", RecordPolarity.NEGATIVE, RecordValueKind.DURATION);

    private final String slug;
    private final RecordPolarity polarity;
    private final RecordValueKind valueKind;

    ResultRecordMetric(String slug, RecordPolarity polarity, RecordValueKind valueKind) {
        this.slug = slug;
        this.polarity = polarity;
        this.valueKind = valueKind;
    }

    @Override
    public String slug() {
        return slug;
    }

    @Override
    public RecordPolarity polarity() {
        return polarity;
    }

    @Override
    public RecordValueKind valueKind() {
        return valueKind;
    }

    @Override
    public RecordSourceType sourceType() {
        return RecordSourceType.GAME_RESULT;
    }

    @Override
    public Optional<GameType> fixedGame() {
        return Optional.empty();
    }

    @Override
    public boolean sharedScopeAllowed() {
        return false;
    }
}
