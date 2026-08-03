package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Strict parser for reconstructable v1 status components. */
public final class DailyResultComponentCodec {
    public Optional<Component> decode(String id) {
        String[] parts = id.split(":", -1);
        if (parts.length != 5 || !parts[0].equals("daily-result") || !parts[1].equals("v1")) return Optional.empty();
        try {
            int page = Integer.parseInt(parts[4]);
            if (page < 0) return Optional.empty();
            GameType type = switch (parts[3]) { case "g" -> GameType.GRIDWORDS; case "q" -> GameType.QUADWORDS; default -> null; };
            return type == null ? Optional.empty() : Optional.of(new Component(LocalDate.parse(parts[2]), type, page));
        } catch (DateTimeParseException | NumberFormatException exception) { return Optional.empty(); }
    }
    public Optional<Long> target(String value) {
        if (!value.startsWith("user:")) return Optional.empty();
        try { long id = Long.parseLong(value.substring(5)); return id > 0 ? Optional.of(id) : Optional.empty(); }
        catch (NumberFormatException exception) { return Optional.empty(); }
    }
    public record Component(LocalDate gameDate, GameType gameType, int pageIndex) { }
}