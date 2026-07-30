package de.venomenon.gridwordsbot.parser.quadwords;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuadWordsFixtureDumpTest {

    @Test
    void dumpCanonicalFixtureOutputsForGoldenFiles() throws Exception {
        QuadWordsImageParser parser = new QuadWordsImageParser();
        List<Fixture> fixtures = List.of(
                new Fixture("quadwords-solved-7-9-grey", Path.of("fixtures/quadwords/solved/quadwords-solved-7-9-grey.png"), new ShareOutcome.Solved(7, 9)),
                new Fixture("quadwords-solved-7-9-white", Path.of("fixtures/quadwords/solved/quadwords-solved-7-9-white.png"), new ShareOutcome.Solved(7, 9)),
                new Fixture("quadwords-solved-9-9-grey", Path.of("fixtures/quadwords/solved/quadwords-solved-9-9-grey.png"), new ShareOutcome.Solved(9, 9)),
                new Fixture("quadwords-solved-9-9-white", Path.of("fixtures/quadwords/solved/quadwords-solved-9-9-white.png"), new ShareOutcome.Solved(9, 9)),
                new Fixture("quadwords-unsolved-x-9-white", Path.of("fixtures/quadwords/unsolved/quadwords-unsolved-x-9-white.png"), new ShareOutcome.Unsolved(9)));

        for (Fixture fixture : fixtures) {
            QuadWordsImageParser.Parse result = parser.parse(Files.readAllBytes(fixture.path()), fixture.outcome());
            if (!(result instanceof QuadWordsImageParser.Parse.Parsed parsed)) {
                throw new AssertionError(fixture.name() + " did not parse: " + result);
            }
            System.out.println("GOLDEN-BEGIN " + fixture.name());
            List<QuadWordsBoard> boards = parsed.boards().ordered();
            for (int index = 0; index < boards.size(); index++) {
                System.out.println("BOARD " + index);
                System.out.println(boards.get(index).canonicalText());
            }
            System.out.println("GOLDEN-END " + fixture.name());
        }
    }

    private record Fixture(String name, Path path, ShareOutcome outcome) { }
}
