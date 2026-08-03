package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.EnumSet;
import java.util.Set;

final class ExcuseTestFixtures {

    private ExcuseTestFixtures() {
    }

    static ExcuseTemplate template(
            String id,
            ExcuseStyle style,
            ExcuseTopic topic,
            int specificity,
            int weight,
            Set<ExcuseCondition> requires,
            String text) {
        return new ExcuseTemplate(
                id,
                style,
                EnumSet.allOf(GameType.class),
                topic,
                specificity,
                weight,
                requires,
                Set.of(),
                text,
                true);
    }

    static ExcuseTemplate general(String id, ExcuseStyle style, ExcuseTopic topic) {
        return template(id, style, topic, 0, 100, Set.of(), id + " text");
    }
}
