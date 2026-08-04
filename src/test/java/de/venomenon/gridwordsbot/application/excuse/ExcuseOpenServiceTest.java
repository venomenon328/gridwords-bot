package de.venomenon.gridwordsbot.application.excuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.excuse.DailyComparisonSnapshot;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseReason;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionHistoryEntry;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.excuse.QuadWordsBoardAnalysis;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.ExcuseOpenUseCase;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExcuseOpenServiceTest {

    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final long MESSAGE_ID = 13L;
    private static final long RESULT_ID = 14L;
    private static final long AUTHOR_ID = 15L;
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 8, 3);

    @Test
    void opensExactlyThreeOptionsThroughTheAtomicLoadOrCreatePath() {
        Fixture fixture = new Fixture();
        when(fixture.states.loadOrCreateInitialOptions(eq(RESULT_ID), eq(1), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<ExcuseSelection> factory = invocation.getArgument(2, Supplier.class);
            return Optional.of(factory.get());
        });

        ExcuseOpenUseCase.Result opened = fixture.service.open(request());

        assertThat(opened).isInstanceOfSatisfying(ExcuseOpenUseCase.Options.class, options ->
                assertThat(options.options()).extracting(ExcuseOption::templateId)
                        .containsExactly("test.one", "test.two", "test.three"));
        ArgumentCaptor<Supplier<ExcuseSelection>> factory = supplierCaptor();
        verify(fixture.states).loadOrCreateInitialOptions(eq(RESULT_ID), eq(1), factory.capture());
        assertThat(factory.getValue().get()).isEqualTo(selection());
        verify(fixture.states, never()).storeInitialOptions(any(Long.class), any(Integer.class), any());
    }

    @Test
    void rejectsARequestOutsideTheConfiguredDiscordContextBeforeAnyPersistenceRead() {
        Fixture fixture = new Fixture();

        assertThat(fixture.service.open(new ExcuseOpenUseCase.Request(
                GUILD_ID + 1, CHANNEL_ID, MESSAGE_ID, RESULT_ID, AUTHOR_ID)))
                .isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.CONTEXT_MISMATCH));
        assertThat(fixture.service.open(new ExcuseOpenUseCase.Request(
                GUILD_ID, CHANNEL_ID + 1, MESSAGE_ID, RESULT_ID, AUTHOR_ID)))
                .isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.CONTEXT_MISMATCH));

        verifyNoInteractions(fixture.results, fixture.players, fixture.submissions, fixture.states);
    }

    @Test
    void rejectsAButtonPressByAnyoneOtherThanThePersistedResultAuthor() {
        Fixture fixture = new Fixture();

        ExcuseOpenUseCase.Result opened = fixture.service.open(new ExcuseOpenUseCase.Request(
                GUILD_ID, CHANNEL_ID, MESSAGE_ID, RESULT_ID, AUTHOR_ID + 1));

        assertThat(opened).isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.NOT_RESULT_AUTHOR));
        verify(fixture.results).findById(RESULT_ID);
        verifyNoInteractions(fixture.players, fixture.submissions, fixture.states);
    }

    @Test
    void rejectsAComponentWhoseResultIdDoesNotResolveToTheCanonicalResult() {
        Fixture fixture = new Fixture();
        long forgedResultId = RESULT_ID + 1;

        assertThat(fixture.service.open(new ExcuseOpenUseCase.Request(
                GUILD_ID, CHANNEL_ID, MESSAGE_ID, forgedResultId, AUTHOR_ID)))
                .isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.CONTEXT_MISMATCH));
        verify(fixture.results).findById(forgedResultId);
        verifyNoInteractions(fixture.players, fixture.submissions, fixture.states);
    }

    @Test
    void rejectsStaleMessageAndUnavailableOfferBeforeItCanCreateOptions() {
        Fixture staleMessage = new Fixture();
        assertThat(staleMessage.service.open(new ExcuseOpenUseCase.Request(
                GUILD_ID, CHANNEL_ID, MESSAGE_ID + 1, RESULT_ID, AUTHOR_ID)))
                .isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.CONTEXT_MISMATCH));
        verifyNoInteractions(staleMessage.players, staleMessage.submissions, staleMessage.states);

        Fixture unavailable = new Fixture();
        when(unavailable.states.find(RESULT_ID)).thenReturn(Optional.of(state(
                ExcuseStatus.INVALIDATED, NOW.plusSeconds(60))));
        assertThat(unavailable.service.open(request()))
                .isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.OFFER_UNAVAILABLE));
        verify(unavailable.states).find(RESULT_ID);
        verify(unavailable.states, never()).loadOrCreateInitialOptions(any(Long.class), any(Integer.class), any());
    }

    @Test
    void rejectsAnExpiredOfferWithoutDelegatingToTheAtomicPersistenceOperation() {
        Fixture fixture = new Fixture();
        when(fixture.states.find(RESULT_ID)).thenReturn(Optional.of(state(
                ExcuseStatus.AVAILABLE, NOW)));
        when(fixture.expirations.expireIfDue(RESULT_ID)).thenReturn(true);

        assertThat(fixture.service.open(request()))
                .isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.OFFER_UNAVAILABLE));
        verify(fixture.states, never()).loadOrCreateInitialOptions(any(Long.class), any(Integer.class), any());
        verify(fixture.expirations).expireIfDue(RESULT_ID);
    }

    @Test
    void keepsTheLatestSelectionHardExcludedAndReleasesOlderSoftExclusionsFromOldestFirst() {
        Fixture fixture = new Fixture();
        when(fixture.states.loadOrCreateInitialOptions(eq(RESULT_ID), eq(1), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<ExcuseSelection> factory = invocation.getArgument(2, Supplier.class);
            return Optional.of(factory.get());
        });
        when(fixture.states.findRecentSelections(AUTHOR_ID, 10)).thenReturn(List.of(
                new ExcuseSelectionHistoryEntry("hard", ExcuseTopic.GENERAL, NOW.minusSeconds(1)),
                new ExcuseSelectionHistoryEntry("soft-new", ExcuseTopic.TECHNICAL_FAILURE, NOW.minusSeconds(2)),
                new ExcuseSelectionHistoryEntry("soft-old", ExcuseTopic.RESPONSIBILITY, NOW.minusSeconds(3))));
        when(fixture.selector.select(any(), any(), any())).thenReturn(Optional.empty(), Optional.of(selection()));

        assertThat(fixture.service.open(request())).isInstanceOf(ExcuseOpenUseCase.Options.class);

        ArgumentCaptor<de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionRequest> requests =
                ArgumentCaptor.forClass(de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionRequest.class);
        verify(fixture.selector, org.mockito.Mockito.times(2)).select(any(), any(), requests.capture());
        assertThat(requests.getAllValues().getFirst().excludedTemplateIds())
                .containsExactlyInAnyOrder("hard", "soft-new", "soft-old");
        assertThat(requests.getAllValues().get(1).excludedTemplateIds())
                .containsExactlyInAnyOrder("hard", "soft-new");
        assertThat(requests.getAllValues().get(1).discouragedTopics())
                .containsExactlyInAnyOrder(ExcuseTopic.GENERAL, ExcuseTopic.TECHNICAL_FAILURE, ExcuseTopic.RESPONSIBILITY);
    }

    @Test
    void reopensThePersistedStyleRerollRoundAfterRestartWithoutOfferingAnotherReroll() {
        Fixture fixture = new Fixture();
        when(fixture.states.find(RESULT_ID)).thenReturn(Optional.of(state(
                ExcuseStatus.AVAILABLE, NOW.plusSeconds(60), true)));
        List<ExcuseOption> reroll = rerollOptions();
        when(fixture.states.findActiveOptions(RESULT_ID, 1, ExcuseRound.STYLE_REROLL)).thenReturn(reroll);

        ExcuseOpenUseCase.Result reopened = fixture.restartedService.open(request());

        assertThat(reopened).isEqualTo(new ExcuseOpenUseCase.Options(1, reroll, List.of()));
        verify(fixture.states).findActiveOptions(RESULT_ID, 1, ExcuseRound.STYLE_REROLL);
        verify(fixture.states, never()).loadOrCreateInitialOptions(any(Long.class), any(Integer.class), any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Supplier<ExcuseSelection>> supplierCaptor() {
        return ArgumentCaptor.forClass(Supplier.class);
    }

    private static ExcuseOpenUseCase.Request request() {
        return new ExcuseOpenUseCase.Request(GUILD_ID, CHANNEL_ID, MESSAGE_ID, RESULT_ID, AUTHOR_ID);
    }

    private static ExcuseState state(ExcuseStatus status, Instant expiresAt) {
        return state(status, expiresAt, false);
    }

    private static ExcuseState state(ExcuseStatus status, Instant expiresAt, boolean rerollUsed) {
        ExcuseEligibility eligibility = eligibility();
        return new ExcuseState(
                RESULT_ID,
                status,
                Optional.of(new ExcuseOfferMetadata(501L, "test-v1", "context-v1", 1, NOW.minusSeconds(60), expiresAt)),
                Optional.of(ExcuseOfferContext.initial(NOW.minusSeconds(30), eligibility)),
                rerollUsed,
                Optional.empty(),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60));
    }

    private static ExcuseEligibility eligibility() {
        DailyComparisonSnapshot comparison = new DailyComparisonSnapshot(
                GameType.GRIDWORDS, 0, false, OptionalInt.empty(), Duration.ZERO);
        return new ExcuseEligibility(
                true,
                Set.of(ExcuseReason.GRIDWORDS_LAST_ATTEMPT),
                new ExcuseContext(GameType.GRIDWORDS, Set.of(ExcuseReason.GRIDWORDS_LAST_ATTEMPT), Map.of()),
                comparison,
                QuadWordsBoardAnalysis.boardless());
    }

    private static ExcuseSelection selection() {
        return new ExcuseSelection(ExcuseRound.INITIAL, List.of(
                new ExcuseOption(ExcuseRound.INITIAL, 1, "test.one", ExcuseStyle.TECHNICAL,
                        ExcuseTopic.TECHNICAL_FAILURE, "Text eins"),
                new ExcuseOption(ExcuseRound.INITIAL, 2, "test.two", ExcuseStyle.TACTICAL,
                        ExcuseTopic.LONG_TERM_PLAN, "Text zwei"),
                new ExcuseOption(ExcuseRound.INITIAL, 3, "test.three", ExcuseStyle.LEGAL,
                        ExcuseTopic.RESPONSIBILITY, "Text drei")));
    }

    private static List<ExcuseOption> rerollOptions() {
        return List.of(
                new ExcuseOption(ExcuseRound.STYLE_REROLL, 1, "reroll.one", ExcuseStyle.COSMIC,
                        ExcuseTopic.GENERAL, "Neu eins"),
                new ExcuseOption(ExcuseRound.STYLE_REROLL, 2, "reroll.two", ExcuseStyle.COSMIC,
                        ExcuseTopic.GENERAL, "Neu zwei"),
                new ExcuseOption(ExcuseRound.STYLE_REROLL, 3, "reroll.three", ExcuseStyle.COSMIC,
                        ExcuseTopic.GENERAL, "Neu drei"));
    }

    private static GameResultStore.StoredGameResult result() {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.GRIDWORDS,
                GAME_DATE,
                new ShareOutcome.Solved(6, 6),
                Duration.ofSeconds(75),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(java.util.Collections.nCopies(6, "\u2B1C".repeat(5)))));
        return new GameResultStore.StoredGameResult(
                RESULT_ID, AUTHOR_ID, parsed, "share", "v1", OptionalLong.of(MESSAGE_ID), NOW, NOW);
    }

    private static SubmissionStore.CanonicalRefreshCandidate publication() {
        return new SubmissionStore.CanonicalRefreshCandidate(new SubmissionStore.StoredSubmission(
                501L,
                GUILD_ID,
                CHANNEL_ID,
                AUTHOR_ID,
                "share",
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                Optional.of(RESULT_ID),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                NOW,
                NOW), 0);
    }

    private static final class Fixture {
        final GameResultStore results = mock(GameResultStore.class);
        final PlayerStore players = mock(PlayerStore.class);
        final SubmissionStore submissions = mock(SubmissionStore.class);
        final ExcuseStateStore states = mock(ExcuseStateStore.class);
        final ExcuseEligibilityPolicy policy = mock(ExcuseEligibilityPolicy.class);
        final ExcuseSelector selector = mock(ExcuseSelector.class);
        final ExcuseCatalog catalog = mock(ExcuseCatalog.class);
        final de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase expirations = mock(
                de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase.class);
        final ExcuseOpenService service = newService();
        final ExcuseOpenService restartedService = newService();

        private ExcuseOpenService newService() {
            return new ExcuseOpenService(
                    GUILD_ID,
                    CHANNEL_ID,
                    results,
                    players,
                    submissions,
                    states,
                    policy,
                    catalog,
                    selector,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    expirations);
        }

        Fixture() {
            when(results.findById(RESULT_ID)).thenReturn(Optional.of(result()));
            when(players.findGameParticipationPeriods()).thenReturn(List.of(
                    new GameParticipationPeriod(AUTHOR_ID, GameType.GRIDWORDS, GAME_DATE, null)));
            when(submissions.findCurrentCanonicalPublicationCandidate(RESULT_ID)).thenReturn(Optional.of(publication()));
            when(states.find(RESULT_ID)).thenReturn(Optional.of(state(ExcuseStatus.AVAILABLE, NOW.plusSeconds(60))));
            when(policy.evaluate(any(), any())).thenReturn(eligibility());
            when(selector.select(any(), any(), any())).thenReturn(Optional.of(selection()));
        }
    }
}
