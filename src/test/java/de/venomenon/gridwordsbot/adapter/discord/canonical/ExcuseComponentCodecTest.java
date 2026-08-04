package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;

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
}
