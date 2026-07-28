package de.venomenon.gridwordsbot.parser.quadwords;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.FixtureSupport;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuadWordsAttachmentMetadataFallbackTest {

    private static final String SOLVED_SHARE = "quadwords/solved/synthetic-solved-with-streak.txt";
    private final QuadWordsShareParser parser = new QuadWordsShareParser();

    @ParameterizedTest
    @MethodSource("plausibleAttachments")
    void acceptsImageContentTypesAndFilenameFallbacksWhenContentTypeIsMissing(
            AttachmentMetadata attachment) {
        assertThat(parser.parse(input(attachment))).isInstanceOf(ParseResult.Parsed.class);
    }

    static Stream<Arguments> plausibleAttachments() {
        return Stream.of(
                        new AttachmentMetadata("arbitrary.bin", "image/png", 1),
                        new AttachmentMetadata("result.png", "", 1),
                        new AttachmentMetadata("result.jpg", "", 1),
                        new AttachmentMetadata("result.jpeg", "", 1),
                        new AttachmentMetadata("result.webp", "", 1))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("nonImageContentTypes")
    void rejectsImageLookingFilenamesWhenANonImageContentTypeIsPresent(AttachmentMetadata attachment) {
        ParseResult result = parser.parse(input(attachment));

        assertThat(result).isEqualTo(new ParseResult.Invalid(
                ParseErrorCode.MISSING_IMAGE_ATTACHMENT,
                "A plausible QuadWords image attachment is missing."));
    }

    static Stream<Arguments> nonImageContentTypes() {
        return Stream.of(
                        new AttachmentMetadata("result.png", "text/plain", 1),
                        new AttachmentMetadata("result.png", "application/octet-stream", 1))
                .map(Arguments::of);
    }

    private ShareParseInput input(AttachmentMetadata attachment) {
        return new ShareParseInput(FixtureSupport.read(SOLVED_SHARE), List.of(attachment));
    }
}
