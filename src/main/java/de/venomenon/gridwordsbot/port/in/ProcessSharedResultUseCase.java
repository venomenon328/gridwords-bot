package de.venomenon.gridwordsbot.port.in;

/** Application boundary for one already filtered, transport-neutral shared result message. */
public interface ProcessSharedResultUseCase {
    ProcessingResult process(InboundSharedMessage message);
}
