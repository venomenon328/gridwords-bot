package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.streak.StreakCalculator;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalInt;

/** Publishes one GridWords result outside database transactions and resumes it safely. */
public final class CanonicalGridWordsPublicationService {
    private final GameResultStore results; private final PlayerStore players; private final SubmissionStore submissions;
    private final CanonicalMessageGateway discord; private final Clock clock; private final ZoneId zoneId; private final StreakCalculator streaks = new StreakCalculator();
    public CanonicalGridWordsPublicationService(GameResultStore results, PlayerStore players, SubmissionStore submissions, CanonicalMessageGateway discord, Clock clock, ZoneId zoneId) { this.results=results;this.players=players;this.submissions=submissions;this.discord=discord;this.clock=clock;this.zoneId=zoneId; }
    public boolean publish(long sourceMessageId) {
        SubmissionStore.StoredSubmission submission=submissions.findBySourceMessageId(sourceMessageId).orElseThrow();
        if (submission.state()==SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED) return true;
        long resultId=submission.gameResultId().orElseThrow(); GameResultStore.StoredGameResult result=results.findById(resultId).orElseThrow();
        if (result.parsedResult().gameType()!=GameType.GRIDWORDS) return true;
        CanonicalResultMessage message=message(result,submission.authorPlayerId());
        try {
            long canonicalId;
            if(result.canonicalMessageId().isPresent()) {
                canonicalId=result.canonicalMessageId().getAsLong();
                try { discord.edit(submission.channelId(),canonicalId,message); }
                catch(CanonicalMessageGateway.UnknownMessageException missing) { canonicalId=replaceMissing(submission,result,message); if(canonicalId==0)return false; }
            } else { canonicalId=createOnce(submission,result,message); if(canonicalId==0)return false; }
            return submissions.completeCanonicalPublication(sourceMessageId,resultId,canonicalId);
        } catch (CanonicalMessageGateway.UnknownMessageException e) { submissions.markRetryableFailure(sourceMessageId,"canonical message lookup failed"); return false;
        } catch (RuntimeException e) { submissions.markRetryableFailure(sourceMessageId,"canonical Discord publication failed"); return false; }
    }
    public void resumeOpenPublications() { for (SubmissionStore.StoredSubmission submission:submissions.findGridWordsAwaitingCanonicalPublication()) publish(submission.sourceMessageId()); }
    private long createOnce(SubmissionStore.StoredSubmission submission,GameResultStore.StoredGameResult result,CanonicalResultMessage message) {
        if(!results.claimCanonicalPublication(result.id(),clock.instant().plusSeconds(60))) return 0;
        try { return discord.findByPublicationKey(submission.channelId(),message.publicationKey()).orElseGet(() -> discord.create(submission.channelId(),message)); }
        finally { results.releaseCanonicalPublicationClaim(result.id()); }
    }
    private long replaceMissing(SubmissionStore.StoredSubmission submission,GameResultStore.StoredGameResult result,CanonicalResultMessage message) { return createOnce(submission,result,message); }
    private CanonicalResultMessage message(GameResultStore.StoredGameResult result,long playerId) {
        List<GameResultStore.StoredGameResult> all=results.findAll(); List<Long> active=players.findActivePlayers().stream().map(PlayerStore.StoredPlayer::discordUserId).toList();
        LocalDate today=clock.instant().atZone(zoneId).toLocalDate(); StreakSummary summary=streaks.calculate(all.stream().map(r->new StreakCalculator.PlayerResult(r.playerId(),r.parsedResult())).toList(),active,playerId,today);
        var parsed=result.parsedResult(); boolean complete=all.stream().filter(r->r.playerId()==playerId&&r.parsedResult().gameDate().equals(parsed.gameDate())).map(r->r.parsedResult().gameType()).distinct().count()==2;
        boolean perfect=complete&&all.stream().filter(r->r.playerId()==playerId&&r.parsedResult().gameDate().equals(parsed.gameDate())).allMatch(r->r.parsedResult().outcome() instanceof ShareOutcome.Solved);
        boolean sharedComplete=active.stream().allMatch(player -> all.stream().filter(r -> r.playerId()==player && r.parsedResult().gameDate().equals(parsed.gameDate())).map(r -> r.parsedResult().gameType()).distinct().count()==2); boolean sharedPerfect=sharedComplete && active.stream().allMatch(player -> all.stream().filter(r -> r.playerId()==player && r.parsedResult().gameDate().equals(parsed.gameDate())).allMatch(r -> r.parsedResult().outcome() instanceof ShareOutcome.Solved)); return new CanonicalResultMessage(players.findByDiscordUserId(playerId).orElseThrow().displayName(),GameType.GRIDWORDS,parsed.gameDate(),parsed.outcome(),parsed.duration(),parsed.board().orElseThrow(),summary,complete?OptionalInt.of(summary.personalComplete()):OptionalInt.empty(),perfect?OptionalInt.of(summary.personalPerfect()):OptionalInt.empty(),sharedComplete?OptionalInt.of(summary.sharedComplete()):OptionalInt.empty(),sharedPerfect?OptionalInt.of(summary.sharedPerfect()):OptionalInt.empty(),"gridwords-result-"+result.id());
    }
}
