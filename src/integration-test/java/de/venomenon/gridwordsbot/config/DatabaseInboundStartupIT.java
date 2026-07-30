package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.GridwordsBotApplication;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresPersistenceAdapter;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        classes = {GridwordsBotApplication.class, DatabaseInboundStartupIT.CountingPlayerStoreConfiguration.class},
        properties = {
                "spring.config.import=optional:classpath:/missing-test.properties",
                "gridwords.discord.enabled=false",
                "gridwords.players.first.user-id=701",
                "gridwords.players.first.display-name=Startup Tobias",
                "gridwords.players.second.user-id=702",
                "gridwords.players.second.display-name=Startup Georgia",
                "gridwords.discord.admin-user-ids=701"
        })
@ActiveProfiles({"database", "database-startup-test"})
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
    private CountingPlayerStore countingPlayerStore;

    @Test
    void runsLiquibaseThenSynchronizesConfiguredPlayersExactlyOnce() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'player'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isEqualTo(2);
        assertThat(countingPlayerStore.upsertCount()).isEqualTo(2);
        assertThat(countingPlayerStore.findByDiscordUserId(701L).orElseThrow().administrator()).isTrue();
        assertThat(countingPlayerStore.findByDiscordUserId(702L).orElseThrow().administrator()).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CountingPlayerStoreConfiguration {

        @Bean
        @Primary
        CountingPlayerStore countingPlayerStore(PostgresPersistenceAdapter delegate) {
            return new CountingPlayerStore(delegate);
        }
    }

    static final class CountingPlayerStore implements PlayerStore {
        private final PlayerStore delegate;
        private final AtomicInteger upserts = new AtomicInteger();

        CountingPlayerStore(PlayerStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public StoredPlayer upsert(PlayerUpsert request) {
            upserts.incrementAndGet();
            return delegate.upsert(request);
        }

        @Override
        public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) {
            return delegate.findByDiscordUserId(discordUserId);
        }

        int upsertCount() {
            return upserts.get();
        }
    }
}
