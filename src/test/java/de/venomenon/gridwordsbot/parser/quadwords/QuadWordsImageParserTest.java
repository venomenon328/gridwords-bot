package de.venomenon.gridwordsbot.parser.quadwords;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class QuadWordsImageParserTest {
    private static final String BLANK = "⬜";
    private static final String YELLOW = "🟨";
    private static final String GREEN = "🟩";

    private final QuadWordsImageParser parser = new QuadWordsImageParser();

    @ParameterizedTest
    @MethodSource("existingPngFixtures")
    void parsesEveryExistingPngFixture(Path path, ShareOutcome outcome, int expectedRows) throws IOException {
        QuadWordsImageParser.Parse result = parser.parse(Files.readAllBytes(path), outcome);

        assertThat(result).isInstanceOf(QuadWordsImageParser.Parse.Parsed.class);
        List<QuadWordsBoard> boards = ((QuadWordsImageParser.Parse.Parsed) result).boards().ordered();
        assertThat(boards).hasSize(4);
        assertThat(boards).allSatisfy(board -> {
            assertThat(board.rows()).hasSize(expectedRows);
            assertThat(board.rows()).allSatisfy(row -> assertThat(row.codePointCount(0, row.length())).isEqualTo(5));
        });
    }

    @Test
    void readsBoardsInTopLeftTopRightBottomLeftBottomRightOrder() throws IOException {
        String topLeft = BLANK + YELLOW + GREEN + BLANK + YELLOW;
        String topRight = YELLOW + GREEN + BLANK + YELLOW + GREEN;
        String bottomLeft = GREEN + BLANK + YELLOW + GREEN + BLANK;
        String bottomRight = BLANK + GREEN + YELLOW + BLANK + GREEN;

        QuadWordsImageParser.Parse result = parser.parse(
                imageBytes(7, 24, 35, List.of(topLeft, topRight, bottomLeft, bottomRight), "png"),
                solved(7));

        assertThat(result).isInstanceOf(QuadWordsImageParser.Parse.Parsed.class);
        List<QuadWordsBoard> boards = ((QuadWordsImageParser.Parse.Parsed) result).boards().ordered();
        assertThat(boards).extracting(QuadWordsBoard::rows)
                .containsExactly(
                        List.of(topLeft, topLeft, topLeft, topLeft, topLeft, topLeft, topLeft),
                        List.of(topRight, topRight, topRight, topRight, topRight, topRight, topRight),
                        List.of(bottomLeft, bottomLeft, bottomLeft, bottomLeft, bottomLeft, bottomLeft, bottomLeft),
                        List.of(bottomRight, bottomRight, bottomRight, bottomRight, bottomRight, bottomRight, bottomRight));
    }

    @Test
    void acceptsScaledAndMarginedPngFixtures() throws IOException {
        for (String fixture : List.of("quadwords-scaled.png", "quadwords-margined.png")) {
            QuadWordsImageParser.Parse result = parser.parse(syntheticFixture(fixture), solved(7));

            assertThat(result).isInstanceOf(QuadWordsImageParser.Parse.Parsed.class);
        }
    }

    @Test
    void acceptsJpegWithinDocumentedFormats() throws IOException {
        QuadWordsImageParser.Parse result = parser.parse(
                imageBytes(7, 30, 44, defaultBoards(), "jpeg"), solved(7));

        assertThat(result).isInstanceOf(QuadWordsImageParser.Parse.Parsed.class);
    }

    @Test
    void rejectsUnsupportedAndCorruptedImageFixtures() throws IOException {
        assertInvalid(parser.parse(syntheticFixture("quadwords-corrupted.bin"), solved(7)),
                ParseErrorCode.UNSUPPORTED_IMAGE_FORMAT);
        assertInvalid(parser.parse(imageBytes(7, 24, 30, defaultBoards(), "gif"), solved(7)),
                ParseErrorCode.UNSUPPORTED_IMAGE_FORMAT);
    }

    @Test
    void rejectsTruncatedImageFixture() throws IOException {
        assertInvalid(parser.parse(syntheticFixture("quadwords-truncated.png"), solved(7)),
                ParseErrorCode.INVALID_IMAGE_STRUCTURE);
    }

    @Test
    void rejectsUncertainColourFixture() throws IOException {
        assertInvalid(parser.parse(syntheticFixture("quadwords-colour-uncertain.png"), solved(7)),
                ParseErrorCode.UNCERTAIN_IMAGE_COLOUR);
    }

    @Test
    void rejectsWrongNumberOfColumns() throws IOException {
        BufferedImage image = image(7, 24, 30, defaultBoards());
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            int cellSize = 24;
            int margin = 30;
            int boardWidth = 5 * cellSize + 4 * 3;
            int rightmostColumn = margin + boardWidth - cellSize;
            graphics.fillRect(rightmostColumn, margin, cellSize, 7 * cellSize + 6 * 3);
            graphics.fillRect(rightmostColumn + boardWidth + 31, margin, cellSize, 7 * cellSize + 6 * 3);
            graphics.fillRect(rightmostColumn, margin + 7 * cellSize + 6 * 3 + 29, cellSize, 7 * cellSize + 6 * 3);
            graphics.fillRect(rightmostColumn + boardWidth + 31, margin + 7 * cellSize + 6 * 3 + 29,
                    cellSize, 7 * cellSize + 6 * 3);
        } finally {
            graphics.dispose();
        }

        assertInvalid(parser.parse(encode(image, "png"), solved(7)), ParseErrorCode.INVALID_IMAGE_GEOMETRY);
    }

    @Test
    void rejectsWrongActiveRowCount() throws IOException {
        assertInvalid(parser.parse(imageBytes(8, 24, 30, defaultBoards(), "png"), solved(7)),
                ParseErrorCode.INVALID_IMAGE_ROW_COUNT);
    }

    @Test
    void representsClearlyAbsentTrailingRowsOfIndividualBoardsAsCanonicalBlanks() throws IOException {
        List<String> boards = defaultBoards();
        QuadWordsImageParser.Parse result = parser.parse(
                imageBytes(List.of(6, 6, 6, 7), 24, 30, boards, "png"), solved(7));

        assertThat(result).isInstanceOf(QuadWordsImageParser.Parse.Parsed.class);
        List<QuadWordsBoard> parsed = ((QuadWordsImageParser.Parse.Parsed) result).boards().ordered();
        assertThat(parsed.subList(0, 3)).allSatisfy(board -> {
            assertThat(board.rows()).hasSize(7);
            assertThat(board.rows().getLast()).isEqualTo(BLANK.repeat(5));
        });
        assertThat(parsed.get(3).rows().getLast()).isEqualTo(boards.get(3));
    }

    @Test
    void rejectsWhenNoBoardReachesTheReportedAttempt() throws IOException {
        assertInvalid(parser.parse(imageBytes(6, 24, 30, defaultBoards(), "png"), solved(7)),
                ParseErrorCode.INVALID_IMAGE_ROW_COUNT);
    }

    @Test
    void usesNineRowsForUnsolvedX9() throws IOException {
        QuadWordsImageParser.Parse result = parser.parse(imageBytes(9, 24, 30, defaultBoards(), "png"),
                new ShareOutcome.Unsolved(9));

        assertThat(result).isInstanceOf(QuadWordsImageParser.Parse.Parsed.class);
        assertThat(((QuadWordsImageParser.Parse.Parsed) result).boards().ordered())
                .allSatisfy(board -> assertThat(board.rows()).hasSize(9));
    }

    @Test
    void rejectsByteAndDimensionResourceLimits() throws IOException {
        assertInvalid(parser.parse(new byte[QuadWordsImageParser.MAX_INPUT_BYTES + 1], solved(7)),
                ParseErrorCode.IMAGE_TOO_LARGE);

        assertInvalid(parser.parse(syntheticFixture("quadwords-resource-too-wide.png"), solved(7)),
                ParseErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    void exposesAnExplicitParserVersion() {
        assertThat(QuadWordsImageParser.VERSION).isEqualTo("quadwords-image-v2");
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> existingPngFixtures() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("fixtures/quadwords/solved/quadwords-solved-7-9-grey.png"), solved(7), 7),
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("fixtures/quadwords/solved/quadwords-solved-7-9-white.png"), solved(7), 7),
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("fixtures/quadwords/solved/quadwords-solved-9-9-grey.png"), solved(9), 9),
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("fixtures/quadwords/solved/quadwords-solved-9-9-white.png"), solved(9), 9),
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("fixtures/quadwords/unsolved/quadwords-unsolved-x-9-white.png"),
                        new ShareOutcome.Unsolved(9), 9));
    }

    private static byte[] syntheticFixture(String name) throws IOException {
        return Files.readAllBytes(Path.of("fixtures/quadwords/synthetic", name));
    }

    private static ShareOutcome.Solved solved(int attemptsUsed) {
        return new ShareOutcome.Solved(attemptsUsed, 9);
    }

    private static List<String> defaultBoards() {
        return List.of(
                BLANK + YELLOW + GREEN + BLANK + YELLOW,
                YELLOW + GREEN + BLANK + YELLOW + GREEN,
                GREEN + BLANK + YELLOW + GREEN + BLANK,
                BLANK + GREEN + YELLOW + BLANK + GREEN);
    }

    private static byte[] imageBytes(int rows, int cellSize, int margin, List<String> boards, String format)
            throws IOException {
        return imageBytes(Collections.nCopies(4, rows), cellSize, margin, boards, format);
    }

    private static byte[] imageBytes(
            List<Integer> boardRowCounts, int cellSize, int margin, List<String> boards, String format)
            throws IOException {
        return encode(image(boardRowCounts, cellSize, margin, boards), format);
    }

    private static BufferedImage image(int rows, int cellSize, int margin, List<String> boards) {
        return image(Collections.nCopies(4, rows), cellSize, margin, boards);
    }

    private static BufferedImage image(
            List<Integer> boardRowCounts, int cellSize, int margin, List<String> boards) {
        if (boardRowCounts.size() != 4 || boards.size() != 4
                || boardRowCounts.stream().anyMatch(rows -> rows <= 0 || rows > 9)) {
            throw new IllegalArgumentException("four valid board row counts and patterns are required");
        }
        int rows = boardRowCounts.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int cellGap = 3;
        int boardGap = 31;
        int boardWidth = 5 * cellSize + 4 * cellGap;
        int boardHeight = rows * cellSize + (rows - 1) * cellGap;
        BufferedImage image = new BufferedImage(2 * margin + 2 * boardWidth + boardGap,
                2 * margin + 2 * boardHeight + boardGap, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            for (int boardRow = 0; boardRow < 2; boardRow++) {
                for (int boardColumn = 0; boardColumn < 2; boardColumn++) {
                    int boardIndex = boardRow * 2 + boardColumn;
                    String pattern = boards.get(boardIndex);
                    int originX = margin + boardColumn * (boardWidth + boardGap);
                    int originY = margin + boardRow * (boardHeight + boardGap);
                    for (int row = 0; row < boardRowCounts.get(boardIndex); row++) {
                        for (int column = 0; column < 5; column++) {
                            graphics.setColor(colour(pattern.codePointAt(pattern.offsetByCodePoints(0, column))));
                            graphics.fillRect(originX + column * (cellSize + cellGap),
                                    originY + row * (cellSize + cellGap), cellSize, cellSize);
                        }
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Color colour(int codePoint) {
        return switch (codePoint) {
            case 0x2b1c -> new Color(221, 221, 221);
            case 0x1f7e8 -> new Color(229, 167, 29);
            case 0x1f7e9 -> new Color(67, 162, 67);
            default -> throw new IllegalArgumentException("unknown fixture colour");
        };
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, bytes)).isTrue();
        return bytes.toByteArray();
    }

    private static void assertInvalid(QuadWordsImageParser.Parse result, ParseErrorCode expected) {
        assertThat(result).isEqualTo(new QuadWordsImageParser.Parse.Invalid(expected));
    }
}
