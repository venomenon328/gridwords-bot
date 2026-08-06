package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordValue;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Pure German renderer; it deliberately owns no JDA types or external I/O. */
public final class RecordAnnouncementRenderer {
    public static final String VERSION = "records-v1-discord-3";
    private static final int PAGE_DESCRIPTION_LIMIT = 3_800;

    public RenderedRecordAnnouncement render(RecordAnnouncementRenderInput input) {
        Objects.requireNonNull(input, "input");
        List<RecordEventSnapshot> ordered = input.events().stream()
                .sorted(Comparator.<RecordEventSnapshot, String>comparing(event -> event.draft().stateKey().definitionKey().value())
                        .thenComparing(event -> event.draft().eventId()))
                .toList();
        if (ordered.isEmpty()) throw new IllegalArgumentException("record announcement needs valid events");
        String publicationKey = publicationKey(input.registration().key().idempotencyKey());
        List<String> lines = ordered.stream().map(event -> line(event, input.playerDisplays())).toList();
        List<String> bodies = pageBodies(lines);
        List<RenderedRecordAnnouncementPage> pages = new ArrayList<>();
        for (int index = 0; index < bodies.size(); index++) {
            pages.add(new RenderedRecordAnnouncementPage(index, title(input.registration()), bodies.get(index),
                    publicationKey + "|page:" + (index + 1) + "/" + bodies.size()));
        }
        String fingerprint = sha256(pages.stream()
                .map(page -> page.title() + "\n" + page.description() + "\n" + page.footer())
                .reduce("", (left, right) -> left + "\n---\n" + right));
        return new RenderedRecordAnnouncement(publicationKey, fingerprint, pages);
    }

    public static String publicationKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return "record-announcement:" + sha256(idempotencyKey);
    }

    private static String title(de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration registration) {
        return switch (registration.phase()) {
            case LIVE_EVALUATION -> "🏆 Neuer Rekord";
            case STREAK_CROSSED -> "🔥 Serienrekord übertroffen";
            case STREAK_FINISHED -> "🏁 Serienabschluss";
        };
    }

    private static List<String> pageBodies(List<String> lines) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            int addition = current.isEmpty() ? line.length() : line.length() + 2;
            if (!current.isEmpty() && current.length() + addition > PAGE_DESCRIPTION_LIMIT) {
                pages.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(line);
        }
        if (!current.isEmpty()) pages.add(current.toString());
        return pages;
    }

    private static String line(RecordEventSnapshot event, Map<Long, String> displays) {
        RecordEventDraft draft = event.draft();
        String key = draft.stateKey().definitionKey().value();
        String metric = metric(key);
        String scope = scope(draft.stateKey().scope(), displays);
        String value = value(draft.newValue());
        String previous = draft.previousValue().map(RecordAnnouncementRenderer::value).orElse("keine Vergleichsbasis");
        String holder = draft.newHolderPlayerId().map(id -> display(displays, id)).orElse("gemeinsam");
        String previousHolder = draft.previousHolderPlayerId().map(id -> display(displays, id)).orElse("");
        return switch (draft.type()) {
            case RESULT_RECORD_BROKEN -> "**" + resultGame(key) + " · " + metric + " · " + scope + "**\n"
                    + holder + ": " + value + ". Vorher: " + previous + holderSuffix(previousHolder) + ".";
            case SERIES_RECORD_CROSSED -> "**" + metric + " · " + scope + "**\n"
                    + (serverWide(draft) ? "Kandidat " : "") + holder + " übertrifft den bisherigen Rekord: "
                    + value + " statt " + previous + holderSuffix(previousHolder) + ".";
            case RECORD_SERIES_FINISHED -> "**" + metric + " · " + scope + "**\n"
                    + "Abschluss für " + (serverWide(draft) ? "Kandidat " : "") + holder + " bei " + value
                    + "; neuer Rekord gegenüber " + previous + holderSuffix(previousHolder) + ".";
            case SERIES_RECORD_TIED_AT_END -> "**" + metric + " · " + scope + "**\n"
                    + (serverWide(draft) ? "Kandidat " : "") + holder + " stellt den Rekord ein: " + value + "."
                    + holderSuffix(previousHolder);
            case SERIES_RECORD_NEAR_MISSED_AT_END -> "**" + metric + " · " + scope + "**\n"
                    + (serverWide(draft) ? "Kandidat " + holder + " verpasst knapp: " : "Knapp verpasst: ")
                    + value + " statt " + previous + " (Abstand "
                    + distance(draft.newValue(), draft.previousValue().orElseThrow()) + ")"
                    + holderSuffix(previousHolder) + ".";
            default -> throw new IllegalArgumentException("event is not publicly renderable: " + draft.type());
        };
    }

    private static String holderSuffix(String holder) {
        return holder.isBlank() ? "" : " Vorheriger Halter: " + holder;
    }

    private static boolean serverWide(RecordEventDraft draft) {
        return draft.stateKey().scope() instanceof RecordScope.ServerIndividual;
    }

    private static String resultGame(String key) {
        return key.contains("gridwords") ? "GridWords" : key.contains("quadwords") ? "QuadWords" : "Ergebnis";
    }

    private static String scope(RecordScope scope, Map<Long, String> displays) {
        return switch (scope) {
            case RecordScope.Personal personal -> "persönlich für " + display(displays, personal.playerId());
            case RecordScope.ServerIndividual ignored -> "serverweit individuell";
            case RecordScope.Shared ignored -> "gemeinsam";
        };
    }

    private static String metric(String key) {
        String normalized = key.replace('.', ' ').replace('-', ' ');
        if (normalized.contains("fewest attempts")) return "Wenigste Versuche";
        if (normalized.contains("fastest solution")) return "Schnellste Lösung";
        if (normalized.contains("slowest successful solution")) return "Langsamste erfolgreiche Lösung";
        if (normalized.contains("gridwords drought")) return "GridWords-Durststrecke";
        if (normalized.contains("quadwords drought")) return "QuadWords-Durststrecke";
        if (normalized.contains("without perfect day")) return "Serie ohne perfekten Tag";
        if (normalized.contains("gridwords solved")) return "GridWords-Lösungsserie";
        if (normalized.contains("quadwords solved")) return "QuadWords-Lösungsserie";
        if (normalized.contains("activity")) return "Aktivitätsserie";
        if (normalized.contains("complete")) return "Komplettserie";
        if (normalized.contains("perfect")) return "Perfektserie";
        return "Rekord";
    }

    private static String value(RecordValue value) {
        return switch (value) {
            case AttemptsDurationRecordValue attempts -> attempts.attempts() + " Versuche · " + duration(attempts.duration());
            case DurationRecordValue duration -> duration(duration.duration());
            case StreakRecordValue streak -> streak.length() + " Tage (" + streak.startDate() + " bis " + streak.endDate() + ")";
        };
    }

    private static String distance(RecordValue candidate, RecordValue reference) {
        if (candidate instanceof StreakRecordValue current && reference instanceof StreakRecordValue prior) {
            return Math.abs(prior.length() - current.length()) + " Tag" + (Math.abs(prior.length() - current.length()) == 1 ? "" : "e");
        }
        return "nicht anwendbar";
    }

    private static String duration(Duration duration) {
        long seconds = duration.toSeconds();
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static String display(Map<Long, String> displays, long playerId) {
        return neutralize(displays.getOrDefault(playerId, "Ehemaliger Spieler"));
    }

    /** Avoid Discord mention syntax entirely, including user supplied names. */
    static String neutralize(String text) {
        String safe = Objects.requireNonNullElse(text, "Ehemaliger Spieler")
                .replace('@', ' ').replace('<', ' ').replace('>', ' ').replace('&', ' ')
                .replaceAll("\\s+", " ").trim();
        if (safe.isBlank()) return "Ehemaliger Spieler";
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
