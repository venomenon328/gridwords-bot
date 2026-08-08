package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Maintains the one pending historical-introduction projection for a participant. */
final class AchievementHistoricalIntroductionProjector {
    private static final String RENDERER_VERSION = "achievement-historical-introduction-v1";

    private final AchievementDefinitionCatalog catalog;
    private final AchievementAwardStateStore awardStates;
    private final AchievementEventStore events;
    private final AchievementAnnouncementStore announcements;
    private final Clock clock;

    AchievementHistoricalIntroductionProjector(
            AchievementDefinitionCatalog catalog,
            AchievementAwardStateStore awardStates,
            AchievementEventStore events,
            AchievementAnnouncementStore announcements,
            Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.awardStates = Objects.requireNonNull(awardStates, "awardStates");
        this.events = Objects.requireNonNull(events, "events");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void refresh(long guildId, long channelId, long participantId) {
        if (guildId <= 0 || channelId <= 0 || participantId <= 0) {
            throw new IllegalArgumentException("guildId, channelId and participantId must be positive");
        }
        List<UUID> eventIds = activeEventIds(guildId, participantId);
        String idempotencyKey = "achievement-historical-introduction:v1:" + sha256(
                guildId + "|" + participantId + "|" + catalog.version().value() + "|historical-introduction");
        AchievementAnnouncement.Key key = new AchievementAnnouncement.Key(guildId, idempotencyKey);
        String fingerprint = fingerprint(guildId, channelId, participantId, idempotencyKey, eventIds);

        if (announcements.find(key).isEmpty()) {
            announcements.register(new AchievementAnnouncement.Registration(
                    guildId,
                    channelId,
                    participantId,
                    catalog.version(),
                    AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION,
                    idempotencyKey,
                    RENDERER_VERSION,
                    fingerprint));
        }
        if (!announcements.replaceItems(key, eventIds)) {
            throw new IllegalStateException("historical achievement introduction is no longer pending");
        }
        if (!announcements.updatePendingContent(key, RENDERER_VERSION, fingerprint)) {
            throw new IllegalStateException("historical achievement introduction cannot be refreshed");
        }
    }

    private List<UUID> activeEventIds(long guildId, long participantId) {
        Map<AchievementKey, AchievementAwardState.Snapshot> active = new HashMap<>();
        for (AchievementAwardState.Snapshot state : awardStates.findAll(guildId, participantId)) {
            if (state.write().status() == AchievementAwardState.Status.ACTIVE) {
                active.put(state.key().achievementKey(), state);
            }
        }
        Map<AchievementKey, UUID> latestActivation = new HashMap<>();
        for (AchievementEventFact.Snapshot event : events.findByParticipant(guildId, participantId)) {
            if (event.fact().eventType() == AchievementEventFact.Type.UNLOCKED
                    || event.fact().eventType() == AchievementEventFact.Type.REACTIVATED) {
                latestActivation.put(event.fact().awardKey().achievementKey(), event.fact().eventId());
            }
        }
        return catalog.definitions().stream()
                .map(AchievementDefinition::key)
                .filter(active::containsKey)
                .map(key -> java.util.Optional.ofNullable(latestActivation.get(key)).orElseThrow(
                        () -> new IllegalStateException("active achievement has no activation event: " + key.value())))
                .toList();
    }

    private String fingerprint(
            long guildId, long channelId, long participantId, String idempotencyKey, List<UUID> eventIds) {
        StringBuilder canonical = new StringBuilder(RENDERER_VERSION)
                .append('|').append(guildId)
                .append('|').append(channelId)
                .append('|').append(participantId)
                .append('|').append(catalog.version().value())
                .append('|').append(idempotencyKey);
        for (UUID eventId : eventIds) {
            AchievementEventFact.Snapshot event = events.find(eventId)
                    .orElseThrow(() -> new IllegalStateException("achievement event disappeared during introduction update"));
            AchievementDefinition definition = catalog.find(event.fact().awardKey().achievementKey())
                    .orElseThrow(() -> new IllegalStateException("achievement event uses an unknown catalog key"));
            canonical.append('|').append(eventId)
                    .append('|').append(definition.key().value())
                    .append('|').append(definition.displayName())
                    .append('|').append(definition.description());
        }
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
