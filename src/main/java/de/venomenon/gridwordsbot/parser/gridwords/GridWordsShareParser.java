package de.venomenon.gridwordsbot.parser.gridwords;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.common.ShareHeaderParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministically parses a GridWords header and its Unicode board. */
public final class GridWordsShareParser {

    private static final String GAME_NAME = "GridWords";
    private static final String WHITE = "⬜";

    public ParseResult parse(ShareParseInput input) {
        ShareHeaderParser.HeaderSearch search = ShareHeaderParser.findHeader(input.content(), GAME_NAME);
        if (search.matches().isEmpty()) {
            return new ParseResult.NotApplicable();
        }
        if (search.matches().size() > 1) {
            return invalid(ParseErrorCode.MALFORMED_HEADER, "The message contains multiple GridWords headers.");
        }

        ShareHeaderParser.HeaderLine headerLine = search.matches().getFirst();
        ShareHeaderParser.HeaderParse headerParse = ShareHeaderParser.parse(headerLine, 6);
        if (!headerParse.isSuccess()) {
            return invalid(headerParse.errorCode(), headerParse.description());
        }

        List<String> rows = new ArrayList<>();
        for (int index = 0; index < search.lines().length; index++) {
            if (index == headerLine.index()) {
                continue;
            }
            String line = search.lines()[index].strip();
            if (line.isEmpty() || isHttpLink(line)) {
                continue;
            }
            Optional<String> normalizedRow = normalizeRow(line);
            if (normalizedRow.isEmpty()) {
                return invalid(ParseErrorCode.INVALID_BOARD, "The GridWords board contains an invalid row.");
            }
            rows.add(normalizedRow.orElseThrow());
        }

        if (rows.isEmpty()) {
            return invalid(ParseErrorCode.MISSING_BOARD, "The GridWords board is missing.");
        }
        int expectedRows = headerParse.header().outcome() instanceof ShareOutcome.Solved solved
                ? solved.attemptsUsed()
                : 6;
        if (rows.size() != expectedRows) {
            return invalid(ParseErrorCode.INVALID_BOARD, "The GridWords board has an invalid number of rows.");
        }

        try {
            NormalizedBoard board = new NormalizedBoard(rows);
            return new ParseResult.Parsed(new ParsedGameResult(
                    GameType.GRIDWORDS,
                    headerParse.header().date(),
                    headerParse.header().outcome(),
                    headerParse.header().duration(),
                    headerParse.header().streak(),
                    Optional.of(board)));
        } catch (IllegalArgumentException exception) {
            return invalid(ParseErrorCode.INVALID_BOARD, "The GridWords board is invalid.");
        }
    }

    private Optional<String> normalizeRow(String line) {
        String withoutSpacingOrSelectors = line.replaceAll("[\\h\\uFE0E\\uFE0F]", "");
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < withoutSpacingOrSelectors.length();) {
            int codePoint = withoutSpacingOrSelectors.codePointAt(index);
            switch (codePoint) {
                case 0x2B1C, 0x2B1B -> normalized.append(WHITE);
                case 0x1F7E8 -> normalized.append("🟨");
                case 0x1F7E9 -> normalized.append("🟩");
                default -> {
                    return Optional.empty();
                }
            }
            index += Character.charCount(codePoint);
        }
        return normalized.codePointCount(0, normalized.length()) == 5
                ? Optional.of(normalized.toString())
                : Optional.empty();
    }

    private boolean isHttpLink(String line) {
        return line.matches("https?://\\S+");
    }

    private ParseResult.Invalid invalid(ParseErrorCode errorCode, String description) {
        return new ParseResult.Invalid(errorCode, description);
    }
}
