package de.venomenon.gridwordsbot.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParsedGameResultInvariantTest {

    private static final LocalDate GAME_DATE = LocalDate.of(2026, 7, 27);
    private static final String BOARD_ROW = "\u2B1C".repeat(5);

    @Test
    void acceptsOnlyConsistentGameSpecificResultCombinations() {
        assertThatCode(() -> result(
                GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6), Optional.of(boardWithRows(3))))
                .doesNotThrowAnyException();
        assertThatCode(() -> result(
                GameType.GRIDWORDS, new ShareOutcome.Unsolved(6), Optional.of(boardWithRows(6))))
                .doesNotThrowAnyException();
        assertThatCode(() -> result(
                GameType.QUADWORDS, new ShareOutcome.Solved(9, 9), Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongMaximumAttemptsForTheGameType() {
        assertThatIllegalArgumentException().isThrownBy(() -> result(
                GameType.GRIDWORDS, new ShareOutcome.Solved(3, 9), Optional.of(boardWithRows(3))));
        assertThatIllegalArgumentException().isThrownBy(() -> result(
                GameType.QUADWORDS, new ShareOutcome.Solved(6, 6), Optional.empty()));
    }

    @Test
    void rejectsGridWordsSolvedBoardsWhoseHeightDoesNotMatchAttempts() {
        assertThatIllegalArgumentException().isThrownBy(() -> result(
                GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6), Optional.of(boardWithRows(2))));
        assertThatIllegalArgumentException().isThrownBy(() -> result(
                GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6), Optional.of(boardWithRows(5))));
    }

    @Test
    void rejectsUnsolvedGridWordsBoardsWithFewerThanSixRows() {
        assertThatIllegalArgumentException().isThrownBy(() -> result(
                GameType.GRIDWORDS, new ShareOutcome.Unsolved(6), Optional.of(boardWithRows(5))));
    }

    @Test
    void keepsTheImageContentTypeAuthoritativeOverTheFilename() {
        assertThat(new AttachmentMetadata("unrelated.bin", "image/png", 1).isPlausibleImage()).isTrue();
        assertThat(new AttachmentMetadata("result.png", "text/plain", 1).isPlausibleImage()).isFalse();
        assertThat(new AttachmentMetadata("result.png", "application/octet-stream", 1).isPlausibleImage()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("filenameFallbacks")
    void usesTheFilenameFallbackOnlyWhenTheContentTypeIsMissing(String filename) {
        assertThat(new AttachmentMetadata(filename, "", 1).isPlausibleImage()).isTrue();
    }

    static Stream<Arguments> filenameFallbacks() {
        return Stream.of("result.png", "result.jpg", "result.jpeg", "result.webp")
                .map(Arguments::of);
    }

    private static ParsedGameResult result(
            GameType gameType, ShareOutcome outcome, Optional<NormalizedBoard> board) {
        return new ParsedGameResult(
                gameType, GAME_DATE, outcome, Duration.ZERO, OptionalInt.empty(), board);
    }

    private static NormalizedBoard boardWithRows(int rowCount) {
        return new NormalizedBoard(Collections.nCopies(rowCount, BOARD_ROW));
    }
}
