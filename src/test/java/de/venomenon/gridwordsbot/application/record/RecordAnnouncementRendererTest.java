package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecordAnnouncementRendererTest {
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-06T20:00:00Z");

    @Test
    void rendersStableSanitizedResultFactsWithAHiddenPublicationKey() {
        RecordAnnouncementRegistration registration = registration(List.of(EVENT));
        RecordAnnouncementRenderer renderer = new RecordAnnouncementRenderer();

        RenderedRecordAnnouncement rendered = renderer.render(new RecordAnnouncementRenderInput(registration,
                List.of(event(RecordEventType.RESULT_RECORD_BROKEN)), Map.of(7L, "<@7> Georgia", 5L, "@Tobias")));

        assertThat(rendered.pages()).hasSize(1);
        assertThat(rendered.pages().getFirst().description()).contains("GridWords", "Schnellste Lösung", "Georgia", "Tobias")
                .doesNotContain("@", "<", ">");
        assertThat(rendered.pages().getFirst().footer()).startsWith("record-announcement:").contains("|page:1/1");
        assertThat(renderer.render(new RecordAnnouncementRenderInput(registration,
                List.of(event(RecordEventType.RESULT_RECORD_BROKEN)), Map.of(7L, "<@7> Georgia", 5L, "@Tobias"))))
                .isEqualTo(rendered);
    }

    @Test
    void rendersNearMissWithTheCorrectStreakDistance() {
        RecordEventSnapshot nearMiss = event(RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END);
        RecordAnnouncementRenderer renderer = new RecordAnnouncementRenderer();

        RenderedRecordAnnouncement rendered = renderer.render(new RecordAnnouncementRenderInput(registration(List.of(EVENT)),
                List.of(nearMiss), Map.of(7L, "Georgia")));

        assertThat(rendered.pages().getFirst().description()).contains("Knapp verpasst", "Abstand 1 Tag");
    }

    @Test
    void rendersServerWideSeriesCandidatesAndPreviousHoldersWhenTheyExist() {
        RecordEventSnapshot crossed = serverWideSeriesEvent(RecordEventType.SERIES_RECORD_CROSSED);

        RenderedRecordAnnouncement rendered = new RecordAnnouncementRenderer().render(new RecordAnnouncementRenderInput(
                registration(List.of(EVENT)), List.of(crossed), Map.of(5L, "Vorher", 7L, "Ada")));

        assertThat(rendered.pages().getFirst().description())
                .contains("Kandidat Ada", "Vorheriger Halter: Vorher");
    }

    @Test
    void rendersServerWideNearMissWithCandidateAndPreviousHolder() {
        RecordEventSnapshot nearMiss = serverWideSeriesEvent(RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END);

        RenderedRecordAnnouncement rendered = new RecordAnnouncementRenderer().render(new RecordAnnouncementRenderInput(
                registration(List.of(EVENT)), List.of(nearMiss), Map.of(5L, "Vorher", 7L, "Ada")));

        assertThat(rendered.pages().getFirst().description())
                .contains("Kandidat Ada verpasst knapp", "Vorheriger Halter: Vorher", "Abstand 1 Tag");
    }

    @Test
    void rendersNegativeTieWithAnonymizedFallback() {
        RecordEventSnapshot tied = negativeTieEvent();

        RenderedRecordAnnouncement rendered = new RecordAnnouncementRenderer().render(new RecordAnnouncementRenderInput(
                registration(List.of(EVENT)), List.of(tied), Map.of()));

        assertThat(rendered.pages().getFirst().description())
                .contains("GridWords-Durststrecke", "Rekord", "Ehemaliger Spieler")
                .doesNotContain("@", "<", ">");
    }

    @Test
    void paginatesDeterministicallyWithinTheConfiguredDescriptionLimit() {
        List<RecordEventSnapshot> events = IntStream.range(0, 80)
                .mapToObj(RecordAnnouncementRendererTest::resultEvent)
                .toList();
        List<UUID> ids = events.stream().map(snapshot -> snapshot.draft().eventId()).toList();
        RecordAnnouncementRenderer renderer = new RecordAnnouncementRenderer();

        RenderedRecordAnnouncement rendered = renderer.render(new RecordAnnouncementRenderInput(
                registration(ids), events, Map.of(7L, "Ada", 5L, "Vorher")));
        RenderedRecordAnnouncement replay = renderer.render(new RecordAnnouncementRenderInput(
                registration(ids), events, Map.of(7L, "Ada", 5L, "Vorher")));

        assertThat(rendered).isEqualTo(replay);
        assertThat(rendered.pages()).hasSizeGreaterThan(1);
        for (int index = 0; index < rendered.pages().size(); index++) {
            RenderedRecordAnnouncementPage page = rendered.pages().get(index);
            assertThat(page.position()).isEqualTo(index);
            assertThat(page.description().length()).isLessThanOrEqualTo(3_800);
            assertThat(page.footer()).endsWith("|page:" + (index + 1) + "/" + rendered.pages().size());
        }
    }

    private static RecordAnnouncementRegistration registration(List<UUID> ids) {
        return new RecordAnnouncementRegistration(new RecordAnnouncementKey(1, 2, "live-result:1:player:7:LIVE_EVALUATION"),
                RecordAnnouncementSubject.player(7), RecordAnnouncementPhase.LIVE_EVALUATION,
                RecordAnnouncementProjection.CREATE, RecordAnnouncementRenderer.VERSION,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", ids);
    }

    private static RecordEventSnapshot event(RecordEventType type) {
        DurationRecordValue previous = new DurationRecordValue(Duration.ofSeconds(type == RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END ? 10 : 95));
        de.venomenon.gridwordsbot.domain.record.RecordValue next = type == RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END
                ? new StreakRecordValue(9, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 9))
                : new DurationRecordValue(Duration.ofSeconds(74));
        Optional<de.venomenon.gridwordsbot.domain.record.RecordValue> before = type == RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END
                ? Optional.of(new StreakRecordValue(10, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10)))
                : Optional.of(previous);
        RecordSourceReference.GameResult source = new RecordSourceReference.GameResult(1, 1, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6));
        RecordEventDraft draft = new RecordEventDraft(EVENT, "event", new RecordStateKey(1,
                new RecordDefinitionKey(type == RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END
                        ? "streak.activity.personal" : "result.gridwords.fastest-solution.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7)), type, before, next,
                Optional.of(5L), Optional.of(7L), Optional.of(source), source, "live-result:1",
                RecordProcessingOrigin.LIVE_SUBMISSION, NOW);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private static RecordEventSnapshot serverWideSeriesEvent(RecordEventType type) {
        boolean nearMiss = type == RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END;
        RecordSourceReference.GameResult source = new RecordSourceReference.GameResult(
                1, 1, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6));
        RecordEventDraft draft = new RecordEventDraft(EVENT, "server-series", new RecordStateKey(1,
                new RecordDefinitionKey("streak.gridwords-solved.server-individual"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.ServerIndividual()), type,
                Optional.of(new StreakRecordValue(
                        nearMiss ? 5 : 4, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, nearMiss ? 5 : 4))),
                new StreakRecordValue(
                        nearMiss ? 4 : 5, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, nearMiss ? 4 : 5)),
                Optional.of(5L), Optional.of(7L), Optional.of(source), source, "server-series",
                RecordProcessingOrigin.LIVE_SUBMISSION, NOW);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private static RecordEventSnapshot negativeTieEvent() {
        RecordSourceReference.GameResult source = new RecordSourceReference.GameResult(
                1, 1, 99, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6));
        RecordEventDraft draft = new RecordEventDraft(EVENT, "negative-tie", new RecordStateKey(1,
                new RecordDefinitionKey("streak.gridwords-drought.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(99)),
                RecordEventType.SERIES_RECORD_TIED_AT_END,
                Optional.of(new StreakRecordValue(3, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3))),
                new StreakRecordValue(3, LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 6)),
                Optional.of(99L), Optional.of(99L), Optional.of(source), source, "negative-tie",
                RecordProcessingOrigin.DAY_CLOSE, NOW);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private static RecordEventSnapshot resultEvent(int index) {
        UUID id = UUID.nameUUIDFromBytes(("page-event-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long playerId = index + 100L;
        RecordSourceReference.GameResult source = new RecordSourceReference.GameResult(
                index + 1L, 1, playerId, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6));
        RecordEventDraft draft = new RecordEventDraft(id, "page-event:" + index, new RecordStateKey(1,
                new RecordDefinitionKey("result.gridwords.fastest-solution.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(playerId)),
                RecordEventType.RESULT_RECORD_BROKEN,
                Optional.of(new DurationRecordValue(Duration.ofSeconds(120 + index))),
                new DurationRecordValue(Duration.ofSeconds(60 + index)), Optional.of(5L), Optional.of(playerId),
                Optional.of(source), source, "page-trigger", RecordProcessingOrigin.LIVE_SUBMISSION, NOW);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), NOW, NOW);
    }
}
