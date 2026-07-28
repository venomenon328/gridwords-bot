package de.venomenon.gridwordsbot.parser.quadwords;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.common.ShareHeaderParser;
import java.util.Optional;

/** Deterministically parses the QuadWords header and checks only image metadata in version 1. */
public final class QuadWordsShareParser {

    private static final String GAME_NAME = "QuadWords";

    public ParseResult parse(ShareParseInput input) {
        ShareHeaderParser.HeaderSearch search = ShareHeaderParser.findHeader(input.content(), GAME_NAME);
        if (search.matches().isEmpty()) {
            return new ParseResult.NotApplicable();
        }
        if (search.matches().size() > 1) {
            return invalid(ParseErrorCode.MALFORMED_HEADER, "The message contains multiple QuadWords headers.");
        }

        ShareHeaderParser.HeaderLine headerLine = search.matches().getFirst();
        ShareHeaderParser.HeaderParse headerParse = ShareHeaderParser.parse(headerLine, 9);
        if (!headerParse.isSuccess()) {
            return invalid(headerParse.errorCode(), headerParse.description());
        }
        for (int index = 0; index < search.lines().length; index++) {
            if (index != headerLine.index()) {
                String line = search.lines()[index].strip();
                if (!line.isEmpty() && !line.matches("https?://\\S+")) {
                    return invalid(ParseErrorCode.MALFORMED_HEADER, "The QuadWords share contains unsupported text.");
                }
            }
        }
        if (input.attachments().stream().noneMatch(attachment -> attachment.isPlausibleImage())) {
            return invalid(ParseErrorCode.MISSING_IMAGE_ATTACHMENT, "A plausible QuadWords image attachment is missing.");
        }

        return new ParseResult.Parsed(new ParsedGameResult(
                GameType.QUADWORDS,
                headerParse.header().date(),
                headerParse.header().outcome(),
                headerParse.header().duration(),
                headerParse.header().streak(),
                Optional.empty()));
    }

    private ParseResult.Invalid invalid(ParseErrorCode errorCode, String description) {
        return new ParseResult.Invalid(errorCode, description);
    }
}
