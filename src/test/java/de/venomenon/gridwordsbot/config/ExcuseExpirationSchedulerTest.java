package de.venomenon.gridwordsbot.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase;
import org.junit.jupiter.api.Test;

class ExcuseExpirationSchedulerTest {

    @Test
    void delegatesEveryScheduledTickToTheBoundedPersistentReconciliation() {
        ExcuseExpirationUseCase expirations = mock(ExcuseExpirationUseCase.class);

        new ExcuseExpirationScheduler(expirations).reconcile();

        verify(expirations).reconcile();
    }
}
