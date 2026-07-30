package de.venomenon.gridwordsbot.application.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ReminderMentionPlanTest {
    @Test
    void rendersOnlyExplicitUserMentions() {
        ReminderMentionPlan plan = new ReminderMentionPlan(Set.of(12L, 34L));

        assertThat(plan.allowedUserIds()).containsExactlyInAnyOrder(12L, 34L);
        assertThat(plan.mentions()).containsExactlyInAnyOrder("<@12>", "<@34>");
        assertThat(plan.mentions()).noneMatch(mention -> mention.contains("@everyone") || mention.contains("@here") || mention.startsWith("<@&"));
    }
}
