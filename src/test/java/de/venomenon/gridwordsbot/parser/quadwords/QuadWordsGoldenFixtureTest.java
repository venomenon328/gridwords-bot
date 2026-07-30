package de.venomenon.gridwordsbot.parser.quadwords;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuadWordsGoldenFixtureTest {

    private final QuadWordsImageParser parser = new QuadWordsImageParser();

    @ParameterizedTest
    @MethodSource("fixtures")
    void matchesTheReviewedCanonicalOutputExactly(Path image, Path expected, ShareOutcome outcome) throws Exception {
        QuadWordsImageParser.Parse result = parser.parse(Files.readAllBytes(image), outcome);

        assertThat(result).isInstanceOf(QuadWordsImageParser.Parse.Parsed.class);
        String actual = canonical(((QuadWordsImageParser.Parse.Parsed) result).boards());
        assertThat(actual).isEqualTo(Files.readString(expected).stripTrailing());
    }

    private static String canonical(QuadWordsBoards boards) {
        return "Oben links\n" + boards.topLeft().canonicalText()
                + "\n\nOben rechts\n" + boards.topRight().canonicalText()
                + "\n\nUnten links\n" + boards.bottomLeft().canonicalText()
                + "\n\nUnten rechts\n" + boards.bottomRight().canonicalText();
    }

    private static Stream<Arguments> fixtures() {
        return Stream.of(
                solved("quadwords-solved-7-9-grey", 7),
                solved("quadwords-solved-7-9-white", 7),
                solved("quadwords-solved-9-9-grey", 9),
                solved("quadwords-solved-9-9-white", 9),
                Arguments.of(
                        Path.of("fixtures/quadwords/unsolved/quadwords-unsolved-x-9-white.png"),
                        Path.of("fixtures/quadwords/unsolved/quadwords-unsolved-x-9-white.expected.txt"),
                        new ShareOutcome.Unsolved(9)));
    }

    private static Arguments solved(String name, int attempts) {
        return Arguments.of(
                Path.of("fixtures/quadwords/solved", name + ".png"),
                Path.of("fixtures/quadwords/solved", name + ".expected.txt"),
                new ShareOutcome.Solved(attempts, 9));
    }
}
