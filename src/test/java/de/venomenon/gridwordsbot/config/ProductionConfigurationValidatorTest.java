package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsDatabaseBackedHealthModeWithoutDiscordConnection() {
        assertThatCode(() -> ProductionConfigurationValidator.validate(properties(false, ""), environment()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEnabledDiscordWithoutToken() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ProductionConfigurationValidator.validate(properties(true, ""), environment()))
                .withMessageContaining("DISCORD_BOT_TOKEN");
    }

    @Test
    void rejectsNonBerlinBusinessZone() {
        GridwordsBotProperties properties = new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "", 1L, 2L, List.of()),
                new GridwordsBotProperties.Schedule(LocalTime.of(18, 0), LocalTime.of(23, 0),
                        LocalTime.of(8, 0), LocalTime.of(8, 15), ZoneId.of("UTC")),
                new GridwordsBotProperties.Storage(48));

        assertThatIllegalStateException()
                .isThrownBy(() -> ProductionConfigurationValidator.validate(properties, environment()))
                .withMessageContaining("TIME_ZONE");
    }

    @Test
    void rejectsMissingDatabasePassword() {
        MockEnvironment environment = environment();
        environment.setProperty("spring.datasource.password", "");

        assertThatIllegalStateException()
                .isThrownBy(() -> ProductionConfigurationValidator.validate(properties(false, ""), environment))
                .withMessageContaining("DATABASE_PASSWORD");
    }

    private static GridwordsBotProperties properties(boolean discordEnabled, String token) {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(discordEnabled, token, 1L, 2L, List.of()),
                new GridwordsBotProperties.Schedule(LocalTime.of(18, 0), LocalTime.of(23, 0),
                        LocalTime.of(8, 0), LocalTime.of(8, 15), ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(48));
    }

    private static MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://postgres:5432/gridwords")
                .withProperty("spring.datasource.username", "gridwords")
                .withProperty("spring.datasource.password", "test-password");
    }
}
