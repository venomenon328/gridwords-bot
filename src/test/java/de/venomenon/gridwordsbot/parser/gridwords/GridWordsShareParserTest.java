package de.venomenon.gridwordsbot.parser.gridwords;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.FixtureSupport;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class GridWordsShareParserTest {

    private final GridWordsShareParser parser = new GridWordsShareParser();

    @Test
    void parsesSolvedShareWithStreakAndCanonicalizesBoard() {
        ParseResult.Parsed parsed = parsed("gridwords/solved/synthetic-solved-with-streak.txt");

        assertThat(parsed.result().gameType()).isEqualTo(GameType.GRIDWORDS);
        assertThat(parsed.result().gameDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(parsed.result().outcome()).isEqualTo(new ShareOutcome.Solved(5, 6));
        assertThat(parsed.result().duration()).isEqualTo(Duration.ofSeconds(85));
        assertThat(parsed.result().gridgamesStreak()).isEqualTo(OptionalInt.of(2));
        assertThat(parsed.result().board().orElseThrow().canonicalText()).isEqualTo("""
                ⬜⬜🟨⬜🟩
                ⬜⬜⬜⬜⬜
                🟨🟨⬜🟩🟩
                ⬜⬜🟨🟨🟩
                🟩🟩🟩🟩🟩""");
    }

    @ParameterizedTest
    @MethodSource("solvedFixturePaths")
    void parsesSolvedFixturesWithoutRequiringAttachments(String fixturePath) {
        ParseResult.Parsed parsed = parsed(fixturePath);

        assertThat(parsed.result().outcome()).isInstanceOf(ShareOutcome.Solved.class);
        assertThat(parsed.result().board()).isPresent();
    }

    static Stream<String> solvedFixturePaths() {
        return Stream.of(
                "gridwords/solved/synthetic-solved-without-streak.txt",
                "gridwords/solved/synthetic-link-before-header.txt",
                "gridwords/solved/synthetic-link-after-board.txt");
    }

    @Test
    void toleratesCrLfHorizontalSpacingVariationSelectorsAndBlackSquares() {
        String content = FixtureSupport.read("gridwords/solved/synthetic-crlf-extra-horizontal-spacing.txt")
                .replace("\n", "\r\n");

        ParseResult result = parser.parse(new ShareParseInput(content, List.of()));

        assertThat(result).isInstanceOf(ParseResult.Parsed.class);
        ParseResult.Parsed parsed = (ParseResult.Parsed) result;
        assertThat(parsed.result().gridgamesStreak()).isEqualTo(OptionalInt.of(2));
        assertThat(parsed.result().board().orElseThrow().rows().getFirst()).isEqualTo("⬜⬜🟨⬜🟩");
    }

    @Test
    void parsesTheDocumentedProvisionalUnsolvedX6Fixture() {
        ParseResult.Parsed parsed = parsed("gridwords/unsolved/synthetic-unsolved-x6-assumption.txt");

        assertThat(parsed.result().outcome()).isEqualTo(new ShareOutcome.Unsolved(6));
        assertThat(parsed.result().board().orElseThrow().rows()).hasSize(6);
    }

    @ParameterizedTest
    @MethodSource("invalidFixtures")
    void classifiesInvalidGridWordsFixturesWithStableCodes(String fixturePath, ParseErrorCode expectedCode) {
        ParseResult result = parser.parse(input(fixturePath));

        assertThat(result).isInstanceOf(ParseResult.Invalid.class);
        ParseResult.Invalid invalid = (ParseResult.Invalid) result;
        assertThat(invalid.errorCode()).isEqualTo(expectedCode);
        assertThat(invalid.description()).doesNotContain(FixtureSupport.read(fixturePath));
    }

    static Stream<Arguments> invalidFixtures() {
        return Stream.of(
                Arguments.of("gridwords/invalid/synthetic-wrong-maximum.txt", ParseErrorCode.INVALID_ATTEMPT_RESULT),
                Arguments.of("gridwords/invalid/synthetic-attempts-over-maximum.txt", ParseErrorCode.INVALID_ATTEMPT_RESULT),
                Arguments.of("gridwords/invalid/synthetic-invalid-date.txt", ParseErrorCode.INVALID_DATE),
                Arguments.of("gridwords/invalid/synthetic-invalid-duration.txt", ParseErrorCode.INVALID_DURATION),
                Arguments.of("gridwords/invalid/synthetic-zero-streak.txt", ParseErrorCode.INVALID_STREAK),
                Arguments.of("gridwords/invalid/synthetic-missing-board.txt", ParseErrorCode.MISSING_BOARD),
                Arguments.of("gridwords/invalid/synthetic-invalid-board-width.txt", ParseErrorCode.INVALID_BOARD),
                Arguments.of("gridwords/invalid/synthetic-invalid-board-height.txt", ParseErrorCode.INVALID_BOARD),
                Arguments.of("gridwords/invalid/synthetic-unknown-board-symbol.txt", ParseErrorCode.INVALID_BOARD));
    }

    @Test
    void returnsNotApplicableForMessagesWithoutGridWordsHeaderOrForQuadWordsShares() {
        assertThat(parser.parse(input("gridwords/invalid/synthetic-not-gridwords.txt")))
                .isInstanceOf(ParseResult.NotApplicable.class);
        assertThat(parser.parse(new ShareParseInput(
                FixtureSupport.read("quadwords/solved/synthetic-solved-with-streak.txt"), List.of())))
                .isInstanceOf(ParseResult.NotApplicable.class);
    }

    @Test
    void returnsInvalidForMultipleGridWordsHeaders() {
        String fixture = FixtureSupport.read("gridwords/solved/synthetic-solved-without-streak.txt");

        ParseResult result = parser.parse(new ShareParseInput(fixture + "\n" + fixture, List.of()));

        assertThat(result).isEqualTo(new ParseResult.Invalid(
                ParseErrorCode.MALFORMED_HEADER,
                "The message contains multiple GridWords headers."));
    }

    private ParseResult.Parsed parsed(String fixturePath) {
        ParseResult result = parser.parse(input(fixturePath));
        assertThat(result).isInstanceOf(ParseResult.Parsed.class);
        return (ParseResult.Parsed) result;
    }

    private ShareParseInput input(String fixturePath) {
        return new ShareParseInput(FixtureSupport.read(fixturePath), List.of());
    }
}
