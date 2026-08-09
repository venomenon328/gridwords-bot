package de.venomenon.gridwordsbot.application.player;

import de.venomenon.gridwordsbot.application.status.DailyStatusProjector;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
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
import java.util.OptionalInt;

/** Projects the calling player's current dashboard without mutating profile or participation state. */
public final class PersonalStatusService implements PersonalStatusUseCase {
    private final PlayerStore players;
    private final LatestValidSubmissionQuery submissions;
    private final DailyStatusProjector dailyStatus;
    private final Clock clock;
    private final ZoneId zoneId;

    public PersonalStatusService(
            PlayerStore players,
            LatestValidSubmissionQuery submissions,
            DailyStatusProjector dailyStatus,
            Clock clock,
            ZoneId zoneId) {
        this.players = Objects.requireNonNull(players, "players");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.dailyStatus = Objects.requireNonNull(dailyStatus, "dailyStatus");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
    }

    @Override
    public PersonalStatus status(PlayerIdentity actor) {
        Objects.requireNonNull(actor, "actor");
        LocalDate today = clock.instant().atZone(zoneId).toLocalDate();
        PlayerStore.StoredPlayer player = players.findAllPlayers().stream()
                .filter(candidate -> candidate.discordUserId() == actor.discordUserId())
                .findFirst()
                .orElse(null);
        if (player == null) {
            return PersonalStatus.unknown();
        }

        ParticipationStatus gridWordsParticipation = participation(
                players.findGameParticipationPeriod(actor.discordUserId(), GameType.GRIDWORDS, today));
        ParticipationStatus quadWordsParticipation = participation(
                players.findGameParticipationPeriod(actor.discordUserId(), GameType.QUADWORDS, today));
        DailyStatus projectedToday = dailyStatus.project(today, today);
        Optional<DailyStatus.PlayerLine> line = projectedToday.players().stream()
                .filter(candidate -> candidate.discordUserId() == actor.discordUserId())
                .findFirst();
        boolean participatesToday = gridWordsParticipation.active() || quadWordsParticipation.active();
        if (participatesToday && line.isEmpty()) {
            throw new IllegalStateException("daily status is missing a participating player");
        }

        EnumMap<GameType, LatestSubmission> latest = latestByGameType(
                submissions.findLatestValidSubmissions(actor.discordUserId()));
        return new PersonalStatus(
                true,
                today(GameType.GRIDWORDS, gridWordsParticipation, line),
                today(GameType.QUADWORDS, quadWordsParticipation, line),
                streaks(gridWordsParticipation.active(), quadWordsParticipation.active(), line),
                gridWordsParticipation,
                quadWordsParticipation,
                player.reminderOptIn(),
                Optional.ofNullable(latest.get(GameType.GRIDWORDS)),
                Optional.ofNullable(latest.get(GameType.QUADWORDS)));
    }

    private static TodayGameStatus today(
            GameType gameType,
            ParticipationStatus participation,
            Optional<DailyStatus.PlayerLine> line) {
        if (!participation.active()) {
            return TodayGameStatus.notParticipating(gameType);
        }
        Optional<de.venomenon.gridwordsbot.domain.model.ParsedGameResult> result = line.orElseThrow().result(gameType);
        return result
                .map(parsed -> TodayGameStatus.submitted(gameType, parsed.outcome(), parsed.duration()))
                .orElseGet(() -> TodayGameStatus.open(gameType));
    }

    private static PersonalStreaks streaks(
            boolean gridWordsActive,
            boolean quadWordsActive,
            Optional<DailyStatus.PlayerLine> line) {
        if (!gridWordsActive && !quadWordsActive) {
            return PersonalStreaks.none();
        }
        StreakSummary summary = line.orElseThrow().streaks();
        boolean both = gridWordsActive && quadWordsActive;
        return new PersonalStreaks(
                OptionalInt.of(summary.personalActivity()),
                both ? OptionalInt.of(summary.personalComplete()) : OptionalInt.empty(),
                gridWordsActive ? OptionalInt.of(summary.personalGridWordsSolved()) : OptionalInt.empty(),
                quadWordsActive ? OptionalInt.of(summary.personalQuadWordsSolved()) : OptionalInt.empty(),
                both ? OptionalInt.of(summary.personalPerfect()) : OptionalInt.empty());
    }

    private static ParticipationStatus participation(Optional<GameParticipationPeriod> currentPeriod) {
        if (currentPeriod.isEmpty()) {
            return ParticipationStatus.inactive();
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
