package de.venomenon.gridwordsbot.application.player;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import de.venomenon.gridwordsbot.port.out.LatestValidSubmissionQuery;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Projects the calling player's participation, reminder preference and latest valid results. */
public final class PersonalStatusService implements PersonalStatusUseCase {
    private final PlayerStore players;
    private final LatestValidSubmissionQuery submissions;
    private final Clock clock;
    private final ZoneId zoneId;
    private final Set<Long> administratorIds;

    public PersonalStatusService(
            PlayerStore players,
            LatestValidSubmissionQuery submissions,
            Clock clock,
            ZoneId zoneId,
            Set<Long> administratorIds) {
        this.players = Objects.requireNonNull(players, "players");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.administratorIds = Set.copyOf(Objects.requireNonNull(administratorIds, "administratorIds"));
    }

    @Override
    public PersonalStatus status(PlayerIdentity actor) {
        Objects.requireNonNull(actor, "actor");
        LocalDate today = clock.instant().atZone(zoneId).toLocalDate();
        PlayerStore.StoredPlayer player = players.synchronizeProfile(new PlayerStore.ProfileUpdate(
                actor.discordUserId(), actor.displayName(), administratorIds.contains(actor.discordUserId())));
        EnumMap<GameType, LatestSubmission> latest = latestByGameType(
                submissions.findLatestValidSubmissions(actor.discordUserId()));

        return new PersonalStatus(
                participation(players.findGameParticipationPeriod(actor.discordUserId(), GameType.GRIDWORDS, today)),
                participation(players.findGameParticipationPeriod(actor.discordUserId(), GameType.QUADWORDS, today)),
                player.reminderOptIn(),
                Optional.ofNullable(latest.get(GameType.GRIDWORDS)),
                Optional.ofNullable(latest.get(GameType.QUADWORDS)));
    }

    private static ParticipationStatus participation(Optional<GameParticipationPeriod> currentPeriod) {
        if (currentPeriod.isEmpty()) {
            return new ParticipationStatus(false, Optional.empty(), Optional.empty());
        }
        GameParticipationPeriod period = currentPeriod.orElseThrow();
        return new ParticipationStatus(
                true,
                Optional.of(period.activeFrom()),
                Optional.ofNullable(period.inactiveFrom()).map(date -> date.minusDays(1)));
    }

    private static EnumMap<GameType, LatestSubmission> latestByGameType(
            List<LatestValidSubmissionQuery.LatestValidSubmission> latestSubmissions) {
        Objects.requireNonNull(latestSubmissions, "latestSubmissions");
        EnumMap<GameType, LatestSubmission> latest = new EnumMap<>(GameType.class);
        for (LatestValidSubmissionQuery.LatestValidSubmission submission : latestSubmissions) {
            Objects.requireNonNull(submission, "latestSubmissions must not contain null");
            LatestSubmission previous = latest.put(submission.gameType(), new LatestSubmission(
                    submission.gameType(), submission.outcome(), submission.duration(), submission.gameDate(),
                    submission.receivedAt()));
            if (previous != null) {
                throw new IllegalStateException("latest submission query returned duplicate game type "
                        + submission.gameType());
            }
        }
        return latest;
    }
}
