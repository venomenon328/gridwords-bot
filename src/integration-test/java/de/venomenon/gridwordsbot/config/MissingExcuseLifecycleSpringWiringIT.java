package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.GridwordsBotApplication;
import de.venomenon.gridwordsbot.application.excuse.NoOpExcuseLifecycle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("database")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = GridwordsBotApplication.class,
        properties = {
                "gridwords.discord.enabled=false",
                "gridwords.discord.guild-id=78003",
                "gridwords.discord.channel-id=78004",
                "gridwords.discord.admin-user-ids=78001",
                "spring.main.web-application-type=none"
        })
class MissingExcuseLifecycleSpringWiringIT extends ExcuseLifecycleSpringWiringSupport {

    @Test
    void missingFeaturePropertyUsesTheNoOpLifecycleAndPersistsTheNegativeDecision() {
        assertThat(lifecycle()).isSameAs(NoOpExcuseLifecycle.INSTANCE);
        assertThat(storeQualifyingResult()).isEqualTo(ExcuseStatus.NOT_OFFERED);
    }
}
