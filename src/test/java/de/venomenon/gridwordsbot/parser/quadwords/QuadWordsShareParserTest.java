package de.venomenon.gridwordsbot.parser.quadwords;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.FixtureSupport;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuadWordsShareParserTest {

    private final QuadWordsShareParser parser = new QuadWordsShareParser();

    @Test
    void parsesSolvedShareWithPlausibleImageContentTypeWithoutDownloadingIt() {
        ParseResult result = parser.parse(input(
                "quadwords/solved/synthetic-solved-with-streak.txt",
                new AttachmentMetadata("result.bin", "image/png", 42)));

        assertThat(result).isInstanceOf(ParseResult.Parsed.class);
        ParseResult.Parsed parsed = (ParseResult.Parsed) result;
        assertThat(parsed.result().gameType()).isEqualTo(GameType.QUADWORDS);
        assertThat(parsed.result().gameDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(parsed.result().outcome()).isEqualTo(new ShareOutcome.Solved(9, 9));
        assertThat(parsed.result().duration()).isEqualTo(Duration.ofSeconds(258));
        assertThat(parsed.result().gridgamesStreak()).isEqualTo(OptionalInt.of(2));
        assertThat(parsed.result().board()).isEmpty();
    }

    @Test
    void parsesConfirmedUnsolvedX9FixtureWithImageFileExtensionWhenContentTypeIsMissing() {
        ParseResult result = parser.parse(input(
                "quadwords/unsolved/confirmed-unsolved-x9.txt",
                new AttachmentMetadata("quadwords-result.WEBP", "", 100)));

        assertThat(result).isInstanceOf(ParseResult.Parsed.class);
        ParseResult.Parsed parsed = (ParseResult.Parsed) result;
        assertThat(parsed.result().outcome()).isEqualTo(new ShareOutcome.Unsolved(9));
        assertThat(parsed.result().gridgamesStreak()).isEmpty();
    }

    @Test
    void parsesTheHeaderWithoutSelectingAnAttachment() {
        assertThat(parser.parse(input("quadwords/solved/synthetic-solved-with-streak.txt")))
                .isInstanceOf(ParseResult.Parsed.class);
        assertThat(parser.parse(input(
                "quadwords/solved/synthetic-solved-with-streak.txt",
                new AttachmentMetadata("result.txt", "text/plain", 10))))
                .isInstanceOf(ParseResult.Parsed.class);
    }
    @ParameterizedTest
    @MethodSource("invalidFixtureCases")
    void classifiesInvalidQuadWordsFixturesWithStableCodes(String fixturePath, ParseErrorCode expectedCode) {
        assertInvalid(input(fixturePath, new AttachmentMetadata("result.png", "image/png", 10)), expectedCode);
    }

    static Stream<Arguments> invalidFixtureCases() {
        return Stream.of(
                Arguments.of("quadwords/invalid/synthetic-wrong-maximum.txt", ParseErrorCode.INVALID_ATTEMPT_RESULT),
                Arguments.of("quadwords/invalid/synthetic-invalid-date.txt", ParseErrorCode.INVALID_DATE),
                Arguments.of("quadwords/invalid/synthetic-invalid-duration.txt", ParseErrorCode.INVALID_DURATION));
    }

    @Test
    void returnsNotApplicableForMessagesWithoutQuadWordsHeader() {
        assertThat(parser.parse(input(
                "quadwords/invalid/synthetic-not-quadwords.txt",
                new AttachmentMetadata("result.png", "image/png", 10))))
                .isInstanceOf(ParseResult.NotApplicable.class);
    }

    @Test
    void rejectsMultipleHeadersAndUnexpectedFreeText() {
        String fixture = FixtureSupport.read("quadwords/solved/synthetic-solved-with-streak.txt");
        assertInvalid(new ShareParseInput(
                fixture + "\n" + fixture,
                List.of(new AttachmentMetadata("result.png", "image/png", 10))), ParseErrorCode.MALFORMED_HEADER);
        assertInvalid(new ShareParseInput(
                fixture + "\ncomment",
                List.of(new AttachmentMetadata("result.png", "image/png", 10))), ParseErrorCode.MALFORMED_HEADER);
    }

    private ShareParseInput input(String fixturePath, AttachmentMetadata... attachments) {
        return new ShareParseInput(FixtureSupport.read(fixturePath), List.of(attachments));
    }

    private void assertInvalid(ShareParseInput input, ParseErrorCode expectedCode) {
        ParseResult result = parser.parse(input);
        assertThat(result).isInstanceOf(ParseResult.Invalid.class);
        assertThat(((ParseResult.Invalid) result).errorCode()).isEqualTo(expectedCode);
    }
}
