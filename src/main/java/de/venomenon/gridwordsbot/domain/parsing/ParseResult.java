package de.venomenon.gridwordsbot.domain.parsing;

import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import java.util.Objects;

/** The three deliberately non-exceptional outcomes of attempting to parse one game share. */
public sealed interface ParseResult permits ParseResult.NotApplicable, ParseResult.Parsed, ParseResult.Invalid {

    record NotApplicable() implements ParseResult {
    }

    record Parsed(ParsedGameResult result) implements ParseResult {

        public Parsed {
            Objects.requireNonNull(result, "result must not be null");
        }
    }

    record Invalid(ParseErrorCode errorCode, String description) implements ParseResult {

        public Invalid {
            Objects.requireNonNull(errorCode, "errorCode must not be null");
            Objects.requireNonNull(description, "description must not be null");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
        }
    }
}
