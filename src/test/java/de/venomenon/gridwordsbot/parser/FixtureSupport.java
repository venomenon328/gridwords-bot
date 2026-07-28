package de.venomenon.gridwordsbot.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FixtureSupport {

    private FixtureSupport() {
    }

    public static String read(String relativePath) {
        try {
            return Files.readString(Path.of("fixtures", relativePath), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read parser fixture " + relativePath, exception);
        }
    }
}
