package de.venomenon.gridwordsbot;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class OfflineApplicationStartTest {

    @Test
    void startsOfflineWithoutDiscordOrDatabase() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GridwordsBotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("offline")
                .run("--gridwords.discord.enabled=false")) {
            assertThat(context.getBeansOfType(JDA.class)).isEmpty();
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        }
    }
}
