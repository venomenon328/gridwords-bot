package de.venomenon.gridwordsbot.config;

import java.time.ZoneId;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails production startup before processing Discord events when required runtime settings are unsafe. */
@Component
@Profile("production")
final class ProductionConfigurationValidator implements ApplicationRunner {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Berlin");

    private final GridwordsBotProperties properties;
    private final Environment environment;

    ProductionConfigurationValidator(GridwordsBotProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        validate(properties, environment);
    }

    static void validate(GridwordsBotProperties properties, Environment environment) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(environment, "environment");

        requirePositive(properties.discord().guildId(), "DISCORD_GUILD_ID");
        requirePositive(properties.discord().channelId(), "DISCORD_CHANNEL_ID");
        if (!BUSINESS_ZONE.equals(properties.schedule().timeZone())) {
            throw new IllegalStateException("TIME_ZONE must be Europe/Berlin in the production profile");
        }
        requireNonBlank(environment.getProperty("spring.datasource.url"), "DATABASE_URL");
        requireNonBlank(environment.getProperty("spring.datasource.username"), "DATABASE_USERNAME");
        requireNonBlank(environment.getProperty("spring.datasource.password"), "DATABASE_PASSWORD");
        if (properties.discord().enabled()) {
            requireNonBlank(properties.discord().token(), "DISCORD_BOT_TOKEN");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalStateException(name + " must be a positive Discord ID in the production profile");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set in the production profile");
        }
    }
}
