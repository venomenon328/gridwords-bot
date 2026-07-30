package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.GridwordsBotApplication;
import de.venomenon.gridwordsbot.adapter.persistence.DynamicPlayerPostgresPersistenceAdapter;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        classes = GridwordsBotApplication.class,
        properties = {
                "spring.config.import=optional:classpath:/missing-test.properties",
                "gridwords.discord.enabled=false",
                "gridwords.discord.guild-id=11",
                "gridwords.discord.channel-id=12",
                "gridwords.discord.admin-user-ids=101"
        })
@ActiveProfiles("database")
@Testcontainers
class DatabaseInboundStartupIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerStore playerStore;

    @Test
    void startsAfterLiquibaseWithoutConfiguredPlayersAndUsesTheDynamicAdapter() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'player'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'player_participation_period'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isZero();
        assertThat(playerStore).isInstanceOf(DynamicPlayerPostgresPersistenceAdapter.class);
    }
}
