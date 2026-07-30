package de.venomenon.gridwordsbot.parser.quadwords;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Pure, conservative geometry and colour parser for a QuadWords 2x2 result image. */
public class QuadWordsImageParser {
    public static final String VERSION = "quadwords-image-v2";
    public static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
    public static final int MAX_IMAGE_WIDTH = 4_096;
    public static final int MAX_IMAGE_HEIGHT = 4_096;
    public static final long MAX_IMAGE_PIXELS = 12_000_000L;

    private static final int BOARD_COUNT = 4;
    private static final int COLUMNS_PER_BOARD = 5;
    private static final int SAMPLE_GRID_SIZE = 7;
    private static final double MIN_DOMINANT_SAMPLE_RATIO = 0.70;

    public Parse parse(byte[] bytes, ShareOutcome outcome) {
        if (bytes == null || bytes.length == 0) return new Parse.Invalid(ParseErrorCode.UNSUPPORTED_IMAGE_FORMAT);
        if (bytes.length > MAX_INPUT_BYTES) return new Parse.Invalid(ParseErrorCode.IMAGE_TOO_LARGE);
        try {
            BufferedImage image = decode(bytes);
            int rows = outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() : 9;
            GridGeometry geometry = detectGeometry(image, rows);
            List<QuadWordsBoard> boards = new ArrayList<>(BOARD_COUNT);
            for (int boardRow = 0; boardRow < 2; boardRow++) {
                for (int boardColumn = 0; boardColumn < 2; boardColumn++) {
                    boards.add(readBoard(image, geometry, boardColumn, boardRow, rows));
                }
            }
            return new Parse.Parsed(new QuadWordsBoards(boards.get(0), boards.get(1), boards.get(2), boards.get(3)));
        } catch (ImageParseException exception) {
            return new Parse.Invalid(exception.errorCode());
        } catch (IllegalArgumentException exception) {
            return new Parse.Invalid(ParseErrorCode.INVALID_IMAGE_STRUCTURE);
        }
    }

    private BufferedImage decode(byte[] bytes) throws ImageParseException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ImageParseException(ParseErrorCode.UNSUPPORTED_IMAGE_FORMAT);
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) {
                    throw new ImageParseException(ParseErrorCode.UNSUPPORTED_IMAGE_FORMAT);
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage image = reader.read(0);
                if (image == null) throw new ImageParseException(ParseErrorCode.INVALID_IMAGE_STRUCTURE);
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ImageParseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ImageParseException(ParseErrorCode.INVALID_IMAGE_STRUCTURE);
        }
    }

    private void validateDimensions(int width, int height) throws ImageParseException {
        if (width <= 0 || height <= 0) throw new ImageParseException(ParseErrorCode.INVALID_IMAGE_STRUCTURE);
        if (width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT
                || (long) width * height > MAX_IMAGE_PIXELS) {
            throw new ImageParseException(ParseErrorCode.IMAGE_TOO_LARGE);
        }
    }

    private GridGeometry detectGeometry(BufferedImage image, int rows) throws ImageParseException {
        List<Span> columns = candidateSpans(image, true, Math.max(8, image.getHeight() / 12));
        if (columns.size() != COLUMNS_PER_BOARD * 2) {
            throw new ImageParseException(ParseErrorCode.INVALID_IMAGE_GEOMETRY);
        }
        validateRegularSpans(columns, ParseErrorCode.INVALID_IMAGE_GEOMETRY);

        List<Span> detectedRows = candidateSpans(image, false, Math.max(8, image.getWidth() / 3));
        if (detectedRows.size() < 2) {
            throw new ImageParseException(ParseErrorCode.INVALID_IMAGE_ROW_COUNT);
        }
        int split = indexAfterLargestGap(detectedRows);
        List<Span> topRows = detectedRows.subList(0, split);
        List<Span> bottomRows = detectedRows.subList(split, detectedRows.size());
        if (topRows.size() != rows || bottomRows.size() != rows) {
            throw new ImageParseException(ParseErrorCode.INVALID_IMAGE_ROW_COUNT);
        }
        validateRegularSpans(topRows, ParseErrorCode.INVALID_IMAGE_GEOMETRY);
        validateRegularSpans(bottomRows, ParseErrorCode.INVALID_IMAGE_GEOMETRY);
        return new GridGeometry(columns, List.of(List.copyOf(topRows), List.copyOf(bottomRows)));
    }

    private int indexAfterLargestGap(List<Span> spans) throws ImageParseException {
        int largestGap = -1;
        int split = -1;
        for (int index = 1; index < spans.size(); index++) {
            int gap = spans.get(index).start() - spans.get(index - 1).end() - 1;
            if (gap > largestGap) {
                largestGap = gap;
                split = index;
            }
        }
        int secondLargestGap = -1;
        for (int index = 1; index < spans.size(); index++) {
            if (index == split) continue;
            secondLargestGap = Math.max(secondLargestGap, spans.get(index).start() - spans.get(index - 1).end() - 1);
        }
        if (largestGap < 8 || largestGap < Math.max(1, secondLargestGap) * 3) {
            throw new ImageParseException(ParseErrorCode.INVALID_IMAGE_GEOMETRY);
        }
        return split;
    }

    private List<Span> candidateSpans(BufferedImage image, boolean vertical, int minimumCandidatePixels) {
        int outer = vertical ? image.getWidth() : image.getHeight();
        int inner = vertical ? image.getHeight() : image.getWidth();
        List<Span> spans = new ArrayList<>();
        int start = -1;
        for (int outerIndex = 0; outerIndex < outer; outerIndex++) {
            int candidates = 0;
            for (int innerIndex = 0; innerIndex < inner; innerIndex++) {
                int x = vertical ? outerIndex : innerIndex;
                int y = vertical ? innerIndex : outerIndex;
                if (classifyPixel(image.getRGB(x, y)) != CellColour.UNKNOWN) candidates++;
            }
            if (candidates >= minimumCandidatePixels) {
                if (start < 0) start = outerIndex;
            } else if (start >= 0) {
                spans.add(new Span(start, outerIndex - 1));
                start = -1;
            }
        }
        if (start >= 0) spans.add(new Span(start, outer - 1));
        return spans;
    }

    private void validateRegularSpans(List<Span> spans, ParseErrorCode errorCode) throws ImageParseException {
        int smallest = spans.stream().mapToInt(Span::size).min().orElseThrow();
        int largest = spans.stream().mapToInt(Span::size).max().orElseThrow();
        if (smallest < 6 || largest > smallest * 3 / 2) throw new ImageParseException(errorCode);
        for (int index = 1; index < spans.size(); index++) {
            int gap = spans.get(index).start() - spans.get(index - 1).end() - 1;
            if (gap < 1 || gap > largest * 3) throw new ImageParseException(errorCode);
        }
    }

    private QuadWordsBoard readBoard(BufferedImage image, GridGeometry geometry, int boardColumn, int boardRow, int rows)
            throws ImageParseException {
        List<String> normalized = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < COLUMNS_PER_BOARD; column++) {
                Span x = geometry.columns().get(boardColumn * COLUMNS_PER_BOARD + column);
                List<Span> boardRows = geometry.rowsByBoardRow().get(boardRow);
                line.append(classify(image, x, boardRows.get(row)).symbol());
            }
            normalized.add(line.toString());
        }
        return new QuadWordsBoard(normalized);
    }

    private CellColour classify(BufferedImage image, Span xSpan, Span ySpan) throws ImageParseException {
        int[] counts = new int[CellColour.values().length];
        int sampleCount = 0;
        for (int yIndex = 1; yIndex <= SAMPLE_GRID_SIZE; yIndex++) {
            int y = ySpan.start() + yIndex * (ySpan.size() - 1) / (SAMPLE_GRID_SIZE + 1);
            for (int xIndex = 1; xIndex <= SAMPLE_GRID_SIZE; xIndex++) {
                int x = xSpan.start() + xIndex * (xSpan.size() - 1) / (SAMPLE_GRID_SIZE + 1);
                counts[classifyPixel(image.getRGB(x, y)).ordinal()]++;
                sampleCount++;
            }
        }
        CellColour dominant = CellColour.UNKNOWN;
        for (CellColour colour : CellColour.values()) {
            if (colour != CellColour.UNKNOWN && counts[colour.ordinal()] > counts[dominant.ordinal()]) dominant = colour;
        }
        if (dominant == CellColour.UNKNOWN) {
            if (counts[CellColour.UNKNOWN.ordinal()] == sampleCount && samplesAreNearBlack(image, xSpan, ySpan)) {
                return CellColour.BLANK;
            }
            throw new ImageParseException(ParseErrorCode.UNCERTAIN_IMAGE_COLOUR);
        }
        if (counts[dominant.ordinal()] < Math.ceil(sampleCount * MIN_DOMINANT_SAMPLE_RATIO)) {
            throw new ImageParseException(ParseErrorCode.UNCERTAIN_IMAGE_COLOUR);
        }
        return dominant;
    }

    private boolean samplesAreNearBlack(BufferedImage image, Span xSpan, Span ySpan) {
        for (int yIndex = 1; yIndex <= SAMPLE_GRID_SIZE; yIndex++) {
            int y = ySpan.start() + yIndex * (ySpan.size() - 1) / (SAMPLE_GRID_SIZE + 1);
            for (int xIndex = 1; xIndex <= SAMPLE_GRID_SIZE; xIndex++) {
                int x = xSpan.start() + xIndex * (xSpan.size() - 1) / (SAMPLE_GRID_SIZE + 1);
                int rgb = image.getRGB(x, y);
                if (((rgb >>> 16) & 0xff) > 24 || ((rgb >>> 8) & 0xff) > 24 || (rgb & 0xff) > 24) {
                    return false;
                }
            }
        }
        return true;
    }

    private CellColour classifyPixel(int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        if (minimum >= 35 && maximum - minimum <= 24) return CellColour.BLANK;
        if (red >= 175 && green >= 105 && green <= 210 && blue <= 95 && red - green >= 25) return CellColour.YELLOW;
        if (green >= 105 && red <= 130 && blue <= 130 && green - red >= 45 && green - blue >= 20) return CellColour.GREEN;
        return CellColour.UNKNOWN;
    }

    private record GridGeometry(List<Span> columns, List<List<Span>> rowsByBoardRow) { }

    private record Span(int start, int end) {
        int size() {
            return end - start + 1;
        }
    }

    private enum CellColour {
        BLANK("⬜"),
        YELLOW("🟨"),
        GREEN("🟩"),
        UNKNOWN("");

        private final String symbol;

        CellColour(String symbol) {
            this.symbol = symbol;
        }

        String symbol() {
            return symbol;
        }
    }

    private static final class ImageParseException extends Exception {
        private final ParseErrorCode errorCode;

        ImageParseException(ParseErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        ParseErrorCode errorCode() {
            return errorCode;
        }
    }

    public sealed interface Parse permits Parse.Parsed, Parse.Invalid {
        record Parsed(QuadWordsBoards boards) implements Parse { }
        record Invalid(ParseErrorCode errorCode) implements Parse { }
    }
}
