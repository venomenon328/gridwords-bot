package de.venomenon.gridwordsbot.application.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.venomenon.gridwordsbot.domain.model.*;
import de.venomenon.gridwordsbot.domain.streak.*;
import de.venomenon.gridwordsbot.port.out.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalGridWordsPublicationServiceTest {
 private static final long TOBIAS=1L, GEORGIA=2L, SOURCE=10L, RESULT=20L;
 private GameResultStore results; private PlayerStore players; private SubmissionStore submissions; private CanonicalMessageGateway discord; private CanonicalGridWordsPublicationService service; private GameResultStore.StoredGameResult result;
 @BeforeEach void setUp(){ results=mock(GameResultStore.class);players=mock(PlayerStore.class);submissions=mock(SubmissionStore.class);discord=mock(CanonicalMessageGateway.class);result=result(OptionalLong.empty()); service=new CanonicalGridWordsPublicationService(results,players,submissions,discord,Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"),ZoneOffset.UTC),ZoneId.of("Europe/Berlin"),List.of(TOBIAS,GEORGIA)); when(players.findByDiscordUserId(TOBIAS)).thenReturn(Optional.of(new PlayerStore.StoredPlayer(TOBIAS,"Tobias",true,false,Instant.EPOCH,Instant.EPOCH))); when(results.findAll()).thenReturn(List.of(result)); }
 @Test void publishesFirstGridWordsExactlyOnceAndPersistsState(){ stored(SubmissionStore.SubmissionState.RESULT_STORED); when(results.findById(RESULT)).thenReturn(Optional.of(result)); when(results.claimCanonicalPublication(eq(RESULT),any())).thenReturn(true); when(discord.findByPublicationKey(anyLong(),anyString())).thenReturn(OptionalLong.empty()); when(discord.create(anyLong(),any())).thenReturn(99L); when(submissions.completeCanonicalPublication(SOURCE,RESULT,99L)).thenReturn(true); assertThat(service.publish(SOURCE)).isTrue(); verify(discord).create(eq(12L),any()); verify(submissions).completeCanonicalPublication(SOURCE,RESULT,99L); verify(results).releaseCanonicalPublicationClaim(RESULT); }
 @Test void replayOfPublishedSubmissionDoesNotSendOrEdit(){ stored(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED); assertThat(service.publish(SOURCE)).isTrue(); verifyNoInteractions(results,players,discord); verify(submissions,never()).completeCanonicalPublication(anyLong(),anyLong(),anyLong()); }
 @Test void replacesMissingCanonicalMessageUnderTheClaim(){ result=result(OptionalLong.of(88L)); stored(SubmissionStore.SubmissionState.RESULT_STORED); when(results.findById(RESULT)).thenReturn(Optional.of(result)); when(results.claimCanonicalPublication(eq(RESULT),any())).thenReturn(true); doThrow(new CanonicalMessageGateway.UnknownMessageException()).when(discord).edit(eq(12L),eq(88L),any()); when(discord.findByPublicationKey(anyLong(),anyString())).thenReturn(OptionalLong.empty()); when(discord.create(anyLong(),any())).thenReturn(99L); when(submissions.completeCanonicalPublication(SOURCE,RESULT,99L)).thenReturn(true); assertThat(service.publish(SOURCE)).isTrue(); verify(discord).create(eq(12L),any()); verify(submissions).completeCanonicalPublication(SOURCE,RESULT,99L); }
 @Test void publicationFailureIsPersistedAsRetryableWithoutAReactionSignal(){ stored(SubmissionStore.SubmissionState.RESULT_STORED); when(results.findById(RESULT)).thenReturn(Optional.of(result)); when(results.claimCanonicalPublication(eq(RESULT),any())).thenReturn(true); when(discord.findByPublicationKey(anyLong(),anyString())).thenThrow(new IllegalStateException("Discord unavailable")); assertThat(service.publish(SOURCE)).isFalse(); verify(submissions).markRetryableFailure(eq(SOURCE),anyString()); verify(results).releaseCanonicalPublicationClaim(RESULT); verify(submissions,never()).completeCanonicalPublication(anyLong(),anyLong(),anyLong()); }
 private void stored(SubmissionStore.SubmissionState state){ when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(new SubmissionStore.StoredSubmission(SOURCE,11L,12L,TOBIAS,"share",state,Optional.of(RESULT),List.of(),Optional.empty(),Optional.empty(),Instant.EPOCH,Instant.EPOCH))); }
 private GameResultStore.StoredGameResult result(OptionalLong canonical){ ParsedGameResult parsed=new ParsedGameResult(GameType.GRIDWORDS,LocalDate.of(2026,7,29),new ShareOutcome.Solved(3,6),Duration.ofSeconds(85),OptionalInt.empty(),Optional.of(new NormalizedBoard(List.of("\u2b1c\u2b1c\u2b1c\u2b1c\u2b1c","\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8","\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9")))); return new GameResultStore.StoredGameResult(RESULT,TOBIAS,parsed,"share","v1",canonical,Instant.EPOCH,Instant.EPOCH); }
}
