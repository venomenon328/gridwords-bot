package de.venomenon.gridwordsbot.adapter.discord.record;

import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordValue;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** JDA-only presentation of the transport-neutral current-record read model. */
public final class RecordsOverviewEmbedRenderer {
    private static final int PAGE_LIMIT = 3_800;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public List<MessageEmbed> render(RecordsQueryUseCase.Result result, String personalDisplay) {
        Objects.requireNonNull(result, "result");
        String safePersonalDisplay = neutralize(personalDisplay);
        if (result instanceof RecordsQueryUseCase.Forbidden) {
            return List.of(new EmbedBuilder()
                    .setTitle("📚 Rekorde")
                    .setDescription("Die persönlichen Rekorde anderer Nutzer können nur Administratoren anzeigen.")
                    .build());
        }
        if (result instanceof RecordsQueryUseCase.Unavailable) {
            return List.of(new EmbedBuilder()
                    .setTitle("📚 Rekorde")
                    .setDescription("Die Rekorde sind noch nicht vollständig initialisiert. Bitte versuche es später erneut.")
                    .build());
        }
        RecordsQueryUseCase.Ready ready = (RecordsQueryUseCase.Ready) result;
        if (ready.entries().isEmpty()) {
            return List.of(new EmbedBuilder()
                    .setTitle("📚 Rekorde")
                    .setDescription("Keine Rekorde passen zu der gewählten Sicht.")
                    .build());
        }
        List<String> lines = sectionedEntries(ready.entries());
        List<String> bodies = pageBodies(lines);
        List<MessageEmbed> pages = new ArrayList<>();
        for (int index = 0; index < bodies.size(); index++) {
            String title = bodies.size() == 1 ? "📚 Rekorde" : "📚 Rekorde · Seite " + (index + 1) + "/" + bodies.size();
            pages.add(new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(bodies.get(index))
                    .setFooter("Persönliche Rekorde: " + safePersonalDisplay)
                    .build());
        }
        return List.copyOf(pages);
    }

    private List<String> sectionedEntries(List<RecordsQueryUseCase.Entry> entries) {
        List<String> lines = new ArrayList<>();
        RecordsQueryUseCase.Category previous = null;
        for (RecordsQueryUseCase.Entry current : entries) {
            String rendered = entry(current);
            if (current.category() != previous) {
                rendered = section(current.category()) + "\n\n" + rendered;
                previous = current.category();
            }
            lines.add(rendered);
        }
        return List.copyOf(lines);
    }

    private static String section(RecordsQueryUseCase.Category category) {
        return switch (category) {
            case RESULTS -> "__Ergebnisrekorde__";
            case SERIES -> "__Serienrekorde__";
        };
    }

    private String entry(RecordsQueryUseCase.Entry entry) {
        String heading = "**" + name(entry) + " · " + scope(entry.scope()) + "**";
        if (entry.value().isEmpty()) return heading + "\nNoch kein Rekord.";
        StringBuilder details = new StringBuilder(value(entry.value().orElseThrow()));
        switch (entry.scope()) {
            case PERSONAL, SERVER_INDIVIDUAL -> details.append(" · Halter: ")
                    .append(neutralize(entry.holderDisplay().orElse("Ehemaliger Spieler")));
            case SHARED -> details.append(" · Halter: gemeinsam");
        }
        details.append(" · ").append(source(entry.source().orElseThrow(), entry.value().orElseThrow()));
        if (entry.running()) details.append(" · läuft");
        return heading + "\n" + details;
    }

    private static String name(RecordsQueryUseCase.Entry entry) {
        String metric = switch (entry.metricSlug()) {
            case "fewest-attempts" -> "Wenigste Versuche";
            case "fastest-solution" -> "Schnellste Lösung";
            case "slowest-successful-solution" -> "Langsamste erfolgreiche Lösung";
            case "activity" -> "Aktivitätsserie";
            case "complete" -> "Komplettserie";
            case "gridwords-solved" -> "GridWords-Lösungsserie";
            case "quadwords-solved" -> "QuadWords-Lösungsserie";
            case "perfect" -> "Perfektserie";
            case "gridwords-drought" -> "GridWords-Durststrecke";
            case "quadwords-drought" -> "QuadWords-Durststrecke";
            case "without-perfect-day" -> "Serie ohne perfekten Tag";
            default -> entry.definitionKey();
        };
        if (entry.category() == RecordsQueryUseCase.Category.RESULTS && entry.game().isPresent()) {
            return game(entry.game().orElseThrow()) + " · " + metric;
        }
        return metric;
    }

    private static String game(de.venomenon.gridwordsbot.domain.model.GameType game) {
        return switch (game) {
            case GRIDWORDS -> "GridWords";
            case QUADWORDS -> "QuadWords";
        };
    }

    private static String scope(RecordsQueryUseCase.Scope scope) {
        return switch (scope) {
            case PERSONAL -> "persönlich";
            case SERVER_INDIVIDUAL -> "serverweit individuell";
            case SHARED -> "gemeinsam";
        };
    }

    private static String value(RecordValue value) {
        return switch (value) {
            case AttemptsDurationRecordValue attempts -> attempts.attempts() + " Versuche · " + duration(attempts.duration());
            case DurationRecordValue duration -> duration(duration.duration());
            case StreakRecordValue streak -> streak.length() + " Tage";
        };
    }

    private static String source(RecordSourceReference source, RecordValue value) {
        return switch (source) {
            case RecordSourceReference.GameResult result -> "Spieltag " + DATE.format(result.gameDate());
            case RecordSourceReference.StreakRun ignored -> {
                StreakRecordValue streak = (StreakRecordValue) value;
                yield DATE.format(streak.startDate()) + "–" + DATE.format(streak.endDate());
            }
        };
    }

    private static String duration(Duration duration) {
        long seconds = duration.toSeconds();
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static List<String> pageBodies(List<String> entries) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String entry : entries) {
            int addition = current.isEmpty() ? entry.length() : entry.length() + 2;
            if (!current.isEmpty() && current.length() + addition > PAGE_LIMIT) {
                pages.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(entry);
        }
        if (!current.isEmpty()) pages.add(current.toString());
        return pages;
    }

    static String neutralize(String text) {
        String safe = Objects.requireNonNullElse(text, "Ehemaliger Spieler")
                .replace('@', ' ').replace('<', ' ').replace('>', ' ').replace('&', ' ')
                .replaceAll("\\s+", " ").trim();
        if (safe.isBlank()) return "Ehemaliger Spieler";
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }
}
