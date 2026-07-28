package de.venomenon.gridwordsbot.domain.model;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ParserDomainModelTest {

    @Test
    void preventsInvalidShareOutcomeStates() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ShareOutcome.Solved(0, 6));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShareOutcome.Solved(7, 6));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShareOutcome.Unsolved(0));
    }

    @Test
    void preventsNonCanonicalOrMalformedBoards() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedBoard(List.of("⬜⬜⬜⬜")));
        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedBoard(List.of("⬛⬜⬜⬜⬜")));
    }

    @Test
    void keepsTransportMetadataAndParserInputImmutableAndValid() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AttachmentMetadata("image.png", "image/png", -1));
        assertThat(new AttachmentMetadata("result.bin", "image/png", 1).isPlausibleImage()).isTrue();
        assertThat(new AttachmentMetadata("result.PNG", "", 1).isPlausibleImage()).isTrue();
        assertThat(new AttachmentMetadata("result.jpg", "", 1).isPlausibleImage()).isTrue();
        assertThat(new AttachmentMetadata("result.jpeg", "", 1).isPlausibleImage()).isTrue();
        assertThat(new AttachmentMetadata("result.webp", "", 1).isPlausibleImage()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> new ParsedGameResult(
                GameType.GRIDWORDS,
                LocalDate.of(2026, 7, 27),
                new ShareOutcome.Solved(1, 6),
                Duration.ZERO,
                OptionalInt.empty(),
                Optional.empty()));
    }

    @Test
    void keepsInvalidResultsSafeAndStructured() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ParseResult.Invalid(ParseErrorCode.INVALID_DATE, " "));
    }
}
