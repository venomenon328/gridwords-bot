package de.venomenon.gridwordsbot.domain.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Complete, JDA-free delivery view for a daily status and its result selectors. */
public record DailyStatusView(DailyStatus status, int componentVersion, List<DailyResultMenuPage> resultMenuPages) {
    public static final int OPTIONS_PER_PAGE = 25;
    public static final int MAX_PAGES_PER_GAME = 2;

    public DailyStatusView {
        Objects.requireNonNull(status, "status");
        if (componentVersion <= 0) throw new IllegalArgumentException("componentVersion must be positive");
        resultMenuPages = List.copyOf(Objects.requireNonNull(resultMenuPages, "resultMenuPages"));
    }

    public static DailyStatusView versionOne(DailyStatus status) {
        List<DailyResultMenuPage> pages = new ArrayList<>();
        for (GameType gameType : List.of(GameType.GRIDWORDS, GameType.QUADWORDS)) {
            pages.addAll(resultMenuPages(gameType, status.players().stream()
                    .filter(player -> player.participates(gameType))
                    .map(player -> new PlayerOption(player.discordUserId(), player.displayName()))
                    .toList()));
        }
        return new DailyStatusView(status, 1, pages);
    }

    /** Shared v1 sorting and pagination rule for rendered menu pages and interaction validation. */
    public static List<DailyResultMenuPage> resultMenuPages(GameType gameType, Collection<PlayerOption> players) {
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(players, "players");
        List<PlayerOption> options = players.stream()
                .map(player -> Objects.requireNonNull(player, "players must not contain null"))
                .sorted(Comparator.comparing((PlayerOption option) -> option.displayName().toLowerCase(Locale.ROOT))
                        .thenComparingLong(PlayerOption::discordUserId))
                .toList();
        if (options.isEmpty()) {
            return List.of();
        }
        if (options.stream().map(PlayerOption::discordUserId).distinct().count() != options.size()) {
            throw new IllegalArgumentException("a game menu cannot contain a player more than once");
        }
        int pageCount = (options.size() + OPTIONS_PER_PAGE - 1) / OPTIONS_PER_PAGE;
        List<DailyResultMenuPage> pages = new ArrayList<>();
        for (int page = 0; page < pageCount; page++) {
            int from = page * OPTIONS_PER_PAGE;
            int to = Math.min(options.size(), from + OPTIONS_PER_PAGE);
            pages.add(new DailyResultMenuPage(gameType, page, pageCount, options.subList(from, to)));
        }
        return List.copyOf(pages);
    }

    public record DailyResultMenuPage(GameType gameType, int pageIndex, int pageCount, List<PlayerOption> options) {
        public DailyResultMenuPage {
            Objects.requireNonNull(gameType, "gameType");
            if (pageIndex < 0 || pageCount <= 0 || pageIndex >= pageCount) throw new IllegalArgumentException("invalid page");
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            if (options.isEmpty()) throw new IllegalArgumentException("a result menu page must not be empty");
            if (options.size() > OPTIONS_PER_PAGE) throw new IllegalArgumentException("too many menu options");
        }
    }

    public record PlayerOption(long discordUserId, String displayName) {
        public PlayerOption {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}