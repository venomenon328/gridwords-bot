package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import org.junit.jupiter.api.Test;

class RecordPersistenceConfigurationTest {
    @Test
    void configuresRecordsV2AsTheActiveCatalog() {
        assertThat(new RecordPersistenceConfiguration().recordDefinitionCatalog().version())
                .isEqualTo(RecordDefinitionVersion.RECORDS_V2);
    }
}
