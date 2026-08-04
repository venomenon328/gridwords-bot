package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import org.junit.jupiter.api.Test;

class ExcuseComponentCodecTest {

    private final ExcuseComponentCodec codec = new ExcuseComponentCodec();

    @Test
    void roundTripsTheStrictVersionedOpenComponentWithoutEditorialText() {
        String id = codec.encodeOpen(42L);

        assertThat(id).isEqualTo("excuse:v1:open:42");
        assertThat(codec.encodeOpen(Long.MAX_VALUE)).hasSizeLessThanOrEqualTo(100);
        assertThat(codec.decodeOpen(id)).hasValue(new ExcuseComponentCodec.Open(42L));
    }

    @Test
    void rejectsUnknownVersionsOperationsAndMalformedResultIds() {
        assertThat(codec.decodeOpen("excuse:v2:open:42")).isEmpty();
        assertThat(codec.decodeOpen("excuse:v1:pick:42")).isEmpty();
        assertThat(codec.decodeOpen("excuse:v1:open:0")).isEmpty();
        assertThat(codec.decodeOpen("excuse:v1:open:42:template")).isEmpty();
    }

    @Test
    void roundTripsEveryTextFreeFollowUpComponentWithItsFullServerValidationCoordinates() {
        assertThat(codec.decodePick(codec.encodePick(42L, 3, ExcuseRound.STYLE_REROLL, 2)))
                .hasValue(new ExcuseComponentCodec.Pick(42L, 3, ExcuseRound.STYLE_REROLL, 2));
        assertThat(codec.decodeReroll(codec.encodeReroll(42L, 3)))
                .hasValue(new ExcuseComponentCodec.Reroll(42L, 3));
        assertThat(codec.decodeStyle(codec.encodeStyle(42L, 3)))
                .hasValue(new ExcuseComponentCodec.Style(42L, 3));
        assertThat(codec.decodeDecline(codec.encodeDecline(42L, 3)))
                .hasValue(new ExcuseComponentCodec.Decline(42L, 3));
        assertThat(codec.encodePick(Long.MAX_VALUE, Integer.MAX_VALUE, ExcuseRound.INITIAL, 3))
                .hasSizeLessThanOrEqualTo(100);
        assertThat(codec.encodeStyleValue(ExcuseStyle.NORTHERN_GERMAN)).isEqualTo("NORTHERN_GERMAN");
        assertThat(codec.decodeStyleValue("NORTHERN_GERMAN")).hasValue(ExcuseStyle.NORTHERN_GERMAN);
    }

    @Test
    void rejectsMalformedRoundsPositionsGenerationsAndStyleValues() {
        assertThat(codec.decodePick("excuse:v1:pick:42:1:UNKNOWN:1")).isEmpty();
        assertThat(codec.decodePick("excuse:v1:pick:42:1:INITIAL:4")).isEmpty();
        assertThat(codec.decodePick("excuse:v1:pick:42:0:INITIAL:1")).isEmpty();
        assertThat(codec.decodeReroll("excuse:v1:reroll:42:1:text")).isEmpty();
        assertThat(codec.decodeStyle("excuse:v1:style:0:1")).isEmpty();
        assertThat(codec.decodeDecline("excuse:v1:decline:42:0")).isEmpty();
        assertThat(codec.decodeStyleValue("technisch")).isEmpty();
    }
}
