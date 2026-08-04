package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Optional;

public enum StreakRecordMetric implements RecordMetric {
    ACTIVITY("activity", RecordPolarity.POSITIVE, null, false),
    COMPLETE("complete", RecordPolarity.POSITIVE, null, true),
    GRIDWORDS_SOLVED("gridwords-solved", RecordPolarity.POSITIVE, GameType.GRIDWORDS, true),
    QUADWORDS_SOLVED("quadwords-solved", RecordPolarity.POSITIVE, GameType.QUADWORDS, true),
    PERFECT("perfect", RecordPolarity.POSITIVE, null, true),
    GRIDWORDS_DROUGHT("gridwords-drought", RecordPolarity.NEGATIVE, GameType.GRIDWORDS, false),
    QUADWORDS_DROUGHT("quadwords-drought", RecordPolarity.NEGATIVE, GameType.QUADWORDS, false),
    WITHOUT_PERFECT_DAY("without-perfect-day", RecordPolarity.NEGATIVE, null, false);

    private final String slug;
    private final RecordPolarity polarity;
    private final GameType fixedGame;
    private final boolean sharedScopeAllowed;

    StreakRecordMetric(String slug, RecordPolarity polarity, GameType fixedGame, boolean sharedScopeAllowed) {
        this.slug = slug;
        this.polarity = polarity;
        this.fixedGame = fixedGame;
        this.sharedScopeAllowed = sharedScopeAllowed;
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
        return RecordValueKind.STREAK;
    }

    @Override
    public RecordSourceType sourceType() {
        return RecordSourceType.STREAK_RUN;
    }

    @Override
    public Optional<GameType> fixedGame() {
        return Optional.ofNullable(fixedGame);
    }

    @Override
    public boolean sharedScopeAllowed() {
        return sharedScopeAllowed;
    }

    public boolean drought() {
        return this == GRIDWORDS_DROUGHT || this == QUADWORDS_DROUGHT;
    }
}
