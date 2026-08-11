package de.venomenon.gridwordsbot.parser.gridwords;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.FixtureSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GridWordsLongDurationShareParserTest {

    private final GridWordsShareParser parser = new GridWordsShareParser();

    @Test
    void parsesUnsolvedShareWithHourMinuteSecondDuration() {
        ParseResult result = parse(FixtureSupport.read("gridwords/unsolved/synthetic-unsolved-long-duration.txt"));

        assertThat(result).isInstanceOf(ParseResult.Parsed.class);
        ParseResult.Parsed parsed = (ParseResult.Parsed) result;
        assertThat(parsed.result().outcome()).isEqualTo(new ShareOutcome.Unsolved(6));
        assertThat(parsed.result().duration())
                .isEqualTo(Duration.ofHours(7).plusMinutes(38).plusSeconds(28));
    }

    @Test
    void rejectsOutOfRangeMinutesInHourMinuteSecondDuration() {
        String content = FixtureSupport.read("gridwords/unsolved/synthetic-unsolved-long-duration.txt")
                .replace("7:38:28", "7:60:28");

        assertInvalidDuration(parse(content));
    }

    @Test
    void rejectsOutOfRangeSecondsInHourMinuteSecondDuration() {
        String content = FixtureSupport.read("gridwords/unsolved/synthetic-unsolved-long-duration.txt")
                .replace("7:38:28", "7:38:99");

        assertInvalidDuration(parse(content));
    }

    private ParseResult parse(String content) {
        return parser.parse(new ShareParseInput(content, List.of()));
    }

    private static void assertInvalidDuration(ParseResult result) {
        assertThat(result).isInstanceOf(ParseResult.Invalid.class);
        assertThat(((ParseResult.Invalid) result).errorCode()).isEqualTo(ParseErrorCode.INVALID_DURATION);
    }
}
