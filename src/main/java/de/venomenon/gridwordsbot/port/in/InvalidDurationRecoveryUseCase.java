package de.venomenon.gridwordsbot.port.in;

import java.util.List;

/** Silent maintenance boundary for GridWords shares rejected by the former duration grammar. */
public interface InvalidDurationRecoveryUseCase {

    List<Long> findCandidates(long guildId, long channelId);

    /** Returns whether the persisted recovery marker was durably completed. */
    boolean recover(InboundSharedMessage message);
}
