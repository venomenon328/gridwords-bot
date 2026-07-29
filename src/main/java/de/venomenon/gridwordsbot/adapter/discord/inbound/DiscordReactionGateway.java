package de.venomenon.gridwordsbot.adapter.discord.inbound;

import net.dv8tion.jda.api.entities.Message;

/** Narrow JDA-only boundary for applying a result reaction after persistence. */
interface DiscordReactionGateway {
    void addReaction(Message message, String emoji);
}
