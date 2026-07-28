package de.venomenon.gridwordsbot.parser.common;

import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small common header, date, duration and flame parser for the two explicit game parsers. */
public final class ShareHeaderParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("d. MMMM uuuu", Locale.GERMAN)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^(?<game>GridWords|QuadWords)\\h*\\(\\h*(?<date>[^)]*?)\\h*\\)\\h*"
                    + "(?<attempts>\\S+)\\h*/\\h*(?<maximum>\\S+)\\h*in\\h*(?<duration>\\S+)(?<trailing>.*)$");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(?<minutes>\\d+):(?<seconds>\\d{2})$");
    private static final Pattern STREAK_PATTERN = Pattern.compile("^🔥\\uFE0F?\\h*(?<value>\\d+)\\h*$", Pattern.UNICODE_CASE);

    private ShareHeaderParser() {
    }

    public static HeaderSearch findHeader(String content, String gameName) {
        String[] lines = content.split("\\R", -1);
        List<HeaderLine> matches = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            if (line.startsWith(gameName)) {
                matches.add(new HeaderLine(index, line));
            }
        }
        return new HeaderSearch(lines, matches);
    }

    public static HeaderParse parse(HeaderLine headerLine, int expectedMaximum) {
        Matcher matcher = HEADER_PATTERN.matcher(headerLine.content());
        if (!matcher.matches()) {
            return HeaderParse.failure(ParseErrorCode.MALFORMED_HEADER, "The share header is malformed.");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(matcher.group("date"), DATE_FORMATTER);
        } catch (DateTimeException exception) {
            return HeaderParse.failure(ParseErrorCode.INVALID_DATE, "The game date is invalid.");
        }

        Optional<ShareOutcome> outcome = parseOutcome(
                matcher.group("attempts"), matcher.group("maximum"), expectedMaximum);
        if (outcome.isEmpty()) {
            return HeaderParse.failure(ParseErrorCode.INVALID_ATTEMPT_RESULT, "The attempt result is invalid.");
        }

        Optional<Duration> duration = parseDuration(matcher.group("duration"));
        if (duration.isEmpty()) {
            return HeaderParse.failure(ParseErrorCode.INVALID_DURATION, "The duration is invalid.");
        }

        String trailing = matcher.group("trailing").strip();
        OptionalInt streak = OptionalInt.empty();
        if (!trailing.isEmpty()) {
            Matcher streakMatcher = STREAK_PATTERN.matcher(trailing);
            if (!streakMatcher.matches()) {
                ParseErrorCode code = trailing.startsWith("🔥")
                        ? ParseErrorCode.INVALID_STREAK
                        : ParseErrorCode.MALFORMED_HEADER;
                String description = code == ParseErrorCode.INVALID_STREAK
                        ? "The Gridgames streak is invalid."
                        : "The share header contains unsupported trailing content.";
                return HeaderParse.failure(code, description);
            }
            try {
                int streakValue = Integer.parseInt(streakMatcher.group("value"));
                if (streakValue <= 0) {
                    return HeaderParse.failure(ParseErrorCode.INVALID_STREAK, "The Gridgames streak must be positive.");
                }
                streak = OptionalInt.of(streakValue);
            } catch (NumberFormatException exception) {
                return HeaderParse.failure(ParseErrorCode.INVALID_STREAK, "The Gridgames streak is invalid.");
            }
        }

        return HeaderParse.success(new ParsedHeader(date, outcome.orElseThrow(), duration.orElseThrow(), streak));
    }

    private static Optional<ShareOutcome> parseOutcome(String attemptsToken, String maximumToken, int expectedMaximum) {
        int maximum;
        try {
            maximum = Integer.parseInt(maximumToken);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        if (maximum != expectedMaximum) {
            return Optional.empty();
        }
        if (attemptsToken.equals("X")) {
            return Optional.of(new ShareOutcome.Unsolved(maximum));
        }
        try {
            return Optional.of(new ShareOutcome.Solved(Integer.parseInt(attemptsToken), maximum));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> parseDuration(String token) {
        Matcher matcher = DURATION_PATTERN.matcher(token);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            long minutes = Long.parseLong(matcher.group("minutes"));
            int seconds = Integer.parseInt(matcher.group("seconds"));
            if (seconds > 59) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofMinutes(minutes).plusSeconds(seconds));
        } catch (NumberFormatException | ArithmeticException exception) {
            return Optional.empty();
        }
    }

    public record HeaderSearch(String[] lines, List<HeaderLine> matches) {

        public HeaderSearch {
            lines = lines.clone();
            matches = List.copyOf(matches);
        }
    }

    public record HeaderLine(int index, String content) {
    }

    public record ParsedHeader(LocalDate date, ShareOutcome outcome, Duration duration, OptionalInt streak) {
    }

    public record HeaderParse(ParsedHeader header, ParseErrorCode errorCode, String description) {

        private static HeaderParse success(ParsedHeader header) {
            return new HeaderParse(header, null, null);
        }

        private static HeaderParse failure(ParseErrorCode errorCode, String description) {
            return new HeaderParse(null, errorCode, description);
        }

        public boolean isSuccess() {
            return header != null;
        }
    }
}
