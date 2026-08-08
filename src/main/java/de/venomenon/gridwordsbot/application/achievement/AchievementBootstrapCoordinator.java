package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Claim-, lease- and restart-safe historical reconstruction for one Guild and catalog version. */
public final class AchievementBootstrapCoordinator {
    private final AchievementBootstrapStore bootstraps;
    private final AchievementTransactionRunner transactions;
    private final PlayerStore players;
    private final AchievementResultLifecycle lifecycle;
    private final AchievementDefinitionCatalog catalog;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration retryBackoff;

    public AchievementBootstrapCoordinator(
            AchievementBootstrapStore bootstraps,
            AchievementTransactionRunner transactions,
            PlayerStore players,
            AchievementResultLifecycle lifecycle,
            AchievementDefinitionCatalog catalog,
            Clock clock,
            Duration leaseDuration,
            Duration retryBackoff) {
        this.bootstraps = Objects.requireNonNull(bootstraps, "bootstraps");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.players = Objects.requireNonNull(players, "players");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = requirePositive(leaseDuration, "leaseDuration");
        this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
    }

    public BootstrapRunResult run(long guildId, long channelId) {
        if (guildId <= 0 || channelId <= 0) {
            throw new IllegalArgumentException("guildId and channelId must be positive");
        }
        AchievementWork.BootstrapKey key = new AchievementWork.BootstrapKey(guildId, catalog.version());
        bootstraps.register(key);
        AchievementWork.LeaseClaim claim = bootstraps.claim(key, leaseRequest()).orElse(null);
        if (claim == null) {
            return BootstrapRunResult.NOT_CLAIMED;
        }
        try {
            for (PlayerStore.StoredPlayer participant : players.findAllPlayers()) {
                if (!renew(key, claim.token())) {
                    return BootstrapRunResult.LOST_LEASE;
                }
                boolean completed = transactions.inBootstrapFenceTransaction(key, () -> {
                    if (!ownsLiveClaim(key, claim.token())) {
                        return false;
                    }
                    lifecycle.reconcileBootstrapParticipant(guildId, channelId, participant.discordUserId());
                    return true;
                });
                if (!completed) {
                    return BootstrapRunResult.LOST_LEASE;
                }
            }
            boolean succeeded = transactions.inBootstrapFenceTransaction(key,
                    () -> bootstraps.markSucceeded(key, claim.token(), clock.instant()));
            return succeeded ? BootstrapRunResult.SUCCEEDED : BootstrapRunResult.LOST_LEASE;
        } catch (RuntimeException exception) {
            // The category deliberately remains UNKNOWN: no arbitrary infrastructure failure is relabelled as a
            // business conflict or a permanent bootstrap result.
            bootstraps.markRetryableFailure(
                    key,
                    claim.token(),
                    new AchievementWork.Failure(AchievementWork.FailureCategory.UNKNOWN,
                            "achievement bootstrap technical failure"),
                    clock.instant().plus(retryBackoff));
            throw exception;
        }
    }

    private boolean ownsLiveClaim(AchievementWork.BootstrapKey key, UUID token) {
        AchievementWork.BootstrapSnapshot snapshot = bootstraps.find(key)
                .orElseThrow(() -> new IllegalStateException("claimed achievement bootstrap disappeared"));
        return snapshot.state() == AchievementWork.State.CLAIMED
                && snapshot.claimToken().filter(token::equals).isPresent()
                && snapshot.claimUntil().filter(until -> until.isAfter(clock.instant())).isPresent();
    }

    private boolean renew(AchievementWork.BootstrapKey key, UUID token) {
        return bootstraps.renewLease(key, token, leaseRequest());
    }

    private AchievementWork.LeaseClaimRequest leaseRequest() {
        Instant now = clock.instant();
        return new AchievementWork.LeaseClaimRequest(now, now.plus(leaseDuration));
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public enum BootstrapRunResult { SUCCEEDED, NOT_CLAIMED, LOST_LEASE }
}
