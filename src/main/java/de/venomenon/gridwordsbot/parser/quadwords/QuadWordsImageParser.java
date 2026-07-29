package de.venomenon.gridwordsbot.parser.quadwords;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Pure, conservative geometry and colour parser for a QuadWords 2x2 result image. */
public class QuadWordsImageParser {
    public static final String VERSION = "quadwords-image-v1";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;

    public Parse parse(byte[] bytes, ShareOutcome outcome) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) return new Parse.Invalid(ParseErrorCode.IMAGE_TOO_LARGE);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return new Parse.Invalid(ParseErrorCode.UNSUPPORTED_IMAGE_FORMAT);
            if ((long) image.getWidth() * image.getHeight() > MAX_PIXELS) return new Parse.Invalid(ParseErrorCode.IMAGE_TOO_LARGE);
            int rows = outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() : 9;
            List<QuadWordsBoard> boards = new ArrayList<>(4);
            for (int boardRow = 0; boardRow < 2; boardRow++) for (int boardColumn = 0; boardColumn < 2; boardColumn++) {
                boards.add(readBoard(image, boardColumn, boardRow, rows));
            }
            return new Parse.Parsed(new QuadWordsBoards(boards.get(0), boards.get(1), boards.get(2), boards.get(3)));
        } catch (IOException | IllegalArgumentException exception) {
            return new Parse.Invalid(ParseErrorCode.INVALID_IMAGE_STRUCTURE);
        }
    }

    private QuadWordsBoard readBoard(BufferedImage image, int boardColumn, int boardRow, int rows) {
        int left = boardColumn * image.getWidth() / 2;
        int right = (boardColumn + 1) * image.getWidth() / 2;
        int top = boardRow * image.getHeight() / 2;
        int bottom = (boardRow + 1) * image.getHeight() / 2;
        int width = right - left;
        int height = bottom - top;
        if (width < 50 || height < 50) throw new IllegalArgumentException("board quadrant too small");
        List<String> normalized = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < 5; column++) {
                int x0 = left + width * column / 5;
                int x1 = left + width * (column + 1) / 5;
                int y0 = top + height * row / rows;
                int y1 = top + height * (row + 1) / rows;
                line.append(classify(image, x0, x1, y0, y1));
            }
            normalized.add(line.toString());
        }
        return new QuadWordsBoard(normalized);
    }

    private String classify(BufferedImage image, int x0, int x1, int y0, int y1) {
        long red = 0, green = 0, blue = 0; int samples = 0;
        int insetX = Math.max(2, (x1 - x0) / 4), insetY = Math.max(2, (y1 - y0) / 4);
        for (int y = y0 + insetY; y < y1 - insetY; y += Math.max(1, (y1 - y0) / 5)) for (int x = x0 + insetX; x < x1 - insetX; x += Math.max(1, (x1 - x0) / 5)) {
            int rgb = image.getRGB(x, y); red += (rgb >>> 16) & 255; green += (rgb >>> 8) & 255; blue += rgb & 255; samples++;
        }
        if (samples == 0) throw new IllegalArgumentException("no cell samples");
        int r = (int) (red / samples), g = (int) (green / samples), b = (int) (blue / samples);
        if (r > 155 && g > 155 && b > 155 && Math.max(r, Math.max(g,b)) - Math.min(r, Math.min(g,b)) < 45) return "⬜";
        if (r > 130 && g > 115 && b < 120 && r - b > 55) return "🟨";
        if (g > 95 && g > r * 1.15 && g > b * 1.25) return "🟩";
        throw new IllegalArgumentException("uncertain cell colour");
    }

    public sealed interface Parse permits Parse.Parsed, Parse.Invalid {
        record Parsed(QuadWordsBoards boards) implements Parse { }
        record Invalid(ParseErrorCode errorCode) implements Parse { }
    }
}
