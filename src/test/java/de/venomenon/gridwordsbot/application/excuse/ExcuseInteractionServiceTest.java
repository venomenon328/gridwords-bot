package de.venomenon.gridwordsbot.application.excuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.excuse.DailyComparisonSnapshot;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.excuse.QuadWordsBoardAnalysis;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseReason;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseContext;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.ExcuseInteractionUseCase;
import de.venomenon.gridwordsbot.port.out.CanonicalRefreshWakeUp;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExcuseInteractionServiceTest {
    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final long MESSAGE_ID = 13L;
    private static final long RESULT_ID = 14L;
    private static final long AUTHOR_ID = 15L;
    private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 8, 4);

    @Test
    void offersOnlyDeterministicallyAvailableStylesWithoutUsingRandomSelection() {
        Fixture fixture = new Fixture();
        when(fixture.states.findOptions(RESULT_ID, 1)).thenReturn(initialOptions());
        when(fixture.selector.availableStyles(any(), any(), any())).thenReturn(List.of(ExcuseStyle.COSMIC));

        assertThat(fixture.service.openStyleMenu(action()))
                .isEqualTo(new ExcuseInteractionUseCase.StyleMenu(List.of(ExcuseStyle.COSMIC)));

        verify(fixture.selector, never()).select(any(), any(), any());
    }

    @Test
    void persistsExactlyOneRerollRoundBeforeReturningItsEphemeralOptions() {
        Fixture fixture = new Fixture();
        when(fixture.states.findOptions(RESULT_ID, 1)).thenReturn(initialOptions());
        when(fixture.selector.availableStyles(any(), any(), any())).thenReturn(List.of(ExcuseStyle.COSMIC));
        ExcuseSelection reroll = new ExcuseSelection(ExcuseRound.STYLE_REROLL, rerollOptions());
        when(fixture.selector.select(any(), any(), any())).thenReturn(Optional.of(reroll));
        doAnswer(invocation -> Optional.of(((Supplier<ExcuseSelection>) invocation.getArgument(2)).get()))
                .when(fixture.states).loadOrCreateStyleRerollOptions(eq(RESULT_ID), eq(1), any());

        assertThat(fixture.service.selectStyle(new ExcuseInteractionUseCase.StyleRequest(action(), ExcuseStyle.COSMIC)))
                .isEqualTo(new ExcuseInteractionUseCase.Options(reroll.options(), List.of()));

        ArgumentCaptor<ExcuseSelectionRequest> request = ArgumentCaptor.forClass(ExcuseSelectionRequest.class);
        verify(fixture.selector).select(any(), any(), request.capture());
        assertThat(request.getValue().requiredStyle()).contains(ExcuseStyle.COSMIC);
        assertThat(request.getValue().excludedTemplateIds()).containsExactlyInAnyOrder("one", "two", "three");
        verify(fixture.refreshWakeUp, never()).wakeUp(any(Long.class));
    }

    @Test
    void selectionUsesAtomicRefreshHandoffAndARefreshWakeUpOnlyAfterTheCommit() {
        Fixture fixture = new Fixture();
        when(fixture.states.selectAndRequestCanonicalRefresh(any())).thenReturn(Optional.of(selectedState()));

        assertThat(fixture.service.pick(new ExcuseInteractionUseCase.PickRequest(
                action(), ExcuseRound.INITIAL, 2))).isEqualTo(ExcuseInteractionUseCase.Selected.INSTANCE);

        verify(fixture.states).selectAndRequestCanonicalRefresh(any());
        verify(fixture.states, never()).select(any());
        verify(fixture.refreshWakeUp).wakeUp(RESULT_ID);
    }

    @Test
    void declineUsesTheSameAtomicRefreshHandoffAndForeignActorsCannotConsumeAnything() {
        Fixture fixture = new Fixture();
        when(fixture.states.declineAndRequestCanonicalRefresh(RESULT_ID, 1, NOW)).thenReturn(Optional.of(declinedState()));

        assertThat(fixture.service.decline(action())).isEqualTo(ExcuseInteractionUseCase.Declined.INSTANCE);
        verify(fixture.refreshWakeUp).wakeUp(RESULT_ID);

        ExcuseInteractionUseCase.ActionRequest foreign = new ExcuseInteractionUseCase.ActionRequest(
                GUILD_ID, CHANNEL_ID, MESSAGE_ID, RESULT_ID, AUTHOR_ID + 1, 1);
        assertThat(fixture.service.decline(foreign))
                .isEqualTo(new ExcuseInteractionUseCase.Rejected(ExcuseInteractionUseCase.Reason.NOT_RESULT_AUTHOR));
        verify(fixture.states, never()).decline(any(Long.class), any(Integer.class), any());
    }

    private static ExcuseInteractionUseCase.ActionRequest action() {
        return new ExcuseInteractionUseCase.ActionRequest(GUILD_ID, CHANNEL_ID, MESSAGE_ID, RESULT_ID, AUTHOR_ID, 1);
    }

    private static ExcuseState state(ExcuseStatus status, boolean rerollUsed) {
        ExcuseEligibility eligibility = eligibility();
        return new ExcuseState(RESULT_ID, status,
                Optional.of(new ExcuseOfferMetadata(501L, "test-v1", "context-v1", 1,
                        NOW.minusSeconds(60), NOW.plusSeconds(60))),
                Optional.of(ExcuseOfferContext.initial(NOW.minusSeconds(30), eligibility)), rerollUsed,
                Optional.empty(), NOW.minusSeconds(60), NOW.minusSeconds(60));
    }

    private static ExcuseState selectedState() {
        return new ExcuseState(RESULT_ID, ExcuseStatus.SELECTED,
                state(ExcuseStatus.AVAILABLE, false).offer(), state(ExcuseStatus.AVAILABLE, false).offerContext(), false,
                Optional.of(new de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionSnapshot(
                        ExcuseRound.INITIAL, 2, "two", ExcuseStyle.TACTICAL, ExcuseTopic.LONG_TERM_PLAN, "Text zwei", NOW)),
                NOW.minusSeconds(60), NOW);
    }

    private static ExcuseState declinedState() {
        return state(ExcuseStatus.DECLINED, false);
    }

    private static ExcuseEligibility eligibility() {
        return new ExcuseEligibility(true, Set.of(ExcuseReason.GRIDWORDS_LAST_ATTEMPT),
                new ExcuseContext(GameType.GRIDWORDS, Set.of(ExcuseReason.GRIDWORDS_LAST_ATTEMPT), Map.of()),
                new DailyComparisonSnapshot(GameType.GRIDWORDS, 0, false, OptionalInt.empty(), Duration.ZERO),
                QuadWordsBoardAnalysis.boardless());
    }

    private static List<ExcuseOption> initialOptions() {
        return List.of(option(ExcuseRound.INITIAL, 1, "one", ExcuseStyle.TECHNICAL),
                option(ExcuseRound.INITIAL, 2, "two", ExcuseStyle.TACTICAL),
                option(ExcuseRound.INITIAL, 3, "three", ExcuseStyle.LEGAL));
    }

    private static List<ExcuseOption> rerollOptions() {
        return List.of(option(ExcuseRound.STYLE_REROLL, 1, "cosmic-one", ExcuseStyle.COSMIC),
                option(ExcuseRound.STYLE_REROLL, 2, "cosmic-two", ExcuseStyle.COSMIC),
                option(ExcuseRound.STYLE_REROLL, 3, "cosmic-three", ExcuseStyle.COSMIC));
    }

    private static ExcuseOption option(ExcuseRound round, int position, String id, ExcuseStyle style) {
        return new ExcuseOption(round, position, id, style, ExcuseTopic.GENERAL, "Text " + id);
    }

    private static GameResultStore.StoredGameResult result() {
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, GAME_DATE, new ShareOutcome.Solved(6, 6),
                Duration.ofSeconds(75), OptionalInt.empty(),
                Optional.of(new NormalizedBoard(java.util.Collections.nCopies(6, "⬜".repeat(5)))));
        return new GameResultStore.StoredGameResult(RESULT_ID, AUTHOR_ID, parsed, "share", "v1", OptionalLong.of(MESSAGE_ID), NOW, NOW);
    }

    private static SubmissionStore.CanonicalRefreshCandidate publication() {
        return new SubmissionStore.CanonicalRefreshCandidate(new SubmissionStore.StoredSubmission(501L, GUILD_ID, CHANNEL_ID,
                AUTHOR_ID, "share", SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED, Optional.of(RESULT_ID),
                List.of(), Optional.empty(), Optional.empty(), NOW, NOW), 0);
    }

    private static final class Fixture {
        final GameResultStore results = mock(GameResultStore.class);
        final PlayerStore players = mock(PlayerStore.class);
        final SubmissionStore submissions = mock(SubmissionStore.class);
        final ExcuseStateStore states = mock(ExcuseStateStore.class);
        final ExcuseEligibilityPolicy policy = mock(ExcuseEligibilityPolicy.class);
        final ExcuseSelector selector = mock(ExcuseSelector.class);
        final CanonicalRefreshWakeUp refreshWakeUp = mock(CanonicalRefreshWakeUp.class);
        final ExcuseInteractionService service = new ExcuseInteractionService(GUILD_ID, CHANNEL_ID, results, players,
                submissions, states, policy, mock(ExcuseCatalog.class), selector, Clock.fixed(NOW, ZoneOffset.UTC), refreshWakeUp);

        Fixture() {
            when(results.findById(RESULT_ID)).thenReturn(Optional.of(result()));
            when(players.findGameParticipationPeriods()).thenReturn(List.of(
                    new GameParticipationPeriod(AUTHOR_ID, GameType.GRIDWORDS, GAME_DATE, null)));
            when(submissions.findCurrentCanonicalPublicationCandidate(RESULT_ID)).thenReturn(Optional.of(publication()));
            when(states.find(RESULT_ID)).thenReturn(Optional.of(state(ExcuseStatus.AVAILABLE, false)));
            when(policy.evaluate(any(), any())).thenReturn(eligibility());
        }
    }
}
